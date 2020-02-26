package com.mozilla.speechlibrary;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.mozilla.speechlibrary.networking.NetworkSettings;

import org.mozilla.geckoview.GeckoWebExecutor;

import java.util.ArrayList;

public class MozillaSpeechService {

    protected static final String TAG = "MozillaSpeech";
    private final int SAMPLERATE = 16000;
    private final int CHANNELS = 1;
    private ArrayList<ISpeechRecognitionListener> mListeners;
    private boolean isIdle = true;
    private NetworkSettings mNetworkSettings;
    private boolean useDeepSpeech = false;
    private String mModelPath;
    private GeckoWebExecutor mExecutor;

    public enum SpeechState
    {
        DECODING, MIC_ACTIVITY, STT_RESULT, START_LISTEN,
        NO_VOICE, CANCELED, ERROR
    }

    private static final MozillaSpeechService ourInstance = new MozillaSpeechService();
    private NetworkSpeechRecognition mNetworkSpeechRecognition;
    private LocalSpeechRecognition mLocalSpeechRecognition;
    private Vad mVad;

    public static MozillaSpeechService getInstance() {
        return ourInstance;
    }

    private MozillaSpeechService() {
        mVad = new Vad();
        mNetworkSettings = new NetworkSettings();
    }

    public void start(Context aContext) {

        try {
            if (!isIdle) {
                notifyListeners(SpeechState.ERROR, "Recognition already In progress");
            } else {
                int retVal = mVad.start();
                if (retVal < 0) {
                    notifyListeners(SpeechState.ERROR, "Error Initializing VAD " + String.valueOf(retVal));
                } else {
                    Thread audio_thread;

                    if (this.useDeepSpeech) {
                        this.mLocalSpeechRecognition = new LocalSpeechRecognition(SAMPLERATE, CHANNELS, mVad, this);
                        audio_thread = new Thread(this.mLocalSpeechRecognition);
                    } else {
                        this.mNetworkSpeechRecognition = new NetworkSpeechRecognition(SAMPLERATE, CHANNELS, mVad, aContext, this, mNetworkSettings);
                        audio_thread = new Thread(this.mNetworkSpeechRecognition);
                    }

                    audio_thread.start();
                    isIdle = false;
                }
            }
        } catch (Exception exc) {
            Log.e("MozillaSpeechService", "General error loading the module: " + exc);
            notifyListeners(SpeechState.ERROR, "General error loading the module: " + exc);
        }
    }

    public void addListener(ISpeechRecognitionListener aListener) {
        if (mListeners == null) {
            mListeners = new ArrayList<>();
        }
        mListeners.add(aListener);
    }

    public void notifyListeners(MozillaSpeechService.SpeechState aState, Object aPayload) {
        if (aState == SpeechState.STT_RESULT || aState == SpeechState.ERROR
                || aState == SpeechState.NO_VOICE || aState == SpeechState.CANCELED) {
            isIdle = true;
        }
        for (ISpeechRecognitionListener listener : mListeners) {
            listener.onSpeechStatusChanged(aState, aPayload);
        }
    }

    public void cancel() {
        if (this.mNetworkSpeechRecognition != null) {
            this.mNetworkSpeechRecognition.cancel();
        }
        if (this.mLocalSpeechRecognition != null) {
            this.mLocalSpeechRecognition.cancel();
        }
    }

    public void removeListener(ISpeechRecognitionListener aListener) {
        if (mListeners != null) {
            mListeners.remove(aListener);
        }
    }

    public void storeSamples(boolean yesOrNo) {
        this.mNetworkSettings.mStoreSamples = yesOrNo;
    }

    public void storeTranscriptions(boolean yesOrNo) {
        this.mNetworkSettings.mStoreTranscriptions = yesOrNo;
    }

    public void setLanguage(String language) {
        this.mNetworkSettings.mLanguage = language;
    }

    public String getLanguageDir() {
        return LocalSpeechRecognition.getLanguageDir(this.mNetworkSettings.mLanguage);
    }

    public void useDeepSpeech(boolean yesOrNo) {
        this.useDeepSpeech = yesOrNo;
    }

    public String getModelPath() {
        return this.mModelPath;
    }

    // This sets model's root path, not including the language
    public void setModelPath(String aModelPath) {
        this.mModelPath = aModelPath;
    }

    public boolean ensureModelInstalled() {
        return LocalSpeechRecognition.ensureModelInstalled(this.getModelPath() + "/" + this.getLanguageDir());
    }

    public String getModelDownloadURL() {
        return LocalSpeechRecognition.getModelDownloadURL(this.getLanguageDir());
    }

    public void setProductTag(String tag) {
        this.mNetworkSettings.mProductTag = tag;
    }

    public void setGeckoWebExecutor(@NonNull GeckoWebExecutor executor) {
        mExecutor = executor;
    }

    GeckoWebExecutor getGeckoWebExecutor() {
        // We used to be able to get the GeckoRuntime instance through GeckoRuntime.getInstance but
        // it has been made private so now we need to provide the GeckoWebExecutor from outside
        return mExecutor;
    }

}
