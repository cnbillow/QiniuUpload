package com.safewaychina.qiniuuplib.common;

import com.safewaychina.qiniuuplib.exception.RespException;
import com.safewaychina.qiniuuplib.listener.UpProgressListener;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class UploadClient {

    private static final String TAG = "UploadClient";
    private OkHttpClient client;

    public UploadClient() {
        client = new OkHttpClient();
        client.setConnectTimeout(UpConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS);
        client.setReadTimeout(UpConfig.READ_TIMEOUT, TimeUnit.SECONDS);
        client.setWriteTimeout(UpConfig.WRITE_TIMEOUT, TimeUnit.SECONDS);
        client.setFollowRedirects(true);
    }
    public OkHttpClient getClient(){//提供一个对外的获取okHttpClient的方法
        return client;
    }


    //这个方法主要是用来获取token的
    public String post(String url, final Map<String, String> requestParams) throws IOException, RespException {
        FormEncodingBuilder builder = new FormEncodingBuilder();
        for (Map.Entry<String, String> entry : requestParams.entrySet()) {
            builder.add(entry.getKey(), entry.getValue());
        }
        Request request = new Request.Builder()
                .url(url)
                .post(builder.build())
                .build();

        Response response = client.newCall(request).execute();

        if (!response.isSuccessful()) {
            throw new RespException(response.code(), response.body().string());
        } else {
            return response.body().string();
        }
    }

    /**
     * 表单上传将会使用到这个方法
     * @param file
     * @param tokenKey
     * @param listener
     * @return
     * @throws IOException
     * @throws RespException
     */
    public String fromUpLoad(File file,String tokenKey, UpProgressListener listener) throws IOException, RespException {
        RequestBody requestBody = new MultipartBuilder()
                .type(MultipartBuilder.FORM)
                .addFormDataPart("file", file.getName(), RequestBody.create(null, file))
                .addFormDataPart("token", tokenKey)
                .build();

        if (listener != null) {
            requestBody = ProgressHelper.addProgressListener(requestBody, listener);
        }
        Request request = new Request.Builder()
                .addHeader("Content-Type", "multipart/form-data")
                .url("http://upload.qiniu.com/")
                .post(requestBody)
                .build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new RespException(response.code(), response.body().string());
        } else {
            return response.body().string();
        }
    }
}
