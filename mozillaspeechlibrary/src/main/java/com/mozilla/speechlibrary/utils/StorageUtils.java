package com.mozilla.speechlibrary.utils;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class StorageUtils {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = { INTERNAL_STORAGE, EXTERNAL_STORAGE})
    public @interface StorageType {}
    public static final int INTERNAL_STORAGE = 0;
    public static final int EXTERNAL_STORAGE = 1;
}
