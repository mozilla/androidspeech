package com.mozilla.speechlibrary;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;

public class SpeechServiceSettings implements Serializable {

    private boolean mUseStoreSamples;
    private boolean mUseStoreTranscriptions;
    private String mLanguage;
    private String mProductTag;
    private boolean mUseDeepSpeech;
    private String mModelPath;

    public SpeechServiceSettings(@NonNull Builder builder) {
        mUseStoreSamples = builder.storeSamples;
        mUseStoreTranscriptions = builder.storeTranscriptions;
        mLanguage = builder.language;
        mProductTag = builder.productTag;
        mUseDeepSpeech = builder.useDeepSpeech;
        mModelPath = builder.modelPath;
    }

    public boolean useStoreSamples() {
        return mUseStoreSamples;
    }

    public boolean useStoreTranscriptions() {
        return mUseStoreTranscriptions;
    }

    @NonNull
    public String getLanguage() {
        return mLanguage;
    }

    @NonNull
    public String getProductTag() {
        return mProductTag;
    }

    public boolean useUseDeepSpeech() {
        return mUseDeepSpeech;
    }

    @Nullable
    public String getModelPath() {
        return mModelPath;
    }

    public static class Builder {

        private boolean storeSamples;
        private boolean storeTranscriptions;
        private String language;
        private String productTag;
        private boolean useDeepSpeech;
        private String modelPath;

        public Builder() {
            storeSamples = false;
            storeTranscriptions = false;
            language = "en-US";
            productTag = "moz-android-speech-lib";
            useDeepSpeech = false;
            modelPath = null;
        }

        public Builder withStoreSamples(boolean storeSamples) {
            this.storeSamples = storeSamples;
            return this;
        }

        public Builder withStoreTranscriptions(boolean storeTranscriptions) {
            this.storeTranscriptions = storeTranscriptions;
            return this;
        }

        public Builder withLanguage(@NonNull String language) {
            this.language = language;
            return this;
        }

        public Builder withUseDeepSpeech(boolean useDeepSpeech){
            this.useDeepSpeech = useDeepSpeech;
            return this;
        }

        public Builder withModelPath(@NonNull String modelPath){
            this.modelPath = modelPath;
            return this;
        }

        public Builder withProductTag(@NonNull String productTag){
            this.productTag = productTag;
            return this;
        }

        public SpeechServiceSettings build(){
            return new SpeechServiceSettings(this);
        }
    }
}
