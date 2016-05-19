package com.safewaychina.qiniuuplib.exception;

public class RespException extends QiNiuException {
    private int code;

    public int code() {
        return code;
    }

    public RespException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
