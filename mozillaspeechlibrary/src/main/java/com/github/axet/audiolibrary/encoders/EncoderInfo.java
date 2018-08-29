package com.github.axet.audiolibrary.encoders;

public class EncoderInfo {
    public int channels;
    public int hz;
    public int bps;

    public EncoderInfo(int channels, int sampleRate, int bps) {
        this.channels = channels;
        this.hz = sampleRate;
        this.bps = bps;
    }

}
