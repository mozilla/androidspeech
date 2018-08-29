package com.mozilla.speechlibrary;

public class Vad {

    static {
        System.loadLibrary("webrtc_jni");
    }

    protected native int start();
    protected native int feed(short[] x, int n);
    protected native int isSilence();
    protected native int stop();
}
