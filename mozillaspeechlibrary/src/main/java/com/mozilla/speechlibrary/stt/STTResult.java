package com.mozilla.speechlibrary.stt;

import java.io.Serializable;

public class STTResult implements Serializable {

    public String mTranscription;
    public float mConfidence;

    STTResult(String aTranscription, float aConfidence) {
        this.mTranscription = aTranscription;
        this.mConfidence = aConfidence;
    }
}