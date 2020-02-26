package com.mozilla.speechlibrary.networking;

import android.content.Context;
import android.os.Looper;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;
import com.mozilla.speechlibrary.MozillaSpeechService;
import com.mozilla.speechlibrary.STTResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.ByteArrayEntity;

public class CustomNetworking extends Networking {

    public CustomNetworking(Context aContext, MozillaSpeechService aSpeechService) {
        super(aContext, aSpeechService);
    }

    @Override
    public void doSTT(final ByteArrayOutputStream baos, NetworkSettings mNetworkSettings) {
        if (cancelled) {
            mSpeechService.notifyListeners(MozillaSpeechService.SpeechState.CANCELED, null);
            return;
        }

        try {
            Looper.prepare();
            ByteArrayEntity byteArrayEntity = new ByteArrayEntity(baos.toByteArray());
            SyncHttpClient client = new SyncHttpClient();
            client.addHeader("Accept-Language-STT", mNetworkSettings.mLanguage);
            client.addHeader("Store-Transcription", mNetworkSettings.mStoreTranscriptions ? "1": "0" );
            client.addHeader("Store-Sample", mNetworkSettings.mStoreSamples ? "1": "0");
            client.addHeader("Product-Tag", mNetworkSettings.mProductTag);

            client.post(mContext, STT_ENDPOINT, byteArrayEntity, "audio/3gpp",
                    new AsyncHttpResponseHandler() {

                        @Override
                        public void onStart() {
                            // called before request is started
                            mSpeechService.notifyListeners(MozillaSpeechService.SpeechState.DECODING, null);
                        }

                        @Override
                        public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                            // Implement cancelation
                            if (cancelled) {
                                return;
                            }

                            // called when response HTTP status is "200 OK"
                            String json = new String(response);
                            try {
                                JSONObject reader = new JSONObject(json);
                                JSONArray results = reader.getJSONArray("data");
                                final String transcription = results.getJSONObject(0).getString("text");
                                final String confidence = results.getJSONObject(0).getString("confidence");
                                STTResult sttResult = new STTResult(transcription, Float.parseFloat(confidence));
                                mSpeechService.notifyListeners(MozillaSpeechService.SpeechState.STT_RESULT, sttResult);
                            } catch (Exception exc) {
                                String error = String.format("Error parsing results: %s", exc.getMessage());
                                mSpeechService.notifyListeners(MozillaSpeechService.SpeechState.ERROR, error);
                            }
                        }

                        @Override
                        public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                            // called when response HTTP status is "4XX" (eg. 401, 403, 404)
                            String error = String.format("Network Error: %s (%s)", errorResponse == null ? "General Error" : new String(errorResponse), String.valueOf(statusCode));
                            mSpeechService.notifyListeners(MozillaSpeechService.SpeechState.ERROR, error);
                        }

                        @Override
                        public void onRetry(int retryNo) {
                            // called when request is retried
                            String error = String.format("Network Error: Retrying %s", String.valueOf(retryNo));
                            mSpeechService.notifyListeners(MozillaSpeechService.SpeechState.ERROR, error);
                        }
                    }
            );

        } catch(Exception e)  {
            mSpeechService.notifyListeners(MozillaSpeechService.SpeechState.ERROR, e.getMessage());
        }
    }
}