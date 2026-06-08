package com.example.se114_callingsystem.core.di;

import android.content.Context;
import com.example.se114_callingsystem.network.ApiClient;
import com.example.se114_callingsystem.network.BackendService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;

public class AppDependencyProvider {
    private static Context context;
    private static BackendService backendService;
    private static FirebaseFirestore firestore;
    private static FirebaseDatabase realtimeDatabase;
    private static FirebaseAuth firebaseAuth;

    public static void init(Context appContent) {
        context = appContent.getApplicationContext();
    }

    public static synchronized BackendService getBackendService() {
        if (backendService == null) {
            backendService = ApiClient.getClient().create(BackendService.class);
        }
        return backendService;
    }

    public static synchronized FirebaseFirestore getFirestore() {
        if (firestore == null) {
            firestore = FirebaseFirestore.getInstance();
        }
        return firestore;
    }

    public static synchronized FirebaseDatabase getRealtimeDatabase() {
        if (realtimeDatabase == null) {
            realtimeDatabase = FirebaseDatabase.getInstance();
        }
        return realtimeDatabase;
    }

    public static synchronized FirebaseAuth getFirebaseAuth() {
        if (firebaseAuth == null) {
            firebaseAuth = FirebaseAuth.getInstance();
        }
        return firebaseAuth;
    }
}
