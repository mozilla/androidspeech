package com.mozilla.speechlibrary;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Hotword recorder.
 * <p>
 * Expected flow:
 * 1. Create HotwordRecorder()
 * 2. Call startRecording()
 * 3. Call stopRecording()
 * 4. Call validateSample()
 * 5. Call writeWav()
 * 6. Repeat 2-5 until desired number of samples is reached.
 * 7. Call writeConfig()
 */
public class HotwordRecorder {
    private int AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION;
    private int CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO;
    private int SAMPLE_RATE = 16000;
    private int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, ENCODING);
    private AudioFormat AUDIO_FORMAT = new AudioFormat.Builder().setEncoding(ENCODING)
                                                                .setSampleRate(SAMPLE_RATE)
                                                                .setChannelMask(CHANNEL_MASK)
                                                                .build();

    private ByteArrayOutputStream mPcmStream;
    private AudioRecord mRecorder;
    private boolean mRecording;
    private Thread mThread;
    private String mHotwordKey;
    private double[] mSampleLengths;
    private int mSamplesTaken;

    /**
     * Hotword recording constructor.
     *
     * @param key              Hotword key
     * @param numberRecordings Number of recordings to be taken
     */
    public HotwordRecorder(String key, int numberRecordings) {
        mHotwordKey = key;
        mPcmStream = new ByteArrayOutputStream();
        mRecording = false;
        mSampleLengths = new double[numberRecordings];
        mSamplesTaken = 0;
    }

    /**
     * Start the recording process.
     */
    public void startRecording() {
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
        }

        mPcmStream.reset();
        mRecorder = new AudioRecord.Builder().setAudioSource(AUDIO_SOURCE)
                                             .setAudioFormat(AUDIO_FORMAT)
                                             .setBufferSizeInBytes(BUFFER_SIZE)
                                             .build();
        mRecorder.startRecording();
        mRecording = true;
        mThread = new Thread(readAudio);
        mThread.start();
    }

    /**
     * Stop the recording process.
     */
    public void stopRecording() {
        if (mRecorder != null && mRecorder.getState() == AudioRecord.STATE_INITIALIZED) {
            mRecording = false;
            mRecorder.stop();
        }
    }

    /**
     * Read audio from the audio recorder stream.
     */
    private Runnable readAudio =
        new Runnable(){
            public void run(){
                int readBytes;
                short[] buffer = new short[BUFFER_SIZE];

                while (mRecording) {
                    readBytes = mRecorder.read(buffer, 0, BUFFER_SIZE);

                    if (readBytes != AudioRecord.ERROR_INVALID_OPERATION) {
                        for (short s : buffer) {
                            writeShort(mPcmStream, s);
                        }
                    }
                }

                mRecorder.release();
                mRecorder = null;
            }
        };

    /**
     * Convert raw PCM data to a wav file.
     * <p>
     * See: https://stackoverflow.com/questions/43569304/android-how-can-i-write-byte-to-wav-file
     *
     * @return Byte array containing wav file data.
     */
    private byte[] pcmToWav() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        byte[] pcmAudio = mPcmStream.toByteArray();

        writeString(stream, "RIFF"); // chunk id
        writeInt(stream, 36 + pcmAudio.length); // chunk size
        writeString(stream, "WAVE"); // format
        writeString(stream, "fmt "); // subchunk 1 id
        writeInt(stream, 16); // subchunk 1 size
        writeShort(stream, (short)1); // audio format (1 = PCM)
        writeShort(stream, (short)1); // number of channels
        writeInt(stream, SAMPLE_RATE); // sample rate
        writeInt(stream, SAMPLE_RATE * 2); // byte rate
        writeShort(stream, (short)2); // block align
        writeShort(stream, (short)16); // bits per sample
        writeString(stream, "data"); // subchunk 2 id
        writeInt(stream, pcmAudio.length); // subchunk 2 size
        stream.write(pcmAudio);

        return stream.toByteArray();
    }

    /**
     * Trim the silence from this recording.
     */
    private void trimSilence() {
        // TODO
    }

    /**
     * Validate this recording.
     *
     * @return Boolean indicating whether or not the sample is valid.
     */
    public boolean validateSample() {
        if (mSamplesTaken >= mSampleLengths.length) {
            return false;
        }

        trimSilence();

        double seconds = mPcmStream.size() / SAMPLE_RATE;

        if (seconds > 1.6) {
            return false;
        }

        for (int i = 0; i < mSamplesTaken; ++i) {
            if (Math.abs(mSampleLengths[i] - seconds) > 0.3) {
                return false;
            }
        }

        mSampleLengths[mSamplesTaken++] = seconds;
        return true;
    }

    /**
     * Write a 32-bit integer to an output stream, in Little Endian format.
     *
     * @param output Output stream
     * @param value  Integer value
     */
    private void writeInt(final ByteArrayOutputStream output, final int value) {
        output.write(value);
        output.write(value >> 8);
        output.write(value >> 16);
        output.write(value >> 24);
    }

    /**
     * Write a 16-bit integer to an output stream, in Little Endian format.
     *
     * @param output Output stream
     * @param value  Integer value
     */
    private void writeShort(final ByteArrayOutputStream output, final short value) {
        output.write(value);
        output.write(value >> 8);
    }

    /**
     * Write a string to an output stream.
     *
     * @param output Output stream
     * @param value  String value
     */
    private void writeString(final ByteArrayOutputStream output, final String value) {
        for (int i = 0; i < value.length(); i++) {
            output.write(value.charAt(i));
        }
    }

    /**
     * Generate a JSON config for the hotword.
     *
     * @return JSONObject containing config.
     */
    private JSONObject generateConfig() {
        JSONObject obj = new JSONObject();

        try {
            obj.put("hotword_key", mHotwordKey);
            obj.put("kind", "personal");
            obj.put("dtw_ref", 0.22);
            obj.put("from_mfcc", 1);
            obj.put("to_mfcc", 13);
            obj.put("band_radius", 10);
            obj.put("shift", 10);
            obj.put("window_size", 10);
            obj.put("sample_rate", SAMPLE_RATE);
            obj.put("frame_length_ms", 25.0);
            obj.put("frame_shift_ms", 10.0);
            obj.put("num_mfcc", 13);
            obj.put("num_mel_bins", 13);
            obj.put("mel_low_freq", 20);
            obj.put("cepstral_lifter", 22.0);
            obj.put("dither", 0.0);
            obj.put("window_type", "povey");
            obj.put("use_energy", false);
            obj.put("energy_floor", 0.0);
            obj.put("raw_energy", true);
            obj.put("preemphasis_coefficient", 0.97);
        } finally {
            return obj;
        }
    }

    /**
     * Write a wav file from the current sample.
     *
     * @param output Output file
     * @throws IOException
     */
    public void writeWav(final File output) throws IOException {
        byte[] wav = pcmToWav();
        FileOutputStream stream = null;

        try {
            stream = new FileOutputStream(output);
            stream.write(wav);
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }

    /**
     * Write a JSON config file for this hotword.
     *
     * @param output Output file
     * @throws IOException
     */
    public void writeConfig(final File output) throws IOException {
        byte[] config = generateConfig().toString().getBytes();
        FileOutputStream stream = null;

        try {
            stream = new FileOutputStream(output);
            stream.write(config);
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }
}
