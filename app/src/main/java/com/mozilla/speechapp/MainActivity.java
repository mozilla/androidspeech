package com.mozilla.speechapp;

import android.Manifest;
import android.app.DownloadManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.mozilla.speechlibrary.utils.download.Download;
import com.mozilla.speechlibrary.utils.download.DownloadJob;
import com.mozilla.speechlibrary.utils.download.DownloadsManager;
import com.mozilla.speechlibrary.utils.zip.UnzipCallback;
import com.mozilla.speechlibrary.utils.zip.UnzipTask;
import com.mozilla.speechlibrary.stt.STTResult;
import com.mozilla.speechlibrary.SpeechResultCallback;
import com.mozilla.speechlibrary.SpeechService;
import com.mozilla.speechlibrary.SpeechServiceSettings;
import com.mozilla.speechlibrary.utils.storage.StorageUtils;
import com.mozilla.speechlibrary.utils.ModelUtils;
import com.mozilla.speechmodule.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        SpeechResultCallback,
        DownloadsManager.DownloadsListener,
        UnzipCallback {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final @StorageUtils.StorageType int STORAGE_TYPE = StorageUtils.INTERNAL_STORAGE;

    private SpeechService mSpeechService;
    private long mDtStart;
    private LineGraphSeries<DataPoint> mSeries1;
    private TextView mLogText;
    private EditText mTxtLanguage;
    private Switch mTranscriptionsSwitch;
    private Switch mStoreSamplesSwitch;
    private Switch mDeepSpeechSwitch;
    private ProgressBar mProgressBar;
    private Button mStartButton;
    private DownloadsManager mDownloadManager;
    private UnzipTask mUnzip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSpeechService = new SpeechService(this);
        mUnzip = new UnzipTask(this);
        mDownloadManager = new DownloadsManager(this);
        initialize();
    }

    @Override
    protected void onStart() {
        super.onStart();

        mUnzip.addListener(this);
        mDownloadManager.init();
        mDownloadManager.addListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();

        mUnzip.removeListener(this);
        mDownloadManager.end();
        mDownloadManager.removeListener(this);
    }

    @Override
    protected void onDestroy() {
        mSpeechService.stop();
        super.onDestroy();
    }

    private void initialize() {
        EditText txtProductTag;
        mTranscriptionsSwitch = findViewById(R.id.switchTranscriptions);
        mStoreSamplesSwitch = findViewById(R.id.switchSamples);
        mDeepSpeechSwitch = findViewById(R.id.useDeepSpeech);
        mProgressBar = findViewById(R.id.download_progress);

        List<String> permissions = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (permissions.size() > 0) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), 123);
        }

        mStartButton = findViewById(R.id.button_start);
        txtProductTag = findViewById(R.id.txtProdutTag);
        mTxtLanguage = findViewById(R.id.txtLanguage);

        mLogText = findViewById(R.id.plain_text_input);
        mLogText.setMovementMethod(new ScrollingMovementMethod());

        mStartButton.setOnClickListener((View v) ->  {
            mDtStart = System.currentTimeMillis();
            mSeries1.resetData(new DataPoint[0]);

            SpeechServiceSettings.Builder builder = new SpeechServiceSettings.Builder()
                    .withLanguage(mTxtLanguage.getText().toString())
                    .withStoreSamples(mStoreSamplesSwitch.isChecked())
                    .withStoreTranscriptions(mTranscriptionsSwitch.isChecked())
                    .withProductTag(txtProductTag.getText().toString())
                    .withUseDeepSpeech(mDeepSpeechSwitch.isChecked());
            if (mDeepSpeechSwitch.isChecked()) {
                String language = mTxtLanguage.getText().toString();
                String modelPath = ModelUtils.modelPath(this, language);

                if (ModelUtils.isReady(modelPath)) {
                    // The model is already downloaded and unzipped
                    builder.withModelPath(modelPath);
                    mSpeechService.start(builder.build(), this);

                } else {
                    String zipPath = ModelUtils.modelDownloadOutputPath(
                            this,
                            language,
                            STORAGE_TYPE);
                    if (new File(zipPath).exists()) {
                        String zipOutputPath = ModelUtils.modelPath(this, language);
                        if (zipOutputPath != null) {
                            mUnzip.start(zipPath, zipOutputPath);

                        } else {
                            mLogText.append("Output model path error");
                        }

                    } else {
                        // The model needs to be downloaded
                        downloadModel(language);
                    }
                }

            } else {
                mSpeechService.start(builder.build(), this);
            }
        });

        findViewById(R.id.button_cancel).setOnClickListener((View v) ->  {
            try {
                mSpeechService.stop();
                mDownloadManager.getDownloads().forEach(download -> {
                    if (download.getStatus() != DownloadManager.STATUS_SUCCESSFUL) {
                        mDownloadManager.removeDownload(download.getId(), true);
                    }
                });
                mUnzip.cancel();

                mLogText.append("Cancel\n");
                mProgressBar.setVisibility(View.GONE);
                mStartButton.setEnabled(true);

            } catch (Exception e) {
                Log.d(TAG, e.getLocalizedMessage());
                e.printStackTrace();
            }
        });

        findViewById(R.id.button_clear).setOnClickListener(v -> mLogText.setText(""));

        mTranscriptionsSwitch.toggle();
        mStoreSamplesSwitch.toggle();
        mDeepSpeechSwitch.toggle();

        GraphView mGraph = findViewById(R.id.graph);
        mSeries1 = new LineGraphSeries<>(new DataPoint[0]);
        mGraph.addSeries(mSeries1);
        mGraph.getViewport().setXAxisBoundsManual(true);
        mGraph.getViewport().setScalable(true);
        mGraph.getViewport().setScalableY(true);
        mGraph.getViewport().setScrollable(true); // enables horizontal scrolling
        mGraph.getViewport().setScrollableY(true); // enables vertical scrolling
    }

    private void downloadModel(@NonNull String language) {
        String modelUrl = ModelUtils.modelDownloadUrl(language);

        // Check if the model is already downloaded
        Download download = mDownloadManager.getDownloads().stream()
                .filter(item ->
                        item.getStatus() == DownloadManager.STATUS_SUCCESSFUL &&
                                item.getUri().equals(modelUrl))
                .findFirst().orElse(null);
        if (download != null) {
            onDownloadCompleted(download);

        } else {
            // Check if the model is in progress
            boolean isInProgress = mDownloadManager.getDownloads().stream()
                    .anyMatch(item ->
                            item.getStatus() != DownloadManager.STATUS_FAILED &&
                                    item.getUri().equals(modelUrl));
            if (!isInProgress) {
                // Download model
                DownloadJob job = DownloadJob.create(
                        modelUrl,
                        "application/zip",
                        0,
                        null,
                        ModelUtils.modelDownloadOutputPath(this, language, STORAGE_TYPE));
                mDownloadManager.startDownload(job, STORAGE_TYPE);
                mLogText.append("Model not available, downloading...\n");

            } else {
                mLogText.append("Model download already in progress\n");
            }
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // SpeechResultCallback

    @Override
    public void onStartListen() {
        mLogText.append("Started to listen\n");
        mProgressBar.setVisibility(View.GONE);
        mStartButton.setEnabled(false);
    }

    @Override
    public void onMicActivity(double fftsum) {
        mLogText.append("Mic activity detected\n");
        long mPointX = System.currentTimeMillis() - mDtStart;
        mSeries1.appendData(new DataPoint(Math.round(mPointX) + 1, fftsum * -1), true, 3000);
    }

    @Override
    public void onDecoding() {
        mLogText.append("Decoding... \n");
    }

    @Override
    public void onSTTResult(@Nullable STTResult result) {
        if (result != null) {
            String message = String.format("Success: %s (%s)", result.mTranscription, result.mConfidence);
            mLogText.append(message + "\n");
        }
        mStartButton.setEnabled(true);
    }

    @Override
    public void onNoVoice() {
        mLogText.append("No Voice detected\n");
        mStartButton.setEnabled(true);
    }

    @Override
    public void onError(@SpeechResultCallback.ErrorType int errorType, @Nullable String error) {
        if (errorType == SPEECH_ERROR) {
            mLogText.append("Speech recognition Error:" + error + " \n");
            mProgressBar.setVisibility(View.GONE);
            mStartButton.setEnabled(true);

        } else if (errorType == MODEL_NOT_FOUND) {
            downloadModel(mTxtLanguage.getText().toString());
        }

    }

    // DownloadsManager

    @Override
    public void onDownloadsUpdate(@NonNull List<Download> downloads) {
        downloads.forEach(download -> {
            if (download.getStatus() == DownloadManager.STATUS_RUNNING &&
                    ModelUtils.isModelUri(download.getUri())) {
                mLogText.append("Downloading " + download.getFilename() + ": " + download.getProgress() + " \n");
            }
        });
    }

    @Override
    public void onDownloadCompleted(@NonNull Download download) {
        String language = ModelUtils.languageForUri(download.getUri());
        if (download.getStatus() == DownloadManager.STATUS_SUCCESSFUL &&
                ModelUtils.isModelUri(download.getUri())) {
            try {
                File file = new File(download.getOutputFile());
                if (file.exists()) {
                    String zipOutputPath = ModelUtils.modelPath(this, language);
                    if (zipOutputPath != null) {
                        mUnzip.start(download.getOutputFile(), zipOutputPath);

                    } else {
                        mLogText.append("Output model path error");
                    }

                } else {
                    mDownloadManager.removeDownload(download.getId(), true);
                }

            } catch (NullPointerException e) {
                mLogText.append("Model not available: " + language);
            }

        } else {
            mLogText.append("Model download error: " + language);
        }
    }

    @Override
    public void onDownloadError(@NonNull String error, @NonNull String file) {
        mLogText.append(error + "\n");
    }

    // UnzipTask

    @Override
    public void onUnzipStart(@NonNull String zipFile) {
        mLogText.append("Unzipping started" + "\n");
    }

    @Override
    public void onUnzipProgress(@NonNull String zipFile, double progress) {
        mLogText.append("Unzipping: " + progress + "\n");
    }

    @Override
    public void onUnzipFinish(@NonNull String zipFile, @NonNull String outputPath) {
        mLogText.append("Unzipping finished" + "\n");
        File file = new File(zipFile);
        if (file.exists()) {
            file.delete();
        }
    }

    @Override
    public void onUnzipCancelled(@NonNull String zipFile) {
        mLogText.append("Unzipping cancelled" + "\n");
    }

    @Override
    public void onUnzipError(@NonNull String zipFile, @Nullable String error) {
        mLogText.append("Unzipping error: " + error + "\n");
        File file = new File(zipFile);
        if (file.exists()) {
            file.delete();
        }
    }
}
