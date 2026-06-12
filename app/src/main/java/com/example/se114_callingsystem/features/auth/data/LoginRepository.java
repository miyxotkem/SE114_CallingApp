package com.example.se114_callingsystem.features.auth.data;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

public class LoginRepository {

    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;

    @Inject
    public LoginRepository(FirebaseAuth mAuth, FirebaseFirestore db) {
        this.mAuth = mAuth;
        this.db = db;
    }

    public interface LoginCallback {
        void onSuccess(FirebaseUser user);
        void onFailure(Exception exception);
    }

    public interface FirestoreCallback {
        void onSuccess();
        void onFailure(Exception exception);
    }

    public void loginWithEmail(String email, String password, LoginCallback callback) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            callback.onSuccess(user);
                        } else {
                            callback.onFailure(new Exception("FirebaseUser is null after successful login"));
                        }
                    } else {
                        callback.onFailure(task.getException());
                    }
                });
    }

    public void loginWithGoogle(AuthCredential credential, LoginCallback callback) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            callback.onSuccess(user);
                        } else {
                            callback.onFailure(new Exception("FirebaseUser is null after Google login"));
                        }
                    } else {
                        callback.onFailure(task.getException());
                    }
                });
    }

    public void checkAndSaveUserToFirestore(FirebaseUser user, FirestoreCallback callback) {
        if (user == null) {
            callback.onFailure(new Exception("User is null"));
            return;
        }

        String uid = user.getUid();
        db.collection("users").document(uid).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (!task.getResult().exists()) {
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("uid", uid);
                    userData.put("email", user.getEmail());
                    userData.put("username", user.getDisplayName() != null ? user.getDisplayName() : "User");
                    userData.put("avatar", user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");

                    db.collection("users").document(uid).set(userData)
                            .addOnSuccessListener(aVoid -> callback.onSuccess())
                            .addOnFailureListener(callback::onFailure);
                } else {
                    callback.onSuccess();
                }
            } else {
                callback.onFailure(task.getException());
            }
        });
    }
}
