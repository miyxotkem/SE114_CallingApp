package com.example.se114_callingsystem.network;

import java.util.Map;
import retrofit2.Call;
import retrofit2.http.POST;
import retrofit2.http.Body;
import retrofit2.http.Header;

public interface BackendService {

    class AgoraTokenResponse {
        public String token;
    }

    @POST("api/agora/token")
    Call<AgoraTokenResponse> getAgoraToken(
            @Header("Authorization") String authHeader,
            @Body Map<String, Object> body
    );

    class CloudinarySignatureResponse {
        public String signature;
        public long timestamp;
        public String api_key;
        public String cloud_name;
    }

    @POST("api/cloudinary/signature")
    Call<CloudinarySignatureResponse> getCloudinarySignature(
            @Header("Authorization") String authHeader,
            @Body Map<String, Object> body
    );
}
