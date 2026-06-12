package com.example.se114_callingsystem;

import android.app.Application;
import com.cloudinary.android.MediaManager;
import com.example.se114_callingsystem.network.BackendService;
import com.example.se114_callingsystem.core.di.AppDependencyProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import java.util.HashMap;
import java.util.Map;
import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class CallingApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Khởi tạo AppDependencyProvider với Application Context
        AppDependencyProvider.init(this);

        // Khởi tạo Cloudinary MediaManager một lần duy nhất tại đây
        initCloudinary();

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
                        FirebaseAuth auth = AppDependencyProvider.getFirebaseAuth();
                        if (auth.getCurrentUser() == null) return null;
                        String idToken = com.google.android.gms.tasks.Tasks.await(
                                auth.getCurrentUser().getIdToken(true)
                        ).getToken();
                        long timestamp = System.currentTimeMillis() / 1000L;
                        
                        BackendService service = AppDependencyProvider.getBackendService();
                        Map<String, Object> body = new HashMap<>();
                        body.put("timestamp", timestamp);
                        retrofit2.Response<BackendService.CloudinarySignatureResponse> response = 
                                service.getCloudinarySignature("Bearer " + idToken, body).execute();
                        
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
}
