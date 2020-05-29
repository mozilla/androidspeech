package com.mozilla.speechlibrary;

public class Vad {

    static {
        System.loadLibrary("webrtc_jni");
    }

    public native int start();
    public native int feed(short[] x, int n);
    protected native int isSilence();
    public native int stop();
}
