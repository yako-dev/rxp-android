package com.realexpayments.hpp;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.Map;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

/**
 * Helper class for server communication
 */
class ApiAdapter {

    public static final String RETROFIT_TAG = "HPPRetrofit";

    public static IHPPServerAPI getAdapter(String endpoint, final Map<String, String> headers) {

        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
        interceptor.level(HttpLoggingInterceptor.Level.BODY);

        httpClient.addInterceptor(interceptor);
        httpClient.addInterceptor(new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request.Builder builder = chain.request().newBuilder();
                for (String headerName : headers.keySet()) {
                    String headerValue = headers.get(headerName);
                    if( headerValue != null) {
                        builder.addHeader(headerName, headerValue);
                    }
                }
              Request request =  builder.build();
                return chain.proceed(request);
            }
        });

        Retrofit restAdapter = new Retrofit.Builder()
                .baseUrl(endpoint)
                .addConverterFactory(GsonConverterFactory.create(getGson()))
                .client(httpClient.build())
                .build();

        return restAdapter.create(IHPPServerAPI.class);
    }

    public static Gson getGson() {
        return new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
    }
}
