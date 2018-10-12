package com.mozilla.speechapp;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.mozilla.speechlibrary.ISpeechRecognitionListener;
import com.mozilla.speechlibrary.MozillaSpeechService;
import com.mozilla.speechlibrary.STTResult;
import com.mozilla.speechmodule.R;

import static android.support.constraint.Constraints.TAG;

public class MainActivity extends AppCompatActivity implements ISpeechRecognitionListener, CompoundButton.OnCheckedChangeListener {

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
                mMozillaSpeechService.start(getApplicationContext());
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
        switchTranscriptions.toggle();
        switchSamples.toggle();

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
        } else {
            mMozillaSpeechService.storeSamples(isChecked);
        }
    }
}
