package com.example.se114_callingsystem;

import android.app.Application;
import com.cloudinary.android.MediaManager;
import com.example.se114_callingsystem.network.BackendService;
import com.example.se114_callingsystem.network.ApiClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class CallingApplication extends Application {

    @Inject
    FirebaseAuth firebaseAuth;

    @Inject
    BackendService backendService;

    @Override
    public void onCreate() {
        super.onCreate();

        // Khởi tạo ApiClient với Application Context
        ApiClient.init(this);
        // Force Dark Mode globally by default
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);

        // Khởi tạo Cloudinary MediaManager một lần duy nhất tại đây
        initCloudinary();

        // Khởi tạo và đồng bộ FCM Token
        initFcmToken();

        // Bật offline persistence cho Firebase
        initFirebaseOfflineSettings();
    }

    private void initFirebaseOfflineSettings() {
        // Bật offline persistence cho Firestore
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build();
        FirebaseFirestore.getInstance().setFirestoreSettings(settings);

        // Bật offline persistence cho Realtime Database
        try {
            com.google.firebase.database.FirebaseDatabase.getInstance("https://calling-app-5374e-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    .setPersistenceEnabled(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initCloudinary() {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", "dxoukp0yb");
        config.put("api_key", "359217744855482");
        try {
            MediaManager.init(this, new com.cloudinary.android.signed.SignatureProvider() {
                @Override
                public com.cloudinary.android.signed.Signature provideSignature(Map options) {
                    try {
                        if (firebaseAuth.getCurrentUser() == null) return null;
                        String idToken = com.google.android.gms.tasks.Tasks.await(
                                firebaseAuth.getCurrentUser().getIdToken(true)
                        ).getToken();
                        long timestamp = System.currentTimeMillis() / 1000L;
                        
                        Map<String, Object> body = new HashMap<>();
                        body.put("timestamp", timestamp);
                        retrofit2.Response<BackendService.CloudinarySignatureResponse> response = 
                                backendService.getCloudinarySignature("Bearer " + idToken, body).execute();
                        
                        if (response.isSuccessful() && response.body() != null) {
                            return new com.cloudinary.android.signed.Signature(
                                     response.body().signature, 
                                     response.body().api_key, 
                                     response.body().timestamp
                            );
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                @Override
                public String getName() {
                    return "GlobalSignatureProvider";
                }
            }, config);
        } catch (IllegalStateException e) {
            // Đã khởi tạo
        }
    }

    private void initFcmToken() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        android.util.Log.w("CallingApplication", "Fetching FCM registration token failed", task.getException());
                        return;
                    }
                    String token = task.getResult();
                    android.util.Log.d("CallingApplication", "FCM Token: " + token);

                    String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null 
                            ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                    if (currentUserId != null) {
                        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users")
                                .document(currentUserId)
                                .update("fcmToken", token)
                                .addOnSuccessListener(aVoid -> android.util.Log.d("CallingApplication", "FCM token updated successfully"))
                                .addOnFailureListener(e -> android.util.Log.e("CallingApplication", "Error updating FCM token", e));
                    }
                });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
