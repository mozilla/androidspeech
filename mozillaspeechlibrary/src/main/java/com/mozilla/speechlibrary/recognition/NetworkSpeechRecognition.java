package com.mozilla.speechlibrary.recognition;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mozilla.speechlibrary.SpeechResultCallback;
import com.mozilla.speechlibrary.SpeechServiceSettings;
import com.mozilla.speechlibrary.stt.STTGeckoNetworkClient;
import com.mozilla.speechlibrary.stt.STTNetworkClient;

import org.mozilla.geckoview.GeckoWebExecutor;

public class NetworkSpeechRecognition extends SpeechRecognition {

    private GeckoWebExecutor mExecutor;

    public NetworkSpeechRecognition(@NonNull Context context,
                                    @Nullable GeckoWebExecutor executor) {
        super(context);

        mExecutor = executor;
    }

    @Override
    public void start(@NonNull SpeechServiceSettings settings,
                      @NonNull SpeechResultCallback callback) {
        if (mExecutor == null) {
            mStt = new STTNetworkClient(mContext, settings, this);

        } else {
            mStt = new STTGeckoNetworkClient(mContext, settings, this, mExecutor);
        }

        super.start(settings, callback);
    }
}
