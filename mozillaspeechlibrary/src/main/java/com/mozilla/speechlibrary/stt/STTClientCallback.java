package com.mozilla.speechlibrary.stt;

import androidx.annotation.NonNull;

public interface STTClientCallback {
    void onSTTStart();
    void onSTTError(@NonNull String error);
    void onSTTFinished(@NonNull STTResult result);
}
