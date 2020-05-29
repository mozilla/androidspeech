package com.mozilla.speechlibrary.recognition;

import android.content.Context;

import androidx.annotation.NonNull;

import com.mozilla.speechlibrary.SpeechResultCallback;
import com.mozilla.speechlibrary.SpeechServiceSettings;
import com.mozilla.speechlibrary.stt.STTLocalClient;

public class LocalSpeechRecognition extends SpeechRecognition {

    public LocalSpeechRecognition(@NonNull Context context) {
        super(context);
    }

    @Override
    public void start(@NonNull SpeechServiceSettings settings,
                      @NonNull SpeechResultCallback callback) {
        mStt = new STTLocalClient(mContext, settings, this);
        super.start(settings, callback);
    }
}
