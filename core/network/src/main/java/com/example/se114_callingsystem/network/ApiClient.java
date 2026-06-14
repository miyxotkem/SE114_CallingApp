package com.example.se114_callingsystem.network;

import android.content.Context;
import android.os.Build;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static Context context;
    private static Retrofit retrofit = null;

    public static void init(Context ctx) {
        context = ctx.getApplicationContext();
    }

    public static class DynamicBaseUrlInterceptor implements Interceptor {
        private static volatile String host;
        private static volatile int port = -1;
        private static volatile String scheme;

        public static void setBaseUrl(String newUrl) {
            try {
                HttpUrl httpUrl = HttpUrl.parse(newUrl);
                if (httpUrl != null) {
                    scheme = httpUrl.scheme();
                    host = httpUrl.host();
                    port = httpUrl.port();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            if (host != null) {
                HttpUrl newUrl = request.url().newBuilder()
                        .scheme(scheme)
                        .host(host)
                        .port(port)
                        .build();
                request = request.newBuilder()
                        .url(newUrl)
                        .build();
            }
            return chain.proceed(request);
        }
    }

    private static boolean isEmulator() {
        String fingerprint = Build.FINGERPRINT;
        String model = Build.MODEL;
        String brand = Build.BRAND;
        String device = Build.DEVICE;
        String hardware = Build.HARDWARE;
        String product = Build.PRODUCT;
        return fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || model.contains("google_sdk")
                || model.contains("Emulator")
                || model.contains("Android SDK built for x86")
                || (brand.startsWith("generic") && device.startsWith("generic"))
                || "google_sdk".equals(product)
                || hardware.contains("goldfish")
                || hardware.contains("ranchu");
    }

    public static void saveBaseUrl(String newUrl) {
        if (context != null && newUrl != null) {
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("api_base_url", newUrl)
                    .apply();
        }
        DynamicBaseUrlInterceptor.setBaseUrl(newUrl);
    }

    public static String getBaseUrl() {
        String savedUrl = null;
        if (context != null) {
            savedUrl = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .getString("api_base_url", null);
        }
        if (savedUrl == null || savedUrl.trim().isEmpty()) {
            if (isEmulator()) {
                return "http://10.0.2.2:3000/";
            } else {
                return "http://192.168.1.30:3000/";
            }
        }
        return savedUrl;
    }

    public static Retrofit getClient() {
        if (retrofit == null) {
            String baseUrl = getBaseUrl();
            if (!baseUrl.endsWith("/")) {
                baseUrl += "/";
            }
            DynamicBaseUrlInterceptor.setBaseUrl(baseUrl);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new DynamicBaseUrlInterceptor())
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
