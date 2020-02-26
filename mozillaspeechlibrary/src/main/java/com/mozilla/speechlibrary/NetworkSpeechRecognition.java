package com.mozilla.speechlibrary;

import android.content.Context;
import android.media.AudioRecord;
import android.os.Process;

import com.github.axet.audiolibrary.encoders.Encoder;
import com.github.axet.audiolibrary.encoders.EncoderInfo;
import com.github.axet.audiolibrary.encoders.Factory;
import com.github.axet.audiolibrary.encoders.FormatOPUS;
import com.github.axet.audiolibrary.encoders.Sound;
import com.mozilla.speechlibrary.networking.CustomNetworking;
import com.mozilla.speechlibrary.networking.GeckoNetworking;
import com.mozilla.speechlibrary.networking.NetworkSettings;
import com.mozilla.speechlibrary.networking.Networking;

import org.mozilla.geckoview.GeckoWebExecutor;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

class NetworkSpeechRecognition implements Runnable {

    Vad mVad;
    short[] mBuftemp;
    ByteArrayOutputStream baos ;
    int mMinimumVoice = 250;
    int mMaximumSilence = 1500;
    int mUpperLimit = 10;
    static final int FRAME_SIZE = 160;
    boolean done;
    boolean cancelled;
    Context mContext;
    int mSampleRate;
    int mChannels;
    MozillaSpeechService mService;
    Networking network;
    NetworkSettings mNetworkSettings;
    GeckoWebExecutor mExecutor;

    protected NetworkSpeechRecognition(int aSampleRate, int aChannels, Vad aVad, Context aContext,
                                       MozillaSpeechService aService, NetworkSettings mNetworkSettings) {
        this.mVad = aVad;
        this.mContext = aContext;
        this.mSampleRate = aSampleRate;
        this.mChannels = aChannels;
        this.mService = aService;
        this.mNetworkSettings = mNetworkSettings;
        this.mExecutor = aService.getGeckoWebExecutor();
    }

    public void run() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

            baos = new ByteArrayOutputStream();
            boolean finishedvoice = false;
            long samplesvoice = 0 ;
            long samplessilence = 0 ;
            boolean touchedvoice = false;
            boolean touchedsilence = false;
            int vad = 0;
            long dtantes = System.currentTimeMillis();
            long dtantesmili = 	System.currentTimeMillis();
            boolean raisenovoice = false;

            try {
                Class.forName("org.mozilla.geckoview.GeckoWebExecutor");
                if (mExecutor == null) {
                    throw new IllegalStateException("GeckoWebExecutor not set, call setGeckoWebExecutor first.");
                }
                network = new GeckoNetworking(mContext, mService, mExecutor);

            } catch (ClassNotFoundException exception) {
                network = new CustomNetworking(mContext, mService);
            }

            AudioRecord recorder = Sound.getAudioRecord(mChannels, mSampleRate);
            EncoderInfo ef = new EncoderInfo(1, mSampleRate, 16);
            Encoder e = Factory.getEncoder(mContext, FormatOPUS.EXT, ef, baos);

            recorder.startRecording();
            mService.notifyListeners(MozillaSpeechService.SpeechState.START_LISTEN, null);

            while (!this.done && !this.cancelled) {
                int nshorts = 0 ;

                try {
                    mBuftemp = new short[FRAME_SIZE * mChannels * 2];
                    nshorts = recorder.read(mBuftemp, 0, mBuftemp.length);
                    vad = mVad.feed(mBuftemp, nshorts);
                    e.encode(mBuftemp, 0, nshorts);
                    double[] fft =  Sound.fft(mBuftemp, 0, nshorts);
                    double fftsum = Arrays.stream(fft).sum()/fft.length;
                    mService.notifyListeners(MozillaSpeechService.SpeechState.MIC_ACTIVITY, fftsum);
                }
                catch (Exception exc) {
                    exc.printStackTrace();
                }

                long dtdepois = System.currentTimeMillis();

                if (vad == 0) {
                    if (touchedvoice) {
                        samplessilence += dtdepois - dtantesmili;
                        if (samplessilence >  mMaximumSilence) touchedsilence = true;
                    }
                }
                else {
                    samplesvoice  += dtdepois - dtantesmili;
                    if (samplesvoice >  mMinimumVoice) touchedvoice = true;
                }
                dtantesmili = dtdepois;

                if (touchedvoice && touchedsilence)
                    finishedvoice = true;

                if (finishedvoice) {
                    this.done = true;
                    network.doSTT(baos, mNetworkSettings);
                }

                if ((dtdepois - dtantes)/1000 > mUpperLimit ) {
                    this.done = true;
                    if (touchedvoice) {
                        network.doSTT(baos, mNetworkSettings);
                    }
                    else {
                        raisenovoice = true;
                    }
                }

                if (nshorts <= 0)
                    break;
            }

            e.close();
            mVad.stop();
            recorder.stop();
            recorder.release();

            if (raisenovoice) mService.notifyListeners(MozillaSpeechService.SpeechState.NO_VOICE, null);

            if (cancelled) {
                cancelled = false;
                mService.notifyListeners(MozillaSpeechService.SpeechState.CANCELED, null);
                return;
            }
        }
        catch (Exception exc)
        {
            String error = String.format("General audio error %s", exc.getMessage());
            mService.notifyListeners(MozillaSpeechService.SpeechState.ERROR, error);
            exc.printStackTrace();
        }
    }


    public void cancel(){
        cancelled = true;
        mVad.stop();
        network.cancel();
    }
}
