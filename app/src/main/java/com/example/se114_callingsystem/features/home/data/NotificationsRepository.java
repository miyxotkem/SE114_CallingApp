package com.example.se114_callingsystem.features.home.data;

import com.example.se114_callingsystem.core.model.NotificationItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

public class NotificationsRepository {

    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;

    @Inject
    public NotificationsRepository(FirebaseAuth mAuth, FirebaseFirestore db) {
        this.mAuth = mAuth;
        this.db = db;
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

    public ListenerRegistration listenToNotifications(String userId, RealtimeCallback<List<NotificationItem>> callback) {
        return db.collection("users")
                .document(userId)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }

                    List<NotificationItem> list = new ArrayList<>();
                    if (value != null && !value.isEmpty()) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            NotificationItem item = doc.toObject(NotificationItem.class);
                            if (item != null) {
                                Boolean isReadVal = doc.getBoolean("isRead");
                                if (isReadVal != null) {
                                    item.setRead(isReadVal);
                                }
                                list.add(item);
                            }
                        }
                    }
                    callback.onData(list);
                });
    }

    public void markAsRead(String userId, String notificationId, RepositoryCallback<Void> callback) {
        db.collection("users")
                .document(userId)
                .collection("notifications")
                .document(notificationId)
                .update("isRead", true)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
}
