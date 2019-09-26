package com.mozilla.speechlibrary;

import android.content.Context;
import android.media.AudioRecord;
import android.os.Process;
import com.github.axet.audiolibrary.encoders.Sound;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.HashMap;
import java.util.Map;

import java.io.FileOutputStream;
import java.io.File;

import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import android.util.Log;

import org.mozilla.deepspeech.libdeepspeech.DeepSpeechModel;
import org.mozilla.deepspeech.libdeepspeech.DeepSpeechStreamingState;

class LocalDSInference implements Runnable {

    DeepSpeechModel mModel;
    DeepSpeechStreamingState mStreamingState;
    MozillaSpeechService mService;

    Queue<short[]> mBuffers = new ConcurrentLinkedQueue<short[]>();

    boolean stopStream;

    final int BEAM_WIDTH = 250;

    final float LM_WEIGHT = 0.75f;
    final float VALID_WORD_COUNT_WEIGHT = 1.85f;

    static final String _tag = "LocalDSInference";

    static boolean keepClips  = false;
    static boolean useDecoder = true;
    static int clipNumber = 0;
    FileChannel clipDebug;

    String modelRoot;
    String tfliteModel;
    String alphabet;
    String LM;
    String trie;

    protected LocalDSInference(MozillaSpeechService aService, int aSampleRate) {
        Log.e(this._tag, "new LocalDSInference()");

        modelRoot = aService.getModelPath() + "/" + aService.getLanguageDir();

        Log.e(this._tag, "Loading model from " + modelRoot);

        this.tfliteModel = this.modelRoot + "/" + LocalSpeechRecognition.kTfLiteModel;
        this.alphabet    = this.modelRoot + "/" + LocalSpeechRecognition.kAlphabet;
        this.LM          = this.modelRoot + "/" + LocalSpeechRecognition.kLM;
        this.trie        = this.modelRoot + "/" + LocalSpeechRecognition.kTrie;

        this.clipNumber += 1;

        this.keepClips  = (new File(this.modelRoot + "/.keepClips")).exists();
        this.useDecoder = !(new File(this.modelRoot + "/.noUseDecoder")).exists();

        Log.e(this._tag, "keepClips=" + this.keepClips);
        Log.e(this._tag, "useDecoder=" + this.useDecoder);

        this.mService = aService;

        if (this.mModel == null) {
            Log.e(this._tag, "new DeepSpeechModel(\"" + this.tfliteModel + "\")");
            this.mModel = new DeepSpeechModel(this.tfliteModel, this.alphabet, BEAM_WIDTH);
        }

        if (this.useDecoder) {
            this.mModel.enableDecoderWihLM(this.LM, this.trie, LM_WEIGHT, VALID_WORD_COUNT_WEIGHT);
        }

        if (this.keepClips) {
            try {
                this.clipDebug = new FileOutputStream(this.modelRoot + "/clip_" + this.clipNumber + ".wav").getChannel();
            } catch (Exception ex) {
            }
        }

        this.mStreamingState = this.mModel.createStream(aSampleRate);
        this.stopStream      = false;
    }

    public void closeModel() {
        Log.e(this._tag, "closeModel()");

        if (this.mStreamingState != null) {
             String _ = this.mModel.finishStream(this.mStreamingState);
        }

        if (this.mModel != null) {
            Log.e(this._tag, "closeModel()");
            this.mModel.freeModel();
        }

        this.mStreamingState = null;
        this.mModel          = null;
    }

    public void appendAudio(short[] aBuffer) {
        Log.e(this._tag, "appendAudio()");
        if (!this.stopStream) {
            // Log.e(this._tag, "appendAudio()::add");
            this.mBuffers.add(aBuffer);

            if (this.keepClips) {
                // DEBUG
                ByteBuffer myByteBuffer = ByteBuffer.allocate(aBuffer.length * 2);
                myByteBuffer.order(ByteOrder.LITTLE_ENDIAN);

                ShortBuffer myShortBuffer = myByteBuffer.asShortBuffer();
                myShortBuffer.put(aBuffer);

                try {
                    this.clipDebug.write(myByteBuffer);
                } catch (Exception ex) {
                }
            }
        }
    }

    public void endOfStream() {
        Log.e(this._tag, "endOfStream()");
        this.stopStream = true;
        if (this.keepClips) {
            try {
                this.clipDebug.close();
            } catch (Exception ex) {
            }
        }
    }

    public void run() {
        Log.e(this._tag, "run()");

        while ((!this.stopStream) || (this.mBuffers.size() > 0)) {
            short[] aBuffer = this.mBuffers.poll();

            if (aBuffer == null) {
                continue;
            }

            this.mModel.feedAudioContent(this.mStreamingState, aBuffer, aBuffer.length);
        }

        Log.e(this._tag, "finishStream()");
        mService.notifyListeners(MozillaSpeechService.SpeechState.DECODING, null);
        String finalDecoded = this.mModel.finishStream(this.mStreamingState);
        Log.e(this._tag, "finalDecoded(" + finalDecoded.length() + ")=" + finalDecoded);
        this.mStreamingState = null;

        STTResult sttResult = new STTResult(finalDecoded, (float)(1.0));
        mService.notifyListeners(MozillaSpeechService.SpeechState.STT_RESULT, sttResult);
    }
}

class LocalSpeechRecognition implements Runnable {

    Vad mVad;
    boolean done;
    boolean cancelled;

    int mMinimumVoice = 150;
    int mMaximumSilence = 500;
    int mUpperLimit = 10;

    static final int FRAME_SIZE = 80;

    int mSampleRate;
    int mChannels;
    MozillaSpeechService mService;

    static final String _tag = "LocalSpeechRecognition";

    LocalDSInference mInferer;
    Thread mInferenceThread;

    public static String kTfLiteModel = "output_graph.tflite";
    public static String kAlphabet    = "alphabet.txt";
    public static String kLM          = "lm.binary";
    public static String kTrie        = "trie";

    private static Map<String,String> mLanguages = new HashMap<String, String>();
    static {
        mLanguages.put("en-US", "en-us");
        mLanguages.put("fr-FR", "fr-fr");
    }

    private static String kBaseModelURL = "https://github.com/lissyx/DeepSpeech/releases/download/v0.6.0-alpha.7/";

    protected LocalSpeechRecognition(int aSampleRate, int aChannels, Vad aVad,
                                MozillaSpeechService aService) {
        Log.e(this._tag, "new LocalSpeechRecognition()");
        this.mVad = aVad;
        this.mSampleRate = aSampleRate;
        this.mChannels = aChannels;
        this.mService = aService;

        this.mInferer = new LocalDSInference(this.mService, this.mSampleRate);
        this.mInferenceThread = new Thread(this.mInferer);
        this.mInferenceThread.start();
    }

    public void run() {
        try {
            int vad = 0;

            boolean finishedvoice = false;
            boolean touchedvoice = false;
            boolean touchedsilence = false;
            boolean raisenovoice = false;
            long samplesvoice = 0 ;
            long samplessilence = 0 ;
            long dtantes = System.currentTimeMillis();
            long dtantesmili =         System.currentTimeMillis();

            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            AudioRecord recorder = Sound.getAudioRecord(mChannels, mSampleRate);
            recorder.startRecording();
            mService.notifyListeners(MozillaSpeechService.SpeechState.START_LISTEN, null);

            while (!this.done && !this.cancelled) {
                int nshorts = 0 ;

                short[] mBuftemp = new short[FRAME_SIZE * mChannels * 2];
                nshorts = recorder.read(mBuftemp, 0, mBuftemp.length);

                vad = mVad.feed(mBuftemp, nshorts);
                double[] fft =  Sound.fft(mBuftemp, 0, nshorts);
                double fftsum = Arrays.stream(fft).sum()/fft.length;
                mService.notifyListeners(MozillaSpeechService.SpeechState.MIC_ACTIVITY, fftsum);

                long dtdepois = System.currentTimeMillis();

                if (vad == 0) {
                    if (touchedvoice) {
                        samplessilence += dtdepois - dtantesmili;
                        if (samplessilence >  mMaximumSilence) touchedsilence = true;
                    }
                } else { // vad == 1 => Active voice
                    samplesvoice  += dtdepois - dtantesmili;
                    if (samplesvoice >  mMinimumVoice) touchedvoice = true;

                    for (int i = 0; i < mBuftemp.length; ++i) {
                        mBuftemp[i] *= 5.0;
                    }
                }
                dtantesmili = dtdepois;

                this.mInferer.appendAudio(mBuftemp);

                if (touchedvoice && touchedsilence)
                    finishedvoice = true;

                if (finishedvoice) {
                    this.done = true;
                    this.mInferer.endOfStream();
                }

                if ((dtdepois - dtantes)/1000 > mUpperLimit ) {
                    this.done = true;
                    if (touchedvoice) {
                        this.mInferer.endOfStream();
                    }
                    else {
                        raisenovoice = true;
                    }
                }

                if (nshorts <= 0)
                    break;
            }

            mVad.stop();
            recorder.stop();
            recorder.release();

            if (raisenovoice) mService.notifyListeners(MozillaSpeechService.SpeechState.NO_VOICE, null);

            if (cancelled) {
                cancelled = false;
                mService.notifyListeners(MozillaSpeechService.SpeechState.CANCELED, null);
                return;
            }

        } catch (Exception exc) {
            String error = String.format("General audio error %s", exc.getMessage());
            mService.notifyListeners(MozillaSpeechService.SpeechState.ERROR, error);
            exc.printStackTrace();
        }
    }

    public void cancel() {
        Log.e(this._tag, "cancel()");
        this.cancelled = true;
        this.done      = true;

        if (this.mInferer != null) {
            this.mInferer.closeModel();
        }
    }

    public static String getLanguageDir(String aLanguage) {
        String rv = aLanguage;

        if (rv.length() != 3) {
            if (mLanguages.containsKey(rv)) {
                rv = mLanguages.get(rv);
            }
        }

        return rv;
    }
    
    public static boolean ensureModelInstalled(String aModelPath) {
        Log.e(_tag, "ensureModelInstalled(" + aModelPath + ")");
        return (new File(aModelPath + "/" + kTfLiteModel)).exists()
            && (new File(aModelPath + "/" + kAlphabet)).exists()
            && (new File(aModelPath + "/" + kLM)).exists()
            && (new File(aModelPath + "/" + kTrie)).exists();
    }

    public static String getModelDownloadURL(String aLang) {
        return kBaseModelURL + aLang + ".zip";
    }
}
