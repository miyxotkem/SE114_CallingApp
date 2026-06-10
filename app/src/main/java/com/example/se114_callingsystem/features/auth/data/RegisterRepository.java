package com.example.se114_callingsystem.features.auth.data;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

public class RegisterRepository {

    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;

    @Inject
    public RegisterRepository(FirebaseAuth mAuth, FirebaseFirestore db) {
        this.mAuth = mAuth;
        this.db = db;
    }

    public interface RegisterCallback {
        void onSuccess(FirebaseUser user);
        void onFailure(Exception exception);
    }

    public void registerUser(String email, String password, String username, RegisterCallback callback) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserToFirestore(user, username, callback);
                        } else {
                            callback.onFailure(new Exception("FirebaseUser is null after successful registration"));
                        }
                    } else {
                        callback.onFailure(task.getException());
                    }
                });
    }

    private void saveUserToFirestore(FirebaseUser user, String username, RegisterCallback callback) {
        String uid = user.getUid();
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", uid);
        userData.put("email", user.getEmail());
        userData.put("username", username);
        userData.put("avatar", "");

        db.collection("users").document(uid).set(userData)
                .addOnSuccessListener(aVoid -> callback.onSuccess(user))
                .addOnFailureListener(callback::onFailure);
    }
}
