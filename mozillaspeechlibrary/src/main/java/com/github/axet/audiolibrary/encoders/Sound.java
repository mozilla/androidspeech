package com.github.axet.audiolibrary.encoders;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

public class Sound {
    public static final int DEFAULT_AUDIOFORMAT = AudioFormat.ENCODING_PCM_16BIT;

    public static AudioRecord getAudioRecord(int aNumChannels, int aSampleRate){
        int minBufSize = AudioRecord.getMinBufferSize(aSampleRate,
                aNumChannels == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);

        // initialize audio recorder
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                aSampleRate,
                aNumChannels == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufSize);

        return recorder;

    }

    public static double[] fft(short[] buffer, int offset, int len) {
        int len2 = (int) Math.pow(2, Math.ceil(Math.log(len) / Math.log(2)));

        final double[][] dataRI = new double[][]{
                new double[len2], new double[len2]
        };

        double[] dataR = dataRI[0];
        double[] dataI = dataRI[1];

        double powerInput = 0;
        for (int i = 0; i < len; i++) {
            dataR[i] = buffer[offset + i] / (float) 0x7fff;
            powerInput += dataR[i] * dataR[i];
        }
        powerInput = Math.sqrt(powerInput / len);

        FastFourierTransformer.transformInPlace(dataRI, DftNormalization.STANDARD, TransformType.FORWARD);

        double[] data = new double[len2 / 2];

        data[0] = 10 * Math.log10(Math.pow(new Complex(dataR[0], dataI[0]).abs() / len2, 2));

        double powerOutput = 0;
        for (int i = 1; i < data.length; i++) {
            Complex c = new Complex(dataR[i], dataI[i]);
            double p = c.abs();
            p = p / len2;
            p = p * p;
            p = p * 2;
            double dB = 10 * Math.log10(p);

            powerOutput += p;
            data[i] = dB;
        }
        powerOutput = Math.sqrt(powerOutput);

//        if(powerInput != powerOutput) {
//            throw new RuntimeException("in " + powerInput + " out " + powerOutput);
//        }

        return data;
    }

}
