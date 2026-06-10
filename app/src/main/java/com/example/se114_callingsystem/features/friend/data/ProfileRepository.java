package com.example.se114_callingsystem.features.friend.data;

import android.net.Uri;
import android.util.Log;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.example.se114_callingsystem.core.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Map;
import javax.inject.Inject;

public class ProfileRepository {

    private static final String TAG = "ProfileRepository";
    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;

    @Inject
    public ProfileRepository(FirebaseAuth mAuth, FirebaseFirestore db) {
        this.mAuth = mAuth;
        this.db = db;
    }

    public interface ProfileCallback<T> {
        void onSuccess(T result);
        void onFailure(Exception exception);
    }

    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    public void loadUserProfile(String userId, ProfileCallback<User> callback) {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            callback.onSuccess(user);
                        } else {
                            callback.onFailure(new Exception("Failed to parse User object"));
                        }
                    } else {
                        callback.onFailure(new Exception("User profile does not exist"));
                    }
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void updateUserProfile(String userId, Map<String, Object> updates, ProfileCallback<Void> callback) {
        db.collection("users").document(userId).update(updates)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void uploadImage(Uri uri, String publicId, ProfileCallback<String> callback) {
        if (uri == null) {
            callback.onSuccess(null);
            return;
        }

        MediaManager.get().upload(uri)
                .option("resource_type", "auto")
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {}

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String secureUrl = (String) resultData.get("secure_url");
                        callback.onSuccess(secureUrl);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Log.e(TAG, "Cloudinary upload error: " + error.getDescription());
                        callback.onFailure(new Exception(error.getDescription()));
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
    }

    public void signOut() {
        mAuth.signOut();
    }
}
