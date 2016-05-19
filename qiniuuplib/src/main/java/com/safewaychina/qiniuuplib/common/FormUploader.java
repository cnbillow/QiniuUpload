package com.safewaychina.qiniuuplib.common;

import android.text.TextUtils;

import com.safewaychina.qiniuuplib.exception.RespException;
import com.safewaychina.qiniuuplib.listener.UpCompleteListener;
import com.safewaychina.qiniuuplib.listener.UpProgressListener;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class FormUploader implements Runnable {


    private UploadClient client;
    private File file;
    private String bucket;
    private UpProgressListener progressListener;
    private UpCompleteListener completeListener;
    private int retryTime;
    private Map<String, Object> params;
    private Map<String, String> tokenParams;
    private String apiKey;
    private String tokenUrl;


    public FormUploader(UploadClient upLoaderClient, File file, String apiKey, String tokenUrl, Map<String, String> tokenParams, UpCompleteListener uiCompleteListener, UpProgressListener uiProgressListener) {
        this.client = upLoaderClient;
        this.file = file;
        this.apiKey = apiKey;
        this.tokenUrl = tokenUrl;
        this.tokenParams = tokenParams;
        this.completeListener = uiCompleteListener;
        this.progressListener = uiProgressListener;
    }


    @Override
    public void run() {
        try{
            if(apiKey == null && tokenUrl == null){
                completeListener.onComplete(false,"获取token的链接没有值  或者 token没有本地生成");
            }else if(!TextUtils.isEmpty(apiKey)){//已经有了token
                upload(file, apiKey, progressListener);
            }else if(!TextUtils.isEmpty(tokenUrl)){
                String token = client.post(tokenUrl, tokenParams);//后台获取token
                if(!TextUtils.isEmpty(token)){
                    upload(file, token,progressListener);
                }else{
                    completeListener.onComplete(false,"后台获取token的失败");
                }

            }
        }catch (Exception e){
            if (++retryTime > UpConfig.RETRY_TIME || (e instanceof RespException && ((RespException) e).code() / 100 != 5)) {
                completeListener.onComplete(false,e!=null?e.getMessage():"");
            } else {
                this.run();
            }
        }

        String save_path = (String) params.get(Params.SAVE_KEY);
        String path = (String) params.remove(Params.PATH);
        if (save_path == null && path != null) {
            params.put(Params.SAVE_KEY, path);
        }
    }

    /**
     * 最后还是调用了 UploadClient里的方法来发送请求上传的
     * @param file
     * @param apiKey
     * @param progressListener
     * @throws JSONException
     * @throws IOException
     * @throws RespException
     */
    private void upload(File file, String apiKey,UpProgressListener progressListener) throws JSONException, IOException, RespException {
        String result = client.fromUpLoad(file, apiKey,progressListener);
        if(!TextUtils.isEmpty(result) ){
            JSONObject jsonObject = new JSONObject(result);
            //正确上传后的返回值大概是： {"hash": "Fh8xVqod2MQ1mocfI4S4KpRL6D98",  "key": "gogopher.jpg"}
            if(jsonObject.has("hash") && jsonObject.has("key")){
                completeListener.onComplete(true,result);
            }else{
                completeListener.onComplete(false,result);
            }
        }
    }
}
