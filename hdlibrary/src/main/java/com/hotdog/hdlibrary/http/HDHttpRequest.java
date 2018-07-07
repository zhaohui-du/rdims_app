package com.hotdog.hdlibrary.http;

import android.content.Context;
import android.os.Looper;
import android.support.annotation.IntDef;
import android.support.annotation.WorkerThread;

import com.hotdog.hdlibrary.core.HDException;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


/**
 * HttpRequest相关，包含各类型参数设置，执行，取消，响应事件等。包括get和post方法
 */
public class HDHttpRequest<Result>{
    private static final String TAG = "HttpRequest";

    private static final String USER_AGENT  = "MC-Android-Client";

    public static final int GET  = 0;
    public static final int POST = GET + 1;

    @IntDef({GET, POST})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Method {}

    //请求URL
    private String url;
    //request method
    private @Method int method = GET;
    //请求参数
    private HDHttpRequestParams requestParams;
    //Response 处理器
    private AbstractHttpResponseHandler<Result> responseHandler;
    //请求构造器,外部不可见
    private Request.Builder requestBuilder;
    //context上下文,现在主要用来返回结果toast,暂时没有其他用到的地方
    private Context context;
    //用来取消请求的call函数
    private Call call;

    public HDHttpRequest() {
        requestBuilder = new Request.Builder();
    }

    /**
     * 获取 http 状态码，如果请求还没有返回则返回 0
     * @return http 状态码
     */
    public int getHttpStatusCode() {
        return responseHandler != null ? responseHandler.getHttpStatusCode() : 0;
    }

    public HDHttpRequest setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getUrl() {
        return this.url;
    }

    public HDHttpRequest setMethod(@Method int method) {
        this.method = method;
        return this;
    }

    public @Method int getMethod() {
        return method;
    }

    public HDHttpRequest setRequestParams(HDHttpRequestParams params) {
        requestParams = params;
        return this;
    }

    public HDHttpRequestParams getRequestParams() {
        return requestParams;
    }

    public HDHttpRequest setContext(Context context) {
        this.context = context;
        return this;
    }

    public Context getContext() {
        return context;
    }

    /**
     * 设置Header,会覆盖掉已有的值
     */
    public HDHttpRequest setHeader(String key, String value) {
        requestBuilder.header(key, value);
        return this;
    }

    /**
     * 添加header,不会覆盖已有的值
     * @param key   key
     * @param value value
     */
    public HDHttpRequest addHeader(String key, String value) {
        requestBuilder.addHeader(key, value);
        return this;
    }

    /**
     * 批量设置Header,会覆盖掉已有的值
     */
    public HDHttpRequest setHeaders(Map<String, String> header) {
        for (Map.Entry<String, String> entry : header.entrySet()) {
            this.setHeader(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * 批量添加Header,不会覆盖掉已有的值
     */
    public HDHttpRequest addHeaders(Map<String, String> header) {
        for (Map.Entry<String, String> entry : header.entrySet()) {
            this.addHeader(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * 供子类重写,在调用该方法后,所有的请求头,请求参数都会组装起来,除非外部添加拦截器做修改,否则会直接执行
     */
    protected void beforeSendRequest() {
        //override
    }

    /**
     * 取消当前的 http 请求，该方法会触发 onCancel 回调。
     */
    public void cancel() {
        if (call == null || call.isCanceled() || call.isExecuted()) {
            return;
        }
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                call.cancel();
                if (responseHandler != null) {
                    responseHandler.fireOnCancel();
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            new Thread(r).start();
        } else {
            r.run();
        }
    }

    /**
     * 异步执行 request 所有的回调函数都会在主线程执行
     */
    public void enqueue(AbstractHttpResponseHandler<Result> handler) {
        //检查结果解析器  将当前请求对象传递到结果处理器内部
        responseHandler = handler;
        if (responseHandler == null){
            throw new NullPointerException("you must set a handler to handle response");
        } else {
            responseHandler.setRequest(this);
        }
        //子类做一些业务处理
        this.beforeSendRequest();
        //开始发送请求
        responseHandler.fireOnStart();
        call = this.makeCall();
        if (call != null) {
            call.enqueue(responseHandler);
        }
    }

    /**
     *
     * @return 同步执行请求
     * @throws IOException
     */
    @WorkerThread
    public Result execute(IParser<Result> IParser) throws IOException {
        //子类做一些业务处理
        this.beforeSendRequest();
        //开始发送请求
        call = this.makeCall();
        Result result = null;
        if (call != null)
        {
	        Response response = call.execute();
	        if (response.isSuccessful()) {
		        result = IParser.parse(HDHttpResponse.create(response));
	        } else {
				throw new HDException(response.code(), "http status code = " + response.code());
	        }
        }
        return result;
    }

    /**
     * @return 构造请求 {@link Call}
     */
    private Call makeCall() {
        //设置UA
        requestBuilder.addHeader("User-Agent", USER_AGENT);
        //newCall
        switch (method) {
        case GET:
            if (requestParams != null && !requestParams.isEmpty())
            {
                url = String.format("%s%s", url, requestParams.getQuery());
            }
            return HDHttpClient.getHttpClient().newCall(requestBuilder.url(url).get().build());
        case POST:
            RequestBody body = null;
            if (requestParams != null && !requestParams.isEmpty())
            {
                body = requestParams.getBody();
            }
            return HDHttpClient.getHttpClient().newCall(requestBuilder.url(url).post(body).build());
        default:
            return null;
        }
    }


    /**
     *  /**
     *下载文件
     * @param IParser
     * @param isRetry  是否启动从本地加载的拦截器，默认不启用，只有304状态才会从本地加载
     * @return 同步执行请求(文件下载专用)
     * @throws IOException
     */
    @WorkerThread
    public Result executeFile(IParser<Result> IParser, boolean isRetry) throws IOException {
        //子类做一些业务处理
        this.beforeSendRequest();
        //开始发送请求
        call = this.makeFileCall(isRetry);
        Result result = null;
        if (call != null)
        {
            Response response = call.clone().execute();
            if (response.isSuccessful()) {
                result = IParser.parse(HDHttpResponse.create(response));
            }else if(response.code() == 304){
                return executeFile(IParser, true);
            } else {
                throw new HDException(response.code(), "http status code = " + response.code());
            }
        }
        return result;
    }

    /**
     * @return 构造文件下载请求 {@link Call}
     */
    private Call makeFileCall(boolean isRetry) {
        //设置UA
        requestBuilder.addHeader("User-Agent", USER_AGENT);
        //newCall
        switch (method) {
            case GET:
                if (requestParams != null && !requestParams.isEmpty())
                {
                    url = String.format("%s%s", url, requestParams.getQuery());
                }
                return HDHttpClient.getFileHttpClient(isRetry).newCall(requestBuilder.url(url).get().build());
            case POST:
                RequestBody body = null;
                if (requestParams != null && !requestParams.isEmpty())
                {
                    body = requestParams.getBody();
                }
                return HDHttpClient.getFileHttpClient(isRetry).newCall(requestBuilder.url(url).post(body).build());
            default:
                return null;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Request url : ").append(url);
        builder.append("\nMethod : ").append(method == GET ? "GET" : "POST");
        builder.append("\nParams : ").append(requestParams != null ? requestParams.toString() : "null");
        return builder.toString();
    }

}
