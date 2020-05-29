package com.mozilla.speechlibrary.stt;

public interface STTClient {
    default void initEncoding(int sampleRate) {};
    default void encode(final short[] buffer, final int pos, final int len) {};
    default void endEncoding() {};
    default void process(){}
    default boolean isRunning() { return false; }
}
