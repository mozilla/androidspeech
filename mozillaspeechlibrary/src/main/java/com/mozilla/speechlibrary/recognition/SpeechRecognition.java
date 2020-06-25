package com.mozilla.speechlibrary.recognition;

import android.content.Context;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.axet.audiolibrary.encoders.Sound;
import com.mozilla.speechlibrary.SpeechResultReceiver;
import com.mozilla.speechlibrary.SpeechState;
import com.mozilla.speechlibrary.stt.STTResult;
import com.mozilla.speechlibrary.Vad;
import com.mozilla.speechlibrary.SpeechResultCallback;
import com.mozilla.speechlibrary.SpeechServiceSettings;
import com.mozilla.speechlibrary.stt.STTClient;
import com.mozilla.speechlibrary.stt.STTClientCallback;

import java.util.Arrays;

public abstract class SpeechRecognition implements STTClientCallback {

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1;
    private static final int FRAME_SIZE = 160;
    private static final int MAX_SILENCE = 1500;
    private static final int MIN_VOICE = 250;

    @NonNull
    Context mContext;
    STTClient mStt;
    @NonNull
    private SpeechResultReceiver mReceiver;
    private SpeechResultCallback mDelegate;
    private Vad mVad;
    private boolean mIsRunning;
    private AudioRecord mRecorder;

    SpeechRecognition(@NonNull Context context) {
        mContext = context;
        mReceiver = new SpeechResultReceiver(new Handler(context.getMainLooper()));
    }

    public void start(@NonNull SpeechServiceSettings settings, @NonNull SpeechResultCallback callback) {
        mDelegate = callback;
        mReceiver.addReceiver(mDelegate);
        mIsRunning = true;
        mVad = new Vad();

        boolean done = false;

        try {
            int retVal = mVad.start();
            if (retVal < 0) {
                throw new Exception("Error Initializing VAD: " + retVal);
            }

            if (!mStt.isRunning()) {
                return;
            }

            long samplesVoice = 0;
            long samplesSilence = 0;
            boolean touchedVoice = false;
            boolean touchedSilence = false;
            int vad;
            long dtantes = System.currentTimeMillis();
            long dtantesmili = 	System.currentTimeMillis();
            boolean raisenovoice = false;

            mRecorder = Sound.getAudioRecord(CHANNELS, SAMPLE_RATE);
            mRecorder.startRecording();

            mStt.initEncoding(SAMPLE_RATE);
            mCallback.onStartListen();

            while (mIsRunning && !done) {
                int nshorts = 0;

                short[] mBufTemp = new short[FRAME_SIZE * CHANNELS * 2];
                nshorts = mRecorder.read(mBufTemp, 0, mBufTemp.length);

                vad = mVad.feed(mBufTemp, nshorts);
                double[] fft =  Sound.fft(mBufTemp, 0, nshorts);
                double fftsum = Arrays.stream(fft).sum()/fft.length;

                mCallback.onMicActivity(fftsum);

                long dtdepois = System.currentTimeMillis();

                if (vad == 0) {
                    if (touchedVoice) {
                        samplesSilence += dtdepois - dtantesmili;
                        if (samplesSilence > MAX_SILENCE) touchedSilence = true;
                    }

                } else {
                    samplesVoice  += dtdepois - dtantesmili;
                    if (samplesVoice > MIN_VOICE) touchedVoice = true;

                    for (int i = 0; i < mBufTemp.length; ++i) {
                        mBufTemp[i] *= 5.0;
                    }
                }
                dtantesmili = dtdepois;

                mStt.encode(mBufTemp, 0, nshorts);

                if (touchedVoice && touchedSilence) {
                    done = true;
                }

                int mUpperLimit = 10;
                if ((dtdepois - dtantes)/1000 > mUpperLimit) {
                    done = true;
                    if (!touchedVoice) {
                        raisenovoice = true;
                    }
                }

                if (nshorts <= 0)
                    break;
            }

            mStt.endEncoding();

            if (raisenovoice) {
                mCallback.onNoVoice();

            } else {
                mStt.process();
            }

        } catch (Exception exc) {
            mStt.endEncoding();
            mCallback.onError(SpeechResultCallback.SPEECH_ERROR, exc.getLocalizedMessage());
            exc.printStackTrace();

        } finally {
            releaseResources();
        }
    }

    private void releaseResources() {
        if (mRecorder != null) {
            try {
                mRecorder.stop();

            } catch (IllegalStateException e) {
                e.printStackTrace();

            } finally {
                mRecorder.release();
                mRecorder = null;
            }
        }

        if (mVad != null) {
            mVad.stop();
            mVad = null;
        }
    }

    public void stop() {
        mReceiver.removeReceiver(mDelegate);

        mIsRunning = false;

        releaseResources();
    }

    public boolean isRunning() {
        return mIsRunning;
    }

    // STTClientCallback

    @Override
    public void onSTTStart() {
        mCallback.onDecoding();
    }

    @Override
    public void onSTTFinished(@NonNull STTResult result) {
        mCallback.onSTTResult(result);
    }

    @Override
    public void onSTTError(@NonNull String error) {
        mCallback.onError(SpeechResultCallback.SPEECH_ERROR, error);
    }

    // SpeechResultCallback

    private SpeechResultCallback mCallback = new SpeechResultCallback() {

        @Override
        public void onStartListen() {
            Bundle bundle = new Bundle();
            mReceiver.send(SpeechState.START_LISTEN.ordinal(), bundle);
        }

        @Override
        public void onMicActivity(double fftsum) {
            Bundle bundle = new Bundle();
            bundle.putSerializable(SpeechResultReceiver.PARAM_FFT_SUM, fftsum);
            mReceiver.send(SpeechState.MIC_ACTIVITY.ordinal(), bundle);
        }

        @Override
        public void onDecoding() {
            Bundle bundle = new Bundle();
            mReceiver.send(SpeechState.DECODING.ordinal(), bundle);
        }

        @Override
        public void onSTTResult(@Nullable STTResult result) {
            mIsRunning = false;
            Bundle bundle = new Bundle();
            bundle.putSerializable(SpeechResultReceiver.PARAM_RESULT, result);
            mReceiver.send(SpeechState.STT_RESULT.ordinal(), bundle);
        }

        @Override
        public void onNoVoice() {
            mIsRunning = false;
            Bundle bundle = new Bundle();
            mReceiver.send(SpeechState.NO_VOICE.ordinal(), bundle);
        }

        @Override
        public void onError(@ErrorType int errorType, @Nullable String error) {
            mIsRunning = false;
            Bundle bundle = new Bundle();
            bundle.putSerializable(SpeechResultReceiver.PARAM_RESULT, error);
            mReceiver.send(SpeechState.ERROR.ordinal(), bundle);
        }
    };
}
