package com.github.axet.audiolibrary.encoders;

import android.media.AudioFormat;
import android.util.Log;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import vavi.sound.pcm.resampling.ssrc.SSRC;

public class Resample {
    public static final String TAG = Resample.class.getSimpleName();

    public static final ByteOrder ORDER = ByteOrder.LITTLE_ENDIAN;
    public static final int SHORT_BYTES = Short.SIZE / Byte.SIZE;
    public static final int PIPE_SIZE = 100 * 1024;

    Thread thread;
    PipedOutputStream os;
    PipedInputStream is;
    RuntimeException delayed;

    public Resample(final int sampleRate, final int channels, final int hz) {
        try {
            this.os = new PipedOutputStream();
            this.is = new PipedInputStream(PIPE_SIZE);
            final PipedInputStream is = new PipedInputStream(this.os);
            final PipedOutputStream os = new PipedOutputStream(this.is);
            final int c = Sound.DEFAULT_AUDIOFORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1;
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        SSRC ssrc = new SSRC(is, os, sampleRate, hz, c, c, channels, Integer.MAX_VALUE, 0, 0, true);
                    } catch (RuntimeException e) {
                        Log.d(TAG, "SSRC failed", e);
                        delayed = e;
                    } catch (IOException e) {
                        Log.d(TAG, "SSRC failed", e);
                        delayed = new RuntimeException(e);
                    }
                }
            }, "SSRC");
            thread.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void end() {
        if (delayed != null)
            throw delayed;
        try {
            is.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(short[] buf, int pos, int len) {
        if (delayed != null)
            throw delayed;
        try {
            ByteBuffer bb = ByteBuffer.allocate(len * SHORT_BYTES);
            bb.order(ORDER);
            bb.asShortBuffer().put(buf, pos, len);
            os.write(bb.array());
            os.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ByteBuffer read() {
        if (delayed != null)
            throw delayed;
        try {
            int blen = is.available();
            if (blen <= 0)
                return null;
            byte[] b = new byte[blen];
            int read = is.read(b);
            ByteBuffer bb = ByteBuffer.allocate(read);
            bb.order(ORDER);
            bb.put(b, 0, read);
            return bb;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }
}
