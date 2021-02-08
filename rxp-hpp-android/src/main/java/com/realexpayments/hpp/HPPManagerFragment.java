package com.realexpayments.hpp;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.fragment.app.Fragment;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;


import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.realexpayments.hpp.Constants.HTML_MIME_TYPE;
import static com.realexpayments.hpp.Constants.HTTP_SCHEME_ENDING;
import static com.realexpayments.hpp.Constants.JS_WEBVIEW_OBJECT_NAME;
import static com.realexpayments.hpp.Constants.SLASH;
import static com.realexpayments.hpp.Constants.UTF_8;
import static com.realexpayments.hpp.HPPResponse.HPP_POST_RESPONSE;
import static com.realexpayments.hpp.HPPResponse.HPP_VERSION;

/**
 * Payment form fragment.
 * <p>
 * Insert the HppManager fragment into your activity as follows;
 * Fragment hppManagerFragment = hppManager.newInstance();
 * getFragmentManager() .beginTransaction().add(R.id.container,hppManagerFrament) .commit();
 **/

public class HPPManagerFragment extends Fragment {

    private HPPManagerListener mListener;
    private View root;
    private HPPManager hppManager;
    private boolean isResultReceived = false;

    public HPPManagerFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            hppManager = HPPManager.createFromBundle(getArguments());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.hppmanager_fragment, container, false);
        return root;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (HPPManagerListener) activity;

            if (getArguments() != null) {
                hppManager = HPPManager.createFromBundle(getArguments());

                HashMap<String, String> parameters = hppManager.getMap();
                IHPPServerAPI adapter = ApiAdapter.getAdapter(getHostPath(hppManager.getHppRequestProducerURL()), getRequestHeaders());

              Call<ResponseBody> call =  adapter
                        .getHPPRequest(
                                getRelativePathEncoded(hppManager.getHppRequestProducerURL()),
                                parameters
                        );

                call.enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        System.out.println("ON RESPONSE");
                        System.out.println(response.body());
                        handleResponse(call, response);
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        System.out.println("ON FAILURE");
                        t.printStackTrace();
                       handleFailure(call, t);
                    }
                });

            } else {

                mListener.hppManagerFailedWithError(new HPPError("Invalid arguments", null));
            }

        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement HPPManagerListener");
        }
    }

    private Map<String, String> getRequestHeaders() {
        HashMap<String, String> headersMap = new HashMap<>();
        HashMap<String, String> additionalHeaders = hppManager.getAdditionalHeaders();

        if (additionalHeaders != null && !additionalHeaders.isEmpty()) {
            for (String headerName : additionalHeaders.keySet()) {
                String headerValue = additionalHeaders.get(headerName);

                if (!TextUtils.isEmpty(headerName) && !TextUtils.isEmpty(headerValue)) {
                    headersMap.put(headerName, headerValue);
                }
            }
        }

        return headersMap;
    }

    private String getHostPath(String urlString) {
        return urlString.substring(0, urlString.lastIndexOf(getRelativePath(urlString)) - 1);
    }

    private String getRelativePath(String urlString) {
        Uri uri = Uri.parse(urlString);
        String path = uri.getPath();
        return (path.startsWith(SLASH)) ? path.substring(1) : path;
    }

    private String getRelativePathEncoded(String urlString) {
        Uri uri = Uri.parse(urlString);
        String encodedPath = uri.getEncodedPath();
        return (encodedPath.startsWith(SLASH)) ? encodedPath.substring(1) : encodedPath;
    }

    @Override
    public void onDestroy() {
        if (!isResultReceived) {
            mListener.hppManagerCancelled();
            isResultReceived = true;
        }
        mListener = null;

        super.onDestroy();
    }

    public void handleResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
        if(response.isSuccessful()) {
            final WebView webView = root.findViewById(R.id.hpp_web_view);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.addJavascriptInterface(this, JS_WEBVIEW_OBJECT_NAME);
            WebView.setWebContentsDebuggingEnabled(true);

            webView.setOnKeyListener(new View.OnKeyListener() {

                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {

                        if (!isResultReceived) {
                            mListener.hppManagerCancelled();
                            isResultReceived = true;
                        }

                        return false;
                    }
                    return false;
                }
            });

            webView.setWebChromeClient(new WebChromeClient() {

                @Override
                public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                    if (consoleMessage.message().startsWith(HPPManager.RESULT_MESSAGE)) {
                        String msg = consoleMessage.message().substring(HPPManager.RESULT_MESSAGE.length());
                        callbackHandler(msg, hppManager.getHppURL());
                        return true;
                    }

                    return super.onConsoleMessage(consoleMessage);

                }
            });

            webView.setWebViewClient(new WebViewClient() {

                                         Handler handler = new Handler();
                                         String url;

                                         @TargetApi(Build.VERSION_CODES.KITKAT)
                                         @Override
                                         public void onLoadResource(final WebView view, String url) {
                                             this.url = url;
                                             if (url.endsWith("api/auth")) {
                                                 checkResult(view);
                                             }

                                             super.onLoadResource(view, url);
                                         }

                                         private void checkResult(final WebView view) {
                                             handler.postDelayed(new Runnable() {
                                                 @Override
                                                 public void run() {
                                                     view.evaluateJavascript("javascript:console.log('" + HPPManager.RESULT_MESSAGE + "'+document.getElementById('result-message').innerHTML);", new ValueCallback<String>() {
                                                         @Override
                                                         public void onReceiveValue(String value) {
                                                             if (!isResultReceived) {
                                                                 checkResult(view);
                                                             }
                                                         }
                                                     });

                                                 }
                                             }, 100);
                                         }

                                         @SuppressLint("NewApi")
                                         @Override
                                         public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                                             super.onReceivedHttpError(view, request, errorResponse);

                                             if (this.url.equals(hppManager.getHppURL())) {

                                                 isResultReceived = true;
                                                 mListener.hppManagerFailedWithError(new HPPError(errorResponse.getReasonPhrase(), hppManager.getHppURL()));
                                             }

                                         }

                                         @SuppressLint("NewApi")
                                         @Override
                                         public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                                             super.onReceivedError(view, request, error);

                                             isResultReceived = true;
                                             mListener.hppManagerFailedWithError(new HPPError(error.getDescription().toString(), hppManager.getHppURL()));

                                         }

                                         @Override
                                         public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                                             super.onReceivedSslError(view, handler, error);
                                             isResultReceived = true;
                                             mListener.hppManagerFailedWithError(new HPPError(error.toString(), hppManager.getHppURL()));

                                         }
                                     }
            );

            String resp = null;
            try {
                resp = new String(response.body().bytes());
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println("-------------------> SUCCESS");
            System.out.println(resp);
            Type mapType = new TypeToken<Map<String, String>>() {
            }.getType();
            Map<String, String> consumerResponseParams = new Gson().fromJson(resp, mapType);
            postHPPData(webView, getHPPPostData(consumerResponseParams));
        } else {
            System.out.println("-------------------> failure");
            System.out.println(response.message());
            System.out.println(response.raw().toString());
            System.out.println(response.code());
            mListener.hppManagerFailedWithError(new HPPError(String.valueOf(response.code()), new Throwable(String.valueOf(response.code())), call.request().url().encodedPath()));

        }
    }

    private HashMap<String, String> getHPPPostData(Map<String, String> params) {
        HashMap<String, String> map = new HashMap<>();

        // default to HPP Version 2
        map.put(HPP_VERSION, "2");

        // determine the target origin to receive the response
        Uri uri = Uri.parse(hppManager.getHppRequestProducerURL());
        String hppPostResponse = uri.getScheme() + HTTP_SCHEME_ENDING + uri.getHost();
        map.put(HPP_POST_RESPONSE, hppPostResponse);

        for (String key : params.keySet()) {
            String paramValue = params.get(key);

            if (!TextUtils.isEmpty(paramValue)) {
                if (HPPManager.isEncoded()) {
                    byte[] decodedValue = Base64.decode(paramValue, Base64.DEFAULT);
                    String decodedString = new String(decodedValue);
                    map.put(key, decodedString);
                } else {
                    map.put(key, paramValue);
                }

            }
        }

        return map;
    }

    private void postHPPData(final WebView webView, HashMap<String, String> postData) {
    Call<ResponseBody> call =  ApiAdapter.getAdapter(getHostPath(hppManager.getHppURL()), getRequestHeaders())
                .getHPP(getRelativePathEncoded(hppManager.getHppURL()), postData);

    call.enqueue(                        new Callback<ResponseBody> (){
        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
            if(response.isSuccessful()) {
                String htmlString = null;
                try {
                    htmlString = new String(response.body().bytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }

                loadWebView(webView, hppManager.getHppURL(), htmlString);
            }
        }

        @Override
        public void  onFailure(Call<ResponseBody> call, Throwable error) {
            mListener.hppManagerFailedWithError(new HPPError(error.getMessage(), error, call.request().url().encodedPath()));
        }
    });
    }

    private void loadWebView(final WebView webView, final String url, final String htmlString) {
        webView.post(new Runnable() {
            @Override
            public void run() {
                webView.clearCache(true);
                webView.loadDataWithBaseURL(url, htmlString, HTML_MIME_TYPE, UTF_8, null);
            }
        });
    }

    @JavascriptInterface
    public void callbackHandler(String data, String url) {
        if (!isResultReceived && data.length() > 0) {
            isResultReceived = true;

         Call<ResponseBody> call =  ApiAdapter.getAdapter(getHostPath(hppManager.getHppResponseConsumerURL()), getRequestHeaders())
                    .getConsumerRequest(
                            getRelativePathEncoded(hppManager.getHppResponseConsumerURL()), data);
         call.enqueue(            new Callback<ResponseBody>() {
             @Override
             public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                 if(response.isSuccessful()) {
                     String msg = null;
                     try {
                         msg = new String(response.body().bytes());
                     } catch (IOException e) {
                         e.printStackTrace();
                     }

                     mListener.hppManagerCompletedWithResult(msg);
                 } else {
                     mListener.hppManagerFailedWithError(new HPPError("Payment Declined", new Exception("Payment Declined"), hppManager.getHppResponseConsumerURL()));
                 }
             }

             @Override
             public void onFailure(Call<ResponseBody> call, Throwable error) {
                 mListener.hppManagerFailedWithError(new HPPError(error.getMessage(), error, call.request().url().encodedPath()));
             }
         });

        }
    }

    public void handleFailure(Call<ResponseBody> call, Throwable t) {
        mListener.hppManagerFailedWithError(new HPPError(t.getMessage(), t, call.request().url().encodedPath()));
    }
}
