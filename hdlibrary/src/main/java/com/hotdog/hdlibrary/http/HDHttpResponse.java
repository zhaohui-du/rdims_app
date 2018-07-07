package com.hotdog.hdlibrary.http;

import android.support.annotation.NonNull;

import java.io.InputStream;

import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * http response body 封装,外部减少对 okhttp 框架的依赖
 * 可以继续抽象为一个接口,然后有不同框架的实现,等稳定之后再继续抽象
 */
public final class HDHttpResponse {

    public static HDHttpResponse create(Response response) {
        return new HDHttpResponse(response);
    }

    private final int statusCode;
    private final String contentType;
    private final long contentLength;
    private final InputStream inputStream;
    private final Response realResponse;

    private HDHttpResponse(Response response) {
        this.realResponse = response;
        this.statusCode = response.code();
        ResponseBody realBody = response.body();
        this.contentType = realBody.contentType() == null ? "unknown" : realBody.contentType().toString();
        this.contentLength = realBody.contentLength();
        this.inputStream = realBody.byteStream();
    }

    public int statusCode() {
        return statusCode;
    }

    public String header(@NonNull String key) {
        return realResponse.header(key);
    }

    public String contentType() {
        return contentType;
    }

    public long contentLength() {
        return contentLength;
    }

    public InputStream contentStream() {
        return inputStream;
    }

}
