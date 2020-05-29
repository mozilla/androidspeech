package com.mozilla.speechlibrary;

import android.content.Context;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mozilla.speechlibrary.recognition.LocalSpeechRecognition;
import com.mozilla.speechlibrary.recognition.NetworkSpeechRecognition;
import com.mozilla.speechlibrary.recognition.SpeechRecognition;

import org.mozilla.geckoview.GeckoWebExecutor;

import java.util.concurrent.Executors;

public class SpeechService {

    private Context mContext;
    private SpeechRecognition mSpeechRecognition;

    public SpeechService(@NonNull Context context) {
        mContext = context;
    }

    synchronized
    public void start(@NonNull SpeechServiceSettings settings, @NonNull SpeechResultCallback delegate) {
        start(settings, null, delegate);
    }

    synchronized
    public void start(@NonNull SpeechServiceSettings settings, @Nullable GeckoWebExecutor executor, @NonNull SpeechResultCallback delegate) {
        if (mSpeechRecognition != null && mSpeechRecognition.isRunning()) {
            mSpeechRecognition.stop();
        }

        if (settings.useUseDeepSpeech()) {
            mSpeechRecognition = new LocalSpeechRecognition(
                    mContext);

        } else {
            mSpeechRecognition = new NetworkSpeechRecognition(
                    mContext,
                    executor
            );
        }

        execute(() -> mSpeechRecognition.start(settings, delegate));
    }

    public void stop() {
        if (mSpeechRecognition != null) {
            mSpeechRecognition.stop();
        }
    }

    private void execute(@NonNull final Runnable task) {
        Executors.newSingleThreadExecutor().submit(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            task.run();
        });
    }

}
