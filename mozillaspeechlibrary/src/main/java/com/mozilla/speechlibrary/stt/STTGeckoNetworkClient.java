package com.mozilla.speechlibrary.stt;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.mozilla.speechlibrary.SpeechServiceSettings;

import org.mozilla.geckoview.GeckoWebExecutor;
import org.mozilla.geckoview.WebRequest;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class STTGeckoNetworkClient extends STTNetworkClient {

    private GeckoWebExecutor mExecutor;
    private Handler mHandler;

    public STTGeckoNetworkClient(@NonNull Context context,
                          @NonNull SpeechServiceSettings settings,
                          @NonNull STTClientCallback callback,
                          @NonNull GeckoWebExecutor executor) {
        super(context, settings, callback);

        mHandler = new Handler(Looper.getMainLooper());
        mExecutor = executor;
    }

    @Override
    public void process() {
        mCallback.onSTTStart();

        byte[] byteArray = mBaos.toByteArray();
        ByteBuffer input = ByteBuffer.allocateDirect(byteArray.length);
        input.put(byteArray);
        WebRequest request = new WebRequest.Builder(STT_ENDPOINT)
                .body(input)
                .method("POST")
                .addHeader("Accept-Language-STT", mSettings.getLanguage())
                .addHeader("Store-Transcription", mSettings.useStoreTranscriptions() ? "1": "0" )
                .addHeader("Store-Sample", mSettings.useStoreSamples() ? "1": "0")
                .addHeader("Product-Tag", mSettings.getProductTag())
                .addHeader("Content-Type", "audio/3gpp")
                .build();

        mHandler.post(() -> mExecutor.fetch(request).then(webResponse -> {
            String body;
            if (webResponse != null) {
                if (webResponse.body != null) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    int nRead;
                    byte[] data = new byte[1024];
                    while ((nRead = webResponse.body.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    body = new String(buffer.toByteArray(), StandardCharsets.UTF_8);

                    if (webResponse.statusCode == 200) {
                        parseBody(body);

                    } else {
                        // HTTP status is not 200
                        mIsRunning = false;
                        mCallback.onSTTError("STT Error");
                    }

                } else {
                    // WebResponse body is null
                    mIsRunning = false;
                    mCallback.onSTTError("STT Error: Response body is null");
                }

            } else {
                // WebResponse is null
                mIsRunning = false;
                mCallback.onSTTError("STT Error: Unknown network Error");
            }

            return null;

        }).exceptionally(throwable -> {
            // Exception happened
            mIsRunning = false;
            throwable.printStackTrace();
            mCallback.onSTTError("STT Error: " + throwable.getMessage());

            return null;
        }));
    }

}
