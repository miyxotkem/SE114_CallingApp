package com.example.se114_callingsystem.features.call.data;

import com.example.se114_callingsystem.core.model.ServerMember;
import com.example.se114_callingsystem.network.BackendService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VoiceCallRepository {

    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;
    private final BackendService backendService;

    @Inject
    public VoiceCallRepository(FirebaseAuth mAuth, FirebaseFirestore db, BackendService backendService) {
        this.mAuth = mAuth;
        this.db = db;
        this.backendService = backendService;
    }

    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onFailure(Exception e);
    }

    public interface RealtimeCallback<T> {
        void onData(T data);
        void onError(Exception e);
    }

    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    public String getCurrentUserId() {
        return mAuth.getUid();
    }

    public void getFirebaseIdToken(RepositoryCallback<String> callback) {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            callback.onFailure(new Exception("User not authenticated"));
            return;
        }
        user.getIdToken(true).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                callback.onSuccess(task.getResult().getToken());
            } else {
                callback.onFailure(task.getException() != null ? task.getException() : new Exception("Failed to get ID token"));
            }
        });
    }

    public void fetchAgoraToken(String idToken, String channelName, int uid, RepositoryCallback<BackendService.AgoraTokenResponse> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("channelName", channelName);
        body.put("uid", uid);
        backendService.getAgoraToken("Bearer " + idToken, body).enqueue(new Callback<BackendService.AgoraTokenResponse>() {
            @Override
            public void onResponse(Call<BackendService.AgoraTokenResponse> call, Response<BackendService.AgoraTokenResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onFailure(new Exception("Failed to fetch Agora token, code: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<BackendService.AgoraTokenResponse> call, Throwable t) {
                callback.onFailure(new Exception(t));
            }
        });
    }

    public void updateUserActiveChannel(String userId, String channelName, RepositoryCallback<Void> callback) {
        db.collection("users").document(userId)
                .update("currentVoiceChannelName", channelName)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void clearUserActiveChannel(String userId, RepositoryCallback<Void> callback) {
        db.collection("users").document(userId)
                .update("currentVoiceChannelName", "")
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public ListenerRegistration listenToServerMembers(String serverId, RealtimeCallback<List<ServerMember>> callback) {
        if (serverId == null || serverId.isEmpty()) {
            return null;
        }
        return db.collection("servers").document(serverId).collection("members")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snapshots != null) {
                        List<ServerMember> members = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            ServerMember m = doc.toObject(ServerMember.class);
                            if (m != null) {
                                members.add(m);
                            }
                        }
                        callback.onData(members);
                    }
                });
    }
}
