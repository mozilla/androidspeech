package com.mozilla.speechlibrary.stt;

import android.content.Context;

import androidx.annotation.NonNull;

import com.mozilla.speechlibrary.SpeechServiceSettings;

public abstract class STTBaseClient implements STTClient {

    Context mContext;
    SpeechServiceSettings mSettings;
    STTClientCallback mCallback;
    boolean mIsRunning;

    STTBaseClient(@NonNull Context context,
                  @NonNull SpeechServiceSettings settings,
                  @NonNull STTClientCallback callback) {
        mContext = context;
        mSettings = settings;
        mCallback = callback;
    }

    @Override
    public boolean isRunning() {
        return mIsRunning;
    }
}
