package com.realexpayments.hpp;

import java.util.HashMap;


import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.http.Field;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Declaration of the server api
 */

interface IHPPServerAPI {

    String PATH_ARG = "path";
    String PATH = "/{" + PATH_ARG + "}";

    @FormUrlEncoded
    @POST(PATH)
    Call<ResponseBody> getHPPRequest(
            @Path(value = PATH_ARG, encoded = true) String path,
            @FieldMap HashMap<String, String> args
    );

    @FormUrlEncoded
    @POST(PATH)
    Call<ResponseBody> getConsumerRequest(
            @Path(value = PATH_ARG, encoded = true) String path,
            @Field("hppResponse") String hppResponse
    );

    @FormUrlEncoded
    @POST(PATH)
    Call<ResponseBody> getHPP(
            @Path(value = PATH_ARG, encoded = true) String path,
            @FieldMap HashMap<String, String> args
    );
}
