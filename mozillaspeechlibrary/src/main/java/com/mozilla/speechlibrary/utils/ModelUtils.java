package com.mozilla.speechlibrary.utils;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.stream.Stream;

public class ModelUtils {

    private static String MODEL_VERSION = "v0.9.1";
    private static String BASE_MODEL_URL = "https://github.com/lissyx/DeepSpeech/releases/download";
    private static final String MODELS_FOLDER = "models";

    @Nullable
    public static String languageForUri(@NonNull String uri) {
        try {
            File file = new File(uri);
            String name = file.getName();
            int index = name.indexOf(".");
            return name.substring(0, index);

        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    public static String latestVersion() {
        return MODEL_VERSION;
    }

    public static boolean isModelUri(@NonNull String aUri) {
        return isModelUri(aUri, latestVersion());
    }

    public static boolean isModelUri(@NonNull String aUri, @NonNull String version) {
        return aUri.startsWith(modelDownloadUrlBasePath(version));
    }

    @NonNull
    public static String modelDownloadUrl(@NonNull String aLang) {
        return modelDownloadUrl(aLang, latestVersion());
    }

    @NonNull
    public static String modelDownloadUrl(@NonNull String aLang, @NonNull String version) {
        return modelDownloadUrlBasePath(version) +
                aLang + ".zip";
    }

    private static String modelDownloadUrlBasePath() {
        return modelDownloadUrlBasePath(latestVersion());
    }

    private static String modelDownloadUrlBasePath(@NonNull String version) {
        return BASE_MODEL_URL + File.separator +
                version + File.separator;
    }

    @NonNull
    public static String modelDownloadOutputPath(@NonNull Context context,
                                                 @NonNull String aLang,
                                                 @StorageUtils.StorageType int storageType) {
        return modelDownloadOutputPath(context, aLang, storageType, latestVersion());
    }

    @NonNull
    public static String modelDownloadOutputPath(@NonNull Context context,
                                                 @NonNull String aLang,
                                                 @StorageUtils.StorageType int storageType,
                                                 @NonNull String version) {
        File outputFolder;
        if (storageType == StorageUtils.INTERNAL_STORAGE) {
            outputFolder =  new File(context.getExternalFilesDir(null), Environment.DIRECTORY_DOWNLOADS);

        } else {
            outputFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        }
        outputFolder =  new File(outputFolder, version);
        return new File(outputFolder, aLang + ".zip").getAbsolutePath();
    }

    @Nullable
    public static String modelPath(@NonNull Context context,
                                   @NonNull String language) {
        return modelPath(context, language, latestVersion());
    }

    @Nullable
    public static String modelPath(@NonNull Context context,
                                   @NonNull String language,
                                   @NonNull String version) {
        String modelRoot = modelRoot(context, version);
        if (modelRoot == null) {
            return null;
        }
        return modelRoot + "/" + language + "/";
    }

    @Nullable
    public static String modelRoot(@NonNull Context context) {
        return modelRoot(context, latestVersion());
    }

    @Nullable
    public static String modelRoot(@NonNull Context context, @NonNull String version) {
        File outputFolder = context.getExternalFilesDir(MODELS_FOLDER);
        if (outputFolder != null) {
            outputFolder = new File(outputFolder, version);
            if (!outputFolder.exists()) {
                if (outputFolder.mkdirs()) {
                    return outputFolder.getAbsolutePath();

                } else {
                    return null;
                }

            } else {
                return outputFolder.getAbsolutePath();
            }

        } else {
            return null;
        }
    }

    public static HashMap<String, String> installedModels(@NonNull Context context, @NonNull String version) {
        HashMap<String, String> models = new HashMap<>();
        File outputFolder = context.getExternalFilesDir(MODELS_FOLDER);
        if (outputFolder != null) {
            outputFolder = new File(outputFolder, version);
            File[] files = outputFolder.listFiles(File::isDirectory);
            Stream.of(files).forEach(file -> {
                models.put(
                        file.getName(),
                        file.getAbsolutePath());
            });
        }

        return models;
    }

    public static boolean isReady(@Nullable String modelPath) {
        if (modelPath == null) {
            return false;
        }
        return (new File(getTFLiteFolder(modelPath)).exists()
                && new File(getScorerFolder(modelPath)).exists()
                && new File(getInfoJsonFolder(modelPath)).exists());
    }

    @NonNull
    public static String getTFLiteFolder(@NonNull String modelRoot) {
        return modelRoot + "/output_graph.tflite";
    }

    @NonNull
    public static String getScorerFolder(@NonNull String modelRoot) {
        return modelRoot + "/scorer";
    }

    @NonNull
    public static String getInfoJsonFolder(@NonNull String modelRoot) {
        return modelRoot + "/info.json";
    }
}
