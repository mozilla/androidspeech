package com.github.axet.audiolibrary.encoders;

import android.content.Context;
import android.media.AudioFormat;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;

public class Factory {

    public static int getBitrate(int hz) {
        if (hz < 16000) {
            return 32000;
        } else if (hz < 44100) {
            return 64000;
        } else {
            return 128000;
        }
    }

    public static Encoder getEncoder(Context context, String ext, EncoderInfo info, ByteArrayOutputStream out) {
        if (ext.equals(FormatOPUS.EXT)) {
            if (Build.VERSION.SDK_INT >= 23) { // Android 6.0 (has ogg/opus support) https://en.wikipedia.org/wiki/Opus_(audio_format)
                return new FormatOPUS_OGG(context, info, out); // android6+ supports ogg/opus
            }
        }
        return null;
    }

    public static long getEncoderRate(String ext, int rate) {
        if (ext.equals(FormatOPUS.EXT)) {
            long y1 = 202787; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 319120; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;
            return y / 60;
        }

        // default raw
        int c = Sound.DEFAULT_AUDIOFORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1;
        return c * rate;
    }
}
