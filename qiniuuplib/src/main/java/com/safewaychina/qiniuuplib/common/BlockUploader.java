package com.safewaychina.qiniuuplib.common;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.safewaychina.qiniuuplib.exception.QiNiuException;
import com.safewaychina.qiniuuplib.exception.RespException;
import com.safewaychina.qiniuuplib.listener.UpCompleteListener;
import com.safewaychina.qiniuuplib.listener.UpProgressListener;
import com.safewaychina.qiniuuplib.utils.UpYunUtils;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BlockUploader implements Runnable {
    private static final String TAG = "BlockUploader";

    private String url;

    private UpProgressListener progressListener;
    private UpCompleteListener completeListener;
    private File file;
    private UploadClient client;

    private int totalBlockNum;

    private RandomAccessFile randomAccessFile = null;

    private PostData postData;
    private Map<String, Object> params;
    private String tokenKey;
    private String tokenUrl;

    private Map<String, String> tokenParams;
    private int retryTime;
    private int currentBlock;//当前上传的块
    private int currentChunk;//当前上传的片
    private List<Long> chunkSizeList;//当前的块的所有的片的文件大小的集合
    private List<Long> blockSizeList;//所有的块的文件大小的集合
    private List<String> ctxList;//每个数据块最后一个数据片上传后得到的<ctx>的列表
    String lastHost = "http://upload.qiniu.com";
    boolean isFinished = false;

    public BlockUploader(UploadClient upLoaderClient, File file, String tokenKey, String tokenUrl, Map<String, String> tokenParams, UpCompleteListener uiCompleteListener, UpProgressListener uiProgressListener) {
        this.client = upLoaderClient;
        this.file = file;
        this.progressListener = uiProgressListener;
        this.completeListener = uiCompleteListener;
        this.tokenKey = tokenKey;
        this.tokenUrl = tokenUrl;
        this.tokenParams = tokenParams;
        ctxList = new ArrayList<>();
        chunkSizeList = new ArrayList<>();
        blockSizeList = new ArrayList<>();
    }

    @Override
    public void run() {
        //判断key参数，并获取
        if (tokenKey == null && tokenUrl == null) {
            completeListener.onComplete(false, "tokenKey和tokenUrl不能同时为null");
        } else if (!TextUtils.isEmpty(tokenKey)) {//已经有了tokenKey

        } else if (!TextUtils.isEmpty(tokenUrl)) {
            String token = null;
            try {
                token = client.post(tokenUrl, tokenParams);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (RespException e) {
                e.printStackTrace();
            }
            tokenKey = token;
        }
        //初始化上传操作，第一步操作，mkfile，任何的上传文件都要先上传第一块的第一片数据，就是在initRequest里完成的
        initRequest();
    }

    private void initRequest() {
        try {
            this.randomAccessFile = new RandomAccessFile(this.file, "r");
            this.totalBlockNum = UpYunUtils.getBlockNum(this.file, UpConfig.BLOCK_SIZE);//计算总的块数
            getAllBlockSizeList();//计算每一个块的大小，大小放在一个集合,集合叫blockSizeList
            currentBlock = 0;
            while (currentBlock < totalBlockNum) {
                currentChunk = 0;
                getAllChunkSize(currentBlock);//先计算第tempBlock个块有多少个片，每一个片的大小存在chunkSizeList里
                Request request = new Request.Builder()
                        .url(lastHost + "/mkblk/" + blockSizeList.get(0))
                        .addHeader("Authorization", "UpToken " + tokenKey)
                        .addHeader("Content-Length", chunkSizeList.get(0) + "")
                        .addHeader("Content-Type", "application/octet- stream")
                        .post(RequestBody.create(MediaType.parse("application/octet-stream"), readBlockByChunkIndex(currentBlock, currentChunk)))
                        .build();
                Response response = client.getClient().newCall(request).execute();
                String str = response.body().string();
                JSONObject initialResult = new JSONObject(str);
                if (!initialResult.has("error")) {//没错
                    currentChunk++;//这里已经把第0个片的数据上传了
                    lastHost = initialResult.getString("host");
                    String ctx = initialResult.getString("ctx");
                    if (currentBlock + 1 == totalBlockNum && currentChunk + 1 == chunkSizeList.size()) {//一个块一个片就存完了所有数据，跳过上传其他片的操作，直接merge
                        ctxList.add(ctx);
                        megreRequest();
                        isFinished = true;
                    } else {
                        //上传分块，总之有多个分片的数据，分片上传
                        blockUpload(ctx);
                    }
                } else {
                    completeListener.onComplete(false, initialResult.has("error") ? initialResult.getString("error") : "");
                }
                currentBlock++;
            }
            if (!isFinished) {
                megreRequest();
            }
        } catch (IOException e) {
            if (++retryTime > UpConfig.RETRY_TIME) {
                completeListener.onComplete(false, e.toString());
            } else {
                initRequest();
            }
        } catch (JSONException e) {
            throw new RuntimeException("json 解析出错", e);
        } catch (Exception e) {
            throw new RuntimeException("error", e);
        }
    }

    //某一个块里有多少个片，就是chunkSizeList的长度，里面存的是每一个片的长度
    public void getAllBlockSizeList() throws QiNiuException {
        long fileLen = file.length();
        Log.e("getAllBlockSizeList", "fileLen=" + fileLen);
        int num = (int) (file.length() % UpConfig.BLOCK_SIZE == 0 ? file.length() / UpConfig.BLOCK_SIZE : (file.length() / UpConfig.BLOCK_SIZE) + 1);
        long temp;
        for (int i = 0; i < num; i++) {
            if (i == 0) {
                blockSizeList.add(fileLen >= UpConfig.BLOCK_SIZE ? UpConfig.BLOCK_SIZE : fileLen);
            } else {
                temp = fileLen - i * UpConfig.BLOCK_SIZE;
                blockSizeList.add(temp >= UpConfig.BLOCK_SIZE ? UpConfig.BLOCK_SIZE : temp);
            }
        }
    }

    //某一个块里有多少个片，就是chunkSizeList的长度，里面存的是每一个片的长度
    public void getAllChunkSize(int index) throws QiNiuException {
        chunkSizeList.clear();
        long dataLen = blockSizeList.get(index);//某一个块有的字节数组
        int num = (int) (dataLen % UpConfig.CHUNK_SIZE == 0 ? dataLen / UpConfig.CHUNK_SIZE : (dataLen / UpConfig.CHUNK_SIZE) + 1);
        long temp;
        for (int i = 0; i < num; i++) {
            if (i == 0) {
                chunkSizeList.add(dataLen >= UpConfig.CHUNK_SIZE ? UpConfig.CHUNK_SIZE : dataLen);
            } else {
                temp = dataLen - i * UpConfig.CHUNK_SIZE;
                chunkSizeList.add(temp >= UpConfig.CHUNK_SIZE ? UpConfig.CHUNK_SIZE : temp);
            }
        }
    }


    //上传第currentBlock个块的数据，上传完毕才会退出循环
    private void blockUpload(String ctx) {
        while (true) {
            if (postData == null) {
                postData = new PostData();
            }
            try {
                postData.data = readBlockByChunkIndex(currentBlock, currentChunk);
            } catch (QiNiuException e) {
                completeListener.onComplete(false, e.toString());
            }

            String url = lastHost + "/bput/" + ctx + "/" + (currentChunk * UpConfig.CHUNK_SIZE);
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "UpToken " + tokenKey)
                    .addHeader("Content-Type", "application/octet-stream")
                    .addHeader("Content-Length", postData.data.length + "")
                    .post(RequestBody.create(null, postData.data)).build();
            try {
                Response response = client.getClient().newCall(request).execute();
                String str = response.body().string();
                JSONObject initialResult = new JSONObject(str);
                if (!initialResult.has("error")) {//没错
                    lastHost = initialResult.getString("host");
                    String tempCtx = initialResult.getString("ctx");
                    if (currentChunk + 1 == chunkSizeList.size()) {
                        ctxList.add(tempCtx);
                        megreRequest();
                        break;
                    } else {
                        if (progressListener != null) {
                            progressListener.onRequestProgress(currentChunk, currentBlock*UpConfig.BLOCK_SIZE + currentChunk * UpConfig.CHUNK_SIZE);
                        }
                        currentChunk++;
                        // 上传分块
                        blockUpload(tempCtx);
                    }
                } else {
                    completeListener.onComplete(false, initialResult.has("error") ? initialResult.getString("error") : "");
                    break;
                }

            } catch (IOException | JSONException e) {
                if (++retryTime > UpConfig.RETRY_TIME || (e instanceof RespException && ((RespException) e).code() / 100 != 5)) {
                    completeListener.onComplete(false, e.toString());
                    break;
                }
            } finally {
                postData = null;
            }
        }
    }

    private void megreRequest() {
        try {
            StringBuffer sb = new StringBuffer();
            for (String strCtx : ctxList) {
                sb.append(strCtx).append(",");
            }
            String ctxs = sb.toString().substring(0, sb.length() - 1);
            Request mkFileReq = new Request.Builder()
                    .url(lastHost + "/mkfile/" + file.length())
                    .addHeader("Authorization", "UpToken " + tokenKey)
                    .addHeader("Content-Length", ctxs.getBytes().length + "")
                    .addHeader("Content-Type", "text/plain")
                    .post(RequestBody.create(MediaType.parse("text/plain"), ctxs)).build();

            Response mKResponse = client.getClient().newCall(mkFileReq).execute();

            String str = mKResponse.body().string();
            JSONObject result = new JSONObject(str);

            //{ "hash": "<ContentHash  string>", "key":  "<Key          string>"}
            //默认返回如下的字符串，是一个错误的json字符串
            //{"scope":"com-safeway-store","fileName":"IMG_20160416_133428_HDR.jpg","deadline":3600,"returnBody":"{\"name\":null,\"size\":305493,\"w\":592,\"h\":453,\"hash\":Fu0rZk5ku2BptDLQdPNoqWkzJcFA}\"}
            if (mKResponse.isSuccessful()) {//上传成功
                completeListener.onComplete(true, result.toString());
            } else {
                completeListener.onComplete(false, "文件上传失败!!!!!");
                throw new Exception(result == null ? "文件上传失败!!!!!" : (result.has("error") ? result.getString("error") : str));
            }

            //progressListener.onRequestProgress(blockIndex.length, blockIndex.length);
            completeListener.onComplete(true, str);
        } catch (JSONException | IOException | RespException e) {
            if (++retryTime > UpConfig.RETRY_TIME || (e instanceof RespException && ((RespException) e).code() / 100 != 5)) {
                completeListener.onComplete(false, e.toString());
            } else {
                megreRequest();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 从文件中读取块
     * <p/>
     * index begin at 0
     *
     * @return
     */
    private byte[] readBlockByChunkIndex(int blockIndex, int chunkIndex) throws QiNiuException {
        try {
            if (totalBlockNum == 1 && chunkSizeList.size() == 1) {//数据太小，一个块就放满了
                byte[] data = new byte[(int) file.length()];
                randomAccessFile.read(data);
                return data;
            } else {
                byte[] block = new byte[chunkSizeList.get(chunkIndex).intValue()];
                int offset = blockIndex * UpConfig.BLOCK_SIZE + chunkIndex * UpConfig.CHUNK_SIZE;
                randomAccessFile.seek(offset);
                randomAccessFile.read(block);//实际读取的字节数
                return block;
            }
        } catch (Exception e) {
            throw new QiNiuException(e.getMessage());
        }
        // read last block, adjust byte size
        /*if (readedSize < UpConfig.CHUNK_SIZE) {
            byte[] notFullBlock = new byte[readedSize];
            System.arraycopy(block, 0, notFullBlock, 0, readedSize);
            return notFullBlock;
        }*/

    }


    /**
     * 从文件中读取块
     * <p/>
     * index begin at 0
     *
     * @param index
     * @return
     */
    private byte[] readBlockByIndex(int index) throws QiNiuException {
        if (index > this.totalBlockNum) {
            Log.e("Block index error", "the index is bigger than totalBlockNum.");
            throw new QiNiuException("readBlockByIndex: the index is bigger than totalBlockNum.");
        }
        byte[] block = new byte[UpConfig.BLOCK_SIZE];
        int readedSize = 0;
        try {
            int offset = index == 0 ? index : (index - 1) * UpConfig.BLOCK_SIZE;
            randomAccessFile.seek(offset);
            readedSize = randomAccessFile.read(block, 0, UpConfig.BLOCK_SIZE);
        } catch (IOException e) {
            throw new QiNiuException(e.getMessage());
        }

        // read last block, adjust byte size
        if (readedSize < UpConfig.BLOCK_SIZE) {
            byte[] notFullBlock = new byte[readedSize];
            System.arraycopy(block, 0, notFullBlock, 0, readedSize);
            return notFullBlock;
        }
        return block;
    }

    /**
     * 获取没有上传的分块下标
     *
     * @param array
     * @return
     * @throws JSONException
     */
    private int[] getBlockIndex(JSONArray array) throws JSONException {
        int size = 0;
        for (int i = 0; i < array.length(); i++) {
            if (array.getInt(i) == 0) {
                size++;
            }
        }
        // 获取未上传的块下标
        int[] blockIndex = new int[size];
        int index = 0;
        for (int i = 0; i < array.length(); i++) {
            if (array.getInt(i) == 0) {
                blockIndex[index] = i;
                index++;
            }
        }
        return blockIndex;
    }

    private String getParamsString(Map<String, Object> params) {

        Object[] keys = params.keySet().toArray();
        Arrays.sort(keys);
        StringBuffer tmp = new StringBuffer("");
        for (Object key : keys) {
            tmp.append(key).append(params.get(key));
        }
        return tmp.toString();
    }
}
