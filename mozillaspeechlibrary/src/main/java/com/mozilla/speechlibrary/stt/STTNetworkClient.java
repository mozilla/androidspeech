package com.mozilla.speechlibrary.stt;

import android.content.Context;

import androidx.annotation.NonNull;

import com.github.axet.audiolibrary.encoders.Encoder;
import com.github.axet.audiolibrary.encoders.EncoderInfo;
import com.github.axet.audiolibrary.encoders.Factory;
import com.github.axet.audiolibrary.encoders.FormatOPUS;
import com.mozilla.speechlibrary.SpeechServiceSettings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class STTNetworkClient extends STTBaseClient {

    static final String STT_ENDPOINT = "https://speaktome-2.services.mozilla.com/";

    private Encoder mEncoder;
    ByteArrayOutputStream mBaos;

    public STTNetworkClient(@NonNull Context context,
                     @NonNull SpeechServiceSettings settings,
                     @NonNull STTClientCallback callback) {
        super(context, settings, callback);

        mBaos = new ByteArrayOutputStream();
        mIsRunning = true;
    }

    @Override
    public void process() {
        try {
            mCallback.onSTTStart();

            URL obj = new URL(STT_ENDPOINT);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Accept-Language-STT", mSettings.getLanguage());
            con.setRequestProperty("Store-Transcription", mSettings.useStoreTranscriptions() ? "1": "0" );
            con.setRequestProperty("Store-Sample", mSettings.useStoreSamples() ? "1": "0");
            con.setRequestProperty("Product-Tag", mSettings.getProductTag());

            OutputStream os = con.getOutputStream();
            os.write(mBaos.toByteArray());
            os.close();

            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader( con.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                parseBody(new String(response));

            } else {
                mIsRunning = false;

                mCallback.onSTTError("STT Error");
            }

        } catch(Exception e) {
            mIsRunning = false;

            e.printStackTrace();
            mCallback.onSTTError("STT Error: " + e.getMessage());
        }
    }

    @Override
    public void initEncoding(int sampleRate) {
        EncoderInfo ef = new EncoderInfo(1, sampleRate, 16);
        mEncoder = Factory.getEncoder(mContext, FormatOPUS.EXT, ef, mBaos);
    }

    @Override
    public void encode(final short[] buffer, final int pos, final int len) {
        mEncoder.encode(buffer, pos, len);
    }

    @Override
    public void endEncoding() {
        mEncoder.close();
    }

    void parseBody(@NonNull String body) {
        try {
            JSONObject reader = new JSONObject(body);
            JSONArray results = reader.getJSONArray("data");
            final String transcription = results.getJSONObject(0).getString("text");
            final String confidence = results.getJSONObject(0).getString("confidence");
            STTResult sttResult = new STTResult(transcription, Float.parseFloat(confidence));

            mIsRunning = false;
            mCallback.onSTTFinished(sttResult);

        } catch (Exception exc) {
            String error = String.format("Response error: %s", exc.getMessage());

            mIsRunning = false;
            mCallback.onSTTError(error);
        }
    }
}
