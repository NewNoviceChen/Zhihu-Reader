package com.github.newnovicechen.zhihureader.utils;

import com.github.newnovicechen.zhihureader.exception.HttpException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

//通用http发送
public class SendHttpUtil {
    private static OkHttpClient getClient() {
        OkHttpClient client = new OkHttpClient()
                .newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS).build();
        return client;
    }

    private static OkHttpClient getClientWithProxy(Proxy proxy) {
        OkHttpClient client = new OkHttpClient()
                .newBuilder()
                .proxy(proxy)
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS).build();
        return client;
    }

    private static Request getRequest(HttpUrl url) {
        return new Request.Builder()
                .url(url)
                .method("GET", null)
                .addHeader("Content-Type", "application/json")
                .build();
    }

    private static Request getRequest(HttpUrl url, Map<String, String> headerParams) {
        return new Request.Builder()
                .url(url)
                .method("GET", null)
                .addHeader("Content-Type", "application/json")
                .headers(getHeaders(headerParams))
                .build();
    }

    private static Request postRequest(HttpUrl url, String jsonBody) {
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, jsonBody);
        return new Request.Builder()
                .url(url)
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build();
    }

    private static Request postRequest(HttpUrl url, String jsonBody, Map<String, String> headerParams) {
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, jsonBody);
        return new Request.Builder()
                .url(url)
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .headers(getHeaders(headerParams))
                .build();
    }

    private static Headers getHeaders(Map<String, String> headerParams) {
        Headers.Builder headersbuilder = new Headers.Builder();
        if (Objects.nonNull(headerParams)) {
            for (Map.Entry<String, String> entry : headerParams.entrySet()) {
                headersbuilder.add(entry.getKey(), entry.getValue());
            }
        }
        return headersbuilder.build();
    }

    public static <T> T sendHttpPost(HttpUrl url, String jsonBody, Map<String, String> headerParams, Class<T> clazz) {
        OkHttpClient client = getClient();
        Request request = postRequest(url, jsonBody, headerParams);
        Response response = null;
        try {
            response = client.newCall(request).execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return checkResponse(response, clazz);
    }

    public static <T> T sendHttpPost(HttpUrl url, String jsonBody, Map<String, String> headerParams, Proxy proxy, Class<T> clazz) {
        try {
            OkHttpClient client = getClientWithProxy(proxy);
            Request request = postRequest(url, jsonBody, headerParams);
            Response response = client.newCall(request).execute();
            return checkResponse(response, clazz);
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpException(e.getMessage());
        }
    }

    public static JsonObject sendHttpPost(HttpUrl url, String jsonBody, Map<String, String> headerParams, Proxy proxy) {
        try {
            OkHttpClient client = getClientWithProxy(proxy);
            Request request = postRequest(url, jsonBody, headerParams);
            Response response = client.newCall(request).execute();
            return checkResponse(response, JsonObject.class);
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpException(e.getMessage());
        }
    }

    public static JsonObject sendHttpPost(HttpUrl url, String jsonBody, Map<String, String> headerParams) {
        try {
            OkHttpClient client = getClient();
            Request request = postRequest(url, jsonBody, headerParams);
            Response response = client.newCall(request).execute();
            return checkResponse(response, JsonObject.class);
        } catch (Exception e) {
            throw new HttpException(e.getMessage());
        }
    }

    public static <T> T sendHttpPost(HttpUrl url, String jsonBody, Class<T> clazz) {
        try {
            OkHttpClient client = getClient();
            Request request = postRequest(url, jsonBody);
            Response response = client.newCall(request).execute();
            return checkResponse(response, clazz);
        } catch (Exception e) {
            throw new HttpException(e.getMessage());
        }
    }

    public static JsonObject sendHttpPost(HttpUrl url, String jsonBody) {
        try {
            OkHttpClient client = getClient();
            Request request = postRequest(url, jsonBody);
            Response response = client.newCall(request).execute();
            return checkResponse(response, JsonObject.class);
        } catch (Exception e) {
            throw new HttpException(e.getMessage());
        }
    }

    public static byte[] sendHttpPostReturnByte(HttpUrl url, String jsonBody) {
        try {
            OkHttpClient client = getClient();
            Request request = postRequest(url, jsonBody);
            Response response = client.newCall(request).execute();
            return response.body().bytes();
        } catch (Exception e) {
            throw new HttpException(e.getMessage());
        }
    }

    public static JsonObject sendHttpGet(HttpUrl url, Map<String, String> headerParams) {
        try {
            OkHttpClient client = getClient();
            Request request = getRequest(url, headerParams);
            Response response = client.newCall(request).execute();
            return checkResponse(response, JsonObject.class);
        } catch (Exception e) {
            throw new HttpException(e.getMessage());
        }
    }

    public static <T> T sendHttpGet(HttpUrl url, Map<String, String> headerParams, Class<T> clazz) {
        try {
            OkHttpClient client = getClient();
            Request request = getRequest(url, headerParams);
            Response response = client.newCall(request).execute();
            return checkResponse(response, clazz);
        } catch (Exception e) {
            throw new HttpException(e.getMessage());
        }
    }

    public static <T> T sendHttpGet(HttpUrl url, Class<T> clazz) {
        try {
            OkHttpClient client = getClient();
            Request request = getRequest(url);
            Response response = client.newCall(request).execute();
            return checkResponse(response, clazz);
        } catch (Exception e) {
            throw new HttpException(e.getMessage());
        }
    }

    public static JsonObject sendHttpGet(HttpUrl url) {
        try {
            OkHttpClient client = getClient();
            Request request = getRequest(url);
            Response response = client.newCall(request).execute();
            return checkResponse(response, JsonObject.class);
        } catch (Exception e) {
            throw new HttpException(e.getMessage());
        }
    }

    public static <T> T checkResponse(Response response, Class<T> clazz) throws HttpException {
        if (!response.isSuccessful()) {
            throw new HttpException("远程调用失败");
        }
        String s;
        try {
            s = response.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        JsonObject jsonObject = JsonParser.parseString(s).getAsJsonObject();
        if (jsonObject.has("code")) {
            if (jsonObject.get("code").getAsInt() != 200) {
                throw new HttpException(jsonObject.get("errorStackTrace").getAsString());
            }
        }
        return new Gson().fromJson(s, clazz);
    }
}
