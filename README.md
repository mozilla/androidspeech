# androidspeech

This is an Android library containing an API to Mozilla's speech recognition services. 


## Installation
```
dependencies { 
      implementation 'com.github.mozilla:mozillaspeechlibrary:2.0.0'
      implementation 'com.github.mozilla:mozillaspeechutils:2.0.0'      // Just in case you want to use the utils for downloading/unzipping
}
```

## Demo app
Just run the demo application inside the folder `app`

## Usage
The API encapsulates the microphone capture, audio encoding, voice activity detection and network 
communication. So for this reason its integration is very simple: just call `start()` and handle the events in your frontend.

#### First define a listener to capture the events:
```
SpeechResultCallback mVoiceSearchListener = new SpeechResultCallback() {

    @Override
    public void onStartListen() {
        // Handle when the api successfully opened the microphone and started listening
    }

    @Override
    public void onMicActivity(double fftsum) {
        // Captures the activity from the microphone
    }

    @Override
    public void onDecoding() {
        // Handle when the speech object changes to decoding state
    }

    @Override
    public void onSTTResult(@Nullable STTResult result) {
        // When the api finished processing and returned a hypothesis
    }

    @Override
    public void onNoVoice() {
        // Handle when the api didn't detect any voice
    }

    @Override
    public void onError(@SpeechResultCallback.ErrorType int errorType, @Nullable String error) {
        // Handle when any error occurred

    }
};
```
#### Create an instance of the Speech Service:
```
    mSpeechService = new SpeechService(this);
```

#### Start a request:
```
    SpeechServiceSettings.Builder builder = new SpeechServiceSettings.Builder()
        .withLanguage("en-US")
        .withStoreSamples(true)
        .withStoreTranscriptions(true)
        .withProductTag("product-tag")
        .withUseDeepSpeech(true)            // If using DeepSpeech
        .withModelPath("path/to/model");    // If using DeepSpeech
    mSpeechService.start(builder.build(), mVoiceSearchListener);
```

#### In the case you want to cancel a progressing operation:
```
    mSpeechService.stop();
```

**Note**: Your app will need `RECORD_AUDIO`, `WRITE_EXTERNAL_STORAGE` and `READ_EXTERNAL_STORAGE` permissions to be [set](https://github.com/mozilla/androidspeech/blob/master/app/src/main/AndroidManifest.xml#L5) in AndroidManifest.xml manifest and [requested](https://github.com/benfrancis/androidspeech/blob/master/app/src/main/java/com/mozilla/speechapp/MainActivity.java#L78) at runtime.
