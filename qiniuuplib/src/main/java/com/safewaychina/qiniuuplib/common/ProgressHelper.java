package com.safewaychina.qiniuuplib.common;


import com.safewaychina.qiniuuplib.listener.UpProgressListener;
import com.squareup.okhttp.RequestBody;


public class ProgressHelper {
    public ProgressHelper() {
    }

    public static ProgressRequestBody addProgressListener(RequestBody requestBody, UpProgressListener progressRequestListener) {
        return new ProgressRequestBody(requestBody, progressRequestListener);
    }
}

