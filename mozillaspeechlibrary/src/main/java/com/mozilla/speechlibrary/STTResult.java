package com.mozilla.speechlibrary;

public class STTResult {

    public String mTranscription;
    public float mConfidence;

    public STTResult(String aTranscription, float aConfidence) {
        this.mTranscription = aTranscription;
        this.mConfidence = aConfidence;
    }
}