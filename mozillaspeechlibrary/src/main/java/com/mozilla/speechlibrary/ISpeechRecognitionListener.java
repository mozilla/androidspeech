package com.mozilla.speechlibrary;

public interface ISpeechRecognitionListener {
    void onSpeechStatusChanged(MozillaSpeechService.SpeechState aState, Object aPayload);
}