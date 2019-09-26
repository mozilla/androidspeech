package com.mozilla.speechapp;

import android.Manifest;

import android.app.Activity;
import android.app.DownloadManager;

import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.database.Cursor;

import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;

import android.net.Uri;

import android.os.Bundle;
import android.os.AsyncTask;

import android.util.Log;

import android.view.View;
import android.view.WindowManager;

import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import com.mozilla.speechlibrary.ISpeechRecognitionListener;
import com.mozilla.speechlibrary.MozillaSpeechService;
import com.mozilla.speechlibrary.STTResult;
import com.mozilla.speechmodule.R;

import java.io.File;

import net.lingala.zip4j.core.ZipFile;

import static android.support.constraint.Constraints.TAG;

public class MainActivity extends AppCompatActivity implements ISpeechRecognitionListener, CompoundButton.OnCheckedChangeListener {

    private static long sDownloadId;
    private static DownloadManager sDownloadManager;

    private MozillaSpeechService mMozillaSpeechService;
    private GraphView mGraph;
    private long mDtstart;
    private LineGraphSeries<DataPoint> mSeries1;
    private EditText mPlain_text_input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mMozillaSpeechService = MozillaSpeechService.getInstance();
        initialize();
    }

    private void initialize() {

        Button buttonStart, buttonCancel;
        EditText txtProdutTag, txtLanguage;
        Switch switchTranscriptions = findViewById(R.id.switchTranscriptions);
        Switch switchSamples = findViewById(R.id.switchSamples);
        Switch useDeepSpeech = findViewById(R.id.useDeepSpeech);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    123);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    124);
        }

        buttonStart = findViewById(R.id.button_start);
        buttonCancel = findViewById(R.id.button_cancel);
        txtProdutTag = findViewById(R.id.txtProdutTag);
        txtLanguage = findViewById(R.id.txtLanguage);

        mPlain_text_input = findViewById(R.id.plain_text_input);
        buttonStart.setOnClickListener((View v) ->  {
            try {
                mMozillaSpeechService.addListener(this);
                mDtstart = System.currentTimeMillis();
                mSeries1.resetData(new DataPoint[0]);
                mMozillaSpeechService.setLanguage(txtLanguage.getText().toString());
                mMozillaSpeechService.setProductTag(txtProdutTag.getText().toString());
                mMozillaSpeechService.setModelPath(getExternalFilesDir("models").getAbsolutePath());
                if (mMozillaSpeechService.ensureModelInstalled()) {
                    mMozillaSpeechService.start(getApplicationContext());
                } else {
                    maybeDownloadOrExtractModel(getExternalFilesDir("models").getAbsolutePath(), mMozillaSpeechService.getLanguageDir());
                }
            } catch (Exception e) {
                Log.d(TAG, e.getLocalizedMessage());
                e.printStackTrace();
            }
        });

        buttonCancel.setOnClickListener((View v) ->  {
            try {
                mMozillaSpeechService.cancel();
            } catch (Exception e) {
                Log.d(TAG, e.getLocalizedMessage());
                e.printStackTrace();
            }
        });

        switchTranscriptions.setOnCheckedChangeListener(this);
        switchSamples.setOnCheckedChangeListener(this);
        useDeepSpeech.setOnCheckedChangeListener(this);
        switchTranscriptions.toggle();
        switchSamples.toggle();
        useDeepSpeech.toggle();

        mGraph = findViewById(R.id.graph);
        mSeries1 = new LineGraphSeries<>(new DataPoint[0]);
        mGraph.addSeries(mSeries1);
        mGraph.getViewport().setXAxisBoundsManual(true);
        mGraph.getViewport().setScalable(true);
        mGraph.getViewport().setScalableY(true);
        mGraph.getViewport().setScrollable(true); // enables horizontal scrolling
        mGraph.getViewport().setScrollableY(true); // enables vertical scrolling
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void onSpeechStatusChanged(MozillaSpeechService.SpeechState aState, Object aPayload){
        this.runOnUiThread(() -> {
            switch (aState) {
                case DECODING:
                    mPlain_text_input.append("Decoding... \n");
                    break;
                case MIC_ACTIVITY:
                    long mPointx = System.currentTimeMillis() - mDtstart;
                    mSeries1.appendData(new DataPoint(Math.round(mPointx) + 1, (double)aPayload * -1), true, 3000);
                    break;
                case STT_RESULT:
                    String message = String.format("Success: %s (%s)", ((STTResult)aPayload).mTranscription, ((STTResult)aPayload).mConfidence);
                    mPlain_text_input.append(message + "\n");
                    removeListener();
                    break;
                case START_LISTEN:
                    mPlain_text_input.append("Started to listen\n");
                    break;
                case NO_VOICE:
                    mPlain_text_input.append("No Voice detected\n");
                    removeListener();
                    break;
                case CANCELED:
                    mPlain_text_input.append("Canceled\n");
                    removeListener();
                    break;
                case ERROR:
                    mPlain_text_input.append("Error:" + aPayload + " \n");
                    removeListener();
                    break;
                default:
                    break;
            }
        });
    }

    public void removeListener() {
        mMozillaSpeechService.removeListener(this);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.equals(findViewById(R.id.switchTranscriptions))) {
            mMozillaSpeechService.storeTranscriptions(isChecked);
        } else if (buttonView.equals(findViewById(R.id.switchSamples))) {
            mMozillaSpeechService.storeSamples(isChecked);
        } else if (buttonView.equals(findViewById(R.id.useDeepSpeech))) {
            mMozillaSpeechService.useDeepSpeech(isChecked);
        }
    }

    private class AsyncUnzip extends AsyncTask<String, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            Toast noModel = Toast.makeText(getApplicationContext(), "Extracting downloaded model", Toast.LENGTH_LONG);
            mPlain_text_input.append("Extracting downloaded model\n");
            noModel.show();
        }

        @Override
        protected Boolean doInBackground(String...params) {
            String aZipFile = params[0], aRootModelsPath = params[1];
            try {
                ZipFile zf = new ZipFile(aZipFile);
                zf.extractAll(aRootModelsPath);
            } catch (Exception e) {
                Log.d(TAG, e.getLocalizedMessage());
                e.printStackTrace();
            }

            return (new File(aZipFile)).delete();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Button buttonStart = findViewById(R.id.button_start), buttonCancel = findViewById(R.id.button_cancel);
            mMozillaSpeechService.start(getApplicationContext());
            buttonStart.setEnabled(true);
            buttonCancel.setEnabled(true);
        }

    }

    public void maybeDownloadOrExtractModel(String aModelsPath, String aLang) {
        String zipFile   = aModelsPath + "/" + aLang + ".zip";
        String aModelPath= aModelsPath + "/" + aLang + "/";

        File aModelFolder = new File(aModelPath);
        if (!aModelFolder.exists()) {
            aModelFolder.mkdirs();
        }

        Uri modelZipURL  = Uri.parse(mMozillaSpeechService.getModelDownloadURL());
        Uri modelZipFile = Uri.parse("file://" + zipFile);

        Button buttonStart = findViewById(R.id.button_start), buttonCancel = findViewById(R.id.button_cancel);
        buttonStart.setEnabled(false);
        buttonCancel.setEnabled(false);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    Cursor c = sDownloadManager.query(query);
                    if (c.moveToFirst()) {
                        int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
                            Log.d(TAG, "Download successfull");

                            new AsyncUnzip().execute(zipFile, aModelPath);
                        }
                    }
                }
            }
        };

        Toast noModel = Toast.makeText(getApplicationContext(), "No model has been found for language '" + aLang + "'. Triggering download ...", Toast.LENGTH_LONG);
        mPlain_text_input.append("No model has been found for language '" + aLang + "'. Triggering download ...\n");
        noModel.show();

        sDownloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(modelZipURL);
        request.setTitle("DeepSpeech " + aLang);
        request.setDescription("DeepSpeech Model");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setVisibleInDownloadsUi(false);
        request.setDestinationUri(modelZipFile);
        sDownloadId = sDownloadManager.enqueue(request);

        getApplicationContext().registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }
}
