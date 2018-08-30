package com.mozilla.speechlibrary;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;

public class MozillaSpeechService {

    protected static final String TAG = "MozillaSpeech";
    private final int SAMPLERATE = 16000;
    private final int CHANNELS = 1;
    private ArrayList<ISpeechRecognitionListener> mListeners;
    private Context mContext;
    private boolean isIdle = true;

    public enum SpeechState
    {
        DECODING, MIC_ACTIVITY, STT_RESULT, START_LISTEN,
        NO_VOICE, CANCELED, ERROR
    }

    private static final MozillaSpeechService ourInstance = new MozillaSpeechService();
    private SpeechRecognition mSpeechRecognition;
    private SpeechState mState;
    private Vad mVad;

    public static MozillaSpeechService getInstance() {
        return ourInstance;
    }

    private MozillaSpeechService() {
    }

    public void start(Context aContext) {

        try {
            if (!isIdle) {
                notifyListeners(SpeechState.ERROR, "Recognition already In progress");
            } else {
                mVad = new Vad();
                int retVal = mVad.start();
                this.mContext = aContext;
                if (retVal < 0) {
                    notifyListeners(SpeechState.ERROR, "Error Initializing VAD " + String.valueOf(retVal));
                } else {
                    this.mSpeechRecognition = new SpeechRecognition(SAMPLERATE, CHANNELS, mVad, aContext, this);
                    Thread audio_thread = new Thread(this.mSpeechRecognition);
                    audio_thread.start();
                    isIdle = false;
                }
            }
        } catch (Exception exc) {
            notifyListeners(SpeechState.ERROR, "General error loading the module.");
        }
    }

    public void addListener(ISpeechRecognitionListener aListener) {
        if (mListeners == null) {
            mListeners = new ArrayList<>();
        }
        mListeners.add(aListener);
    }

    protected void notifyListeners(MozillaSpeechService.SpeechState aState, Object aPayload) {
        mState = aState;
        for (ISpeechRecognitionListener listener : mListeners) {
            listener.onSpeechStatusChanged(aState, aPayload);
            if (aState == SpeechState.STT_RESULT || aState == SpeechState.ERROR
                    || aState == SpeechState.NO_VOICE || aState == SpeechState.CANCELED) {
                isIdle = true;
            }
        }
    }

    public void cancel() {
        this.mSpeechRecognition.cancel();
    }

    public void removeListener(ISpeechRecognitionListener aListener) {
        if (mListeners != null) {
            mListeners.remove(aListener);
        }
    }

}
