package com.mozilla.speechlibrary.networking;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.mozilla.speechlibrary.MozillaSpeechService;
import com.mozilla.speechlibrary.STTResult;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoWebExecutor;
import org.mozilla.geckoview.WebRequest;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class GeckoNetworking extends Networking {

    private Handler mHandler;
    private GeckoWebExecutor mExecutor;

    public GeckoNetworking(Context aContext, MozillaSpeechService aSpeechService, @NonNull GeckoWebExecutor executor) {
        super(aContext, aSpeechService);

        mHandler = new Handler(Looper.getMainLooper());
        mExecutor = executor;
    }

    @Override
    public void doSTT(final ByteArrayOutputStream baos, final NetworkSettings mNetworkSettings) {
        if (cancelled) {
            mSpeechService.notifyListeners(MozillaSpeechService.SpeechState.CANCELED, null);
            return;
        }

        mHandler.post(() -> {
            ByteBuffer input = ByteBuffer.allocateDirect(baos.size());
            input.put(baos.toByteArray());
            WebRequest request = new WebRequest.Builder(STT_ENDPOINT)
                    .body(input)
                    .method("POST")
                    .addHeader("Accept-Language-STT", mNetworkSettings.mLanguage)
                    .addHeader("Store-Transcription", mNetworkSettings.mStoreTranscriptions ? "1": "0" )
                    .addHeader("Store-Sample", mNetworkSettings.mStoreSamples ? "1": "0")
                    .addHeader("Product-Tag", mNetworkSettings.mProductTag)
                    .addHeader("Content-Type", "audio/3gpp")
                    .build();

            mSpeechService.notifyListeners(MozillaSpeechService.SpeechState.DECODING, null);
            mExecutor.fetch(request).then(webResponse -> {
                String body;
                if (webResponse != null) {
                    if (webResponse.body != null) {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        int nRead;
                        byte[] data = new byte[16384];
                        while ((nRead = webResponse.body.read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, nRead);
                        }
                        body = new String(buffer.toByteArray(), StandardCharsets.UTF_8);

                        if (webResponse.statusCode == 200) {
                            JSONObject reader = new JSONObject(body);
                            JSONArray results = reader.getJSONArray("data");
                            final String transcription = results.getJSONObject(0).getString("text");
                            final String confidence = results.getJSONObject(0).getString("confidence");
                            STTResult sttResult = new STTResult(transcription, Float.parseFloat(confidence));
                            mSpeechService.notifyListeners(MozillaSpeechService.SpeechState.STT_RESULT, sttResult);

                        } else {
                            // called when response HTTP status is not "200"
                            String error = String.format("Network Error: %s", webResponse.statusCode);
                            mSpeechService.notifyListeners(MozillaSpeechService.SpeechState.ERROR, error);
                        }

                    } else {
                        // WebResponse body is null
                        mSpeechService.notifyListeners(MozillaSpeechService.SpeechState.ERROR, "Response error null");
                    }

                } else {
                    // WebResponse is null
                    mSpeechService.notifyListeners(MozillaSpeechService.SpeechState.ERROR, "Unknown network Error");
                }

                return null;

            }).exceptionally(throwable -> {
                // Exception happened
                mSpeechService.notifyListeners(MozillaSpeechService.SpeechState.ERROR, String.format("An exception happened during the request: %s", throwable.getMessage()));
                return null;
            });
        });
    }
}