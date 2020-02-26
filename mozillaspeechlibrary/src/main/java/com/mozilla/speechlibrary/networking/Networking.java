package com.mozilla.speechlibrary.networking;

import android.content.Context;

import com.mozilla.speechlibrary.MozillaSpeechService;

import java.io.ByteArrayOutputStream;

public abstract class Networking {

    String STT_ENDPOINT = "https://speaktome-2.services.mozilla.com/";

    MozillaSpeechService mSpeechService;
    boolean cancelled;
    Context mContext;

    Networking(Context aContext, MozillaSpeechService aSpeechService) {
        mContext = aContext;
        mSpeechService = aSpeechService;
    }

    public abstract void doSTT(final ByteArrayOutputStream baos, NetworkSettings mNetworkSettings);

    public void cancel() {
        cancelled = true;
    }
}
