package com.safewaychina.qiniuuplib.common;
import com.safewaychina.qiniuuplib.listener.UpCompleteListener;
import com.safewaychina.qiniuuplib.listener.UpProgressListener;
import com.safewaychina.qiniuuplib.utils.AsyncRun;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UploadManager {
    private static UploadManager instance;
    private ExecutorService executor;
    private UploadClient upLoaderClient;

    enum UploadType {
        FORM, BLOCK
    }

    private UploadManager() {
        executor = Executors.newFixedThreadPool(UpConfig.CONCURRENCY);
        upLoaderClient = new UploadClient();
    }

    public static UploadManager getInstance() {
        if (instance == null) {
            synchronized (UploadManager.class) {
                if (instance == null) {
                    instance = new UploadManager();
                }
            }
        }
        return instance;
    }

    protected void upload(final File file, String apiKey, final UpCompleteListener completeListener, final UpProgressListener progressListener) {
        this.upload(file, apiKey, null, completeListener, progressListener);
    }

    protected void upload(final File file, String tokenUrl,Map<String,String>tokenParams, final UpCompleteListener completeListener, final UpProgressListener progressListener) {
        this.upload(file, tokenUrl,tokenParams, completeListener, progressListener);
    }

    public void formUpload(final File file, String apiKey, final UpCompleteListener completeListener, final UpProgressListener progressListener) {
        this.upload(UploadType.FORM, file,  apiKey, null, null, completeListener, progressListener);
    }

    public void formUpload(final File file, String tokenUrl,Map<String,String>tokenParams, final UpCompleteListener completeListener, final UpProgressListener progressListener) {
        this.upload(UploadType.FORM, file, null, tokenUrl,tokenParams, completeListener, progressListener);
    }

    public void blockUpload(final File file, String apiKey, final UpCompleteListener completeListener, final UpProgressListener progressListener) {
        this.upload(UploadType.BLOCK, file,  apiKey, null, null,completeListener, progressListener);
    }

    public void blockUpload(final File file,  String tokenUrl,Map<String,String>tokenParams, final UpCompleteListener completeListener, final UpProgressListener progressListener) {
        this.upload(UploadType.BLOCK, file,   null, tokenUrl,tokenParams, completeListener, progressListener);
    }

    protected void upload(UploadType type, final File file,  String apiKey, String tokenUrl,Map<String,String>tokenParams, final UpCompleteListener completeListener, final UpProgressListener progressListener) {
        if (file == null) {
            completeListener.onComplete(false, "文件不可以为空");
            return;
        } else if (apiKey == null && tokenUrl == null) {
            completeListener.onComplete(false, "APIkey和tokenUrl不可同时为null");
            return;
        }

        UpProgressListener uiProgressListener = new UpProgressListener() {
            @Override
            public void onRequestProgress(final long bytesWrite, final long contentLength) {
                AsyncRun.run(new Runnable() {
                    @Override
                    public void run() {
                        if (progressListener != null) {
                            progressListener.onRequestProgress(bytesWrite, contentLength);
                        }
                    }
                });
            }
        };

        UpCompleteListener uiCompleteListener = new UpCompleteListener() {
            @Override
            public void onComplete(final boolean isSuccess, final String result) {
                AsyncRun.run(new Runnable() {
                    @Override
                    public void run() {
                        completeListener.onComplete(isSuccess, result);
                    }
                });
            }
        };

        Runnable uploadRunnable = null;
        switch (type) {
            case FORM:
                //FormUploader(UploadClient upLoaderClient, File file, String uploadUrl, Map<String, Object> uploadParams, String apiKey, String tokenUrl, Map<String, String> tokenParams, UpCompleteListener uiCompleteListener, UpProgressListener uiProgressListener)
                //uploadRunnable = new FormUploader(upLoaderClient, file, uploadUrl,localParams, apiKey, tokenUrl, tokenParams, uiCompleteListener, uiProgressListener);
                uploadRunnable = new FormUploader(upLoaderClient, file, apiKey, tokenUrl, tokenParams, uiCompleteListener, uiProgressListener);
                break;
            case BLOCK:
                //uploadRunnable = new BlockUploader(upLoaderClient, file, uploadUrl, localParams, apiKey, tokenUrl, tokenParams, uiCompleteListener, uiProgressListener);
                uploadRunnable = new BlockUploader(upLoaderClient, file, apiKey, tokenUrl, tokenParams, uiCompleteListener, uiProgressListener);
                break;
        }
        executor.execute(uploadRunnable);
    }

    protected void upload(final File file, String apiKey, String tokenUrl,Map<String,String>tokenParams, final UpCompleteListener completeListener, final UpProgressListener progressListener) {
        if (file.length() < UpConfig.FILE_BOUND) {
            this.upload(UploadType.FORM, file, apiKey, tokenUrl, tokenParams, completeListener, progressListener);
        } else {
            this.upload(UploadType.BLOCK, file, apiKey, tokenUrl, tokenParams, completeListener, progressListener);
        }
    }
}
