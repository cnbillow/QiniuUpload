package com.safewaychina.qiniuuplib.listener;

public interface UpProgressListener {
    void onRequestProgress(long bytesWrite, long contentLength);
}
