package com.mozilla.speechlibrary;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.mozilla.speechlibrary.stt.STTResult;

public interface SpeechResultCallback {

    @IntDef(value = { SPEECH_ERROR, MODEL_NOT_FOUND})
    @interface ErrorType {}
    int SPEECH_ERROR = 0;
    int MODEL_NOT_FOUND = 1;

    void onStartListen();
    void onMicActivity(double fftsum);
    void onDecoding();
    void onSTTResult(@Nullable STTResult result);
    void onNoVoice();
    void onError(@ErrorType int errorType, @Nullable String error);
}
