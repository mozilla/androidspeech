package com.mozilla.speechlibrary.stt;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.mozilla.speechlibrary.SpeechServiceSettings;
import com.mozilla.speechlibrary.utils.ModelUtils;

import org.json.JSONObject;
import org.mozilla.deepspeech.libdeepspeech.DeepSpeechModel;
import org.mozilla.deepspeech.libdeepspeech.DeepSpeechStreamingState;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class STTLocalClient extends STTBaseClient implements Runnable {

    private static final String TAG = STTLocalClient.class.getSimpleName();

    private boolean mKeepClips = false;
    private DeepSpeechModel mModel;
    private DeepSpeechStreamingState mStreamingState;
    private FileChannel clipDebug;
    private Queue<short[]> mBuffers;
    private boolean mEndOfStream;

    public STTLocalClient(@NonNull Context context,
                   @NonNull SpeechServiceSettings settings,
                   @NonNull STTClientCallback callback) {
        super(context, settings, callback);

        String modelRoot = settings.getModelPath();
        if (!ModelUtils.isReady(modelRoot)) {
            mIsRunning = false;
            mEndOfStream = true;
            mCallback.onSTTError("STT Error: Model not ready");
            return;
        }

        try {
            StringBuilder infoJsonContent = new StringBuilder();
            BufferedReader br = new BufferedReader(new FileReader(ModelUtils.getInfoJsonFolder(modelRoot)));
            String line;
            while ((line = br.readLine()) != null) {
                Log.d(TAG, "line=" + line);
                infoJsonContent.append(line);
            }
            br.close();
            Log.d(TAG, "infoJsonContent=" + infoJsonContent);

        } catch (Exception e) {
            mIsRunning = false;
            mEndOfStream = true;
            mCallback.onSTTError("STT Error");
            return;
        }

        int clipNumber = 0;
        clipNumber += 1;

        mKeepClips = (new File(modelRoot + "/.keepClips")).exists();
        boolean useDecoder = !(new File(modelRoot + "/.noUseDecoder")).exists();

        Log.d(TAG, "keepClips=" + mKeepClips);
        Log.d(TAG, "useDecoder=" + useDecoder);

        if (mModel == null) {
            Log.d(TAG, "new DeepSpeechModel(\"" + ModelUtils.getTFLiteFolder(modelRoot) + "\")");
            mModel = new DeepSpeechModel(ModelUtils.getTFLiteFolder(modelRoot));
        }

        if (useDecoder) {
            mModel.enableExternalScorer(ModelUtils.getScorerFolder(modelRoot));
        }

        if (mKeepClips) {
            try {
                clipDebug = new FileOutputStream(modelRoot + "/clip_" + clipNumber + ".wav").getChannel();

            } catch (Exception ignored) { }
        }

        mStreamingState = mModel.createStream();
        mIsRunning = true;
        mEndOfStream = false;
    }

    @Override
    public void encode(final short[] aBuffer, final int pos, final int len) {
        mBuffers.add(aBuffer);
    }

    @Override
    public void process() {
        mEndOfStream = true;
    }

    private void closeModel() {
        if (mModel != null) {
            mModel.freeModel();
        }

        mStreamingState = null;
        mModel = null;
    }

    private void decode() {
        mCallback.onSTTStart();

        String finalDecoded = mModel.finishStream(mStreamingState);

        STTResult sttResult = new STTResult(finalDecoded, (float)(1.0));
        mCallback.onSTTFinished(sttResult);

        closeModel();

        mIsRunning = false;
    }

    @Override
    public void run() {
        mBuffers = new ConcurrentLinkedQueue<>();

        while (!mEndOfStream || mBuffers.size() > 0) {
            short[] aBuffer = mBuffers.poll();

            if (aBuffer == null) {
                continue;
            }

            this.mModel.feedAudioContent(mStreamingState, aBuffer, aBuffer.length);

            // DEBUG
            if (mKeepClips) {
                ByteBuffer myByteBuffer = ByteBuffer.allocate(aBuffer.length * 2);
                myByteBuffer.order(ByteOrder.LITTLE_ENDIAN);

                ShortBuffer myShortBuffer = myByteBuffer.asShortBuffer();
                myShortBuffer.put(aBuffer);

                try {
                    clipDebug.write(myByteBuffer);

                } catch (Exception ignored) {}
            }
        }

        decode();
    }
}
