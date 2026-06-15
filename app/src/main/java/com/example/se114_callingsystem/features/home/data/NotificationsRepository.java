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
                .limit(50)
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

    public void deleteNotification(String userId, String notificationId, RepositoryCallback<Void> callback) {
        db.collection("users")
                .document(userId)
                .collection("notifications")
                .document(notificationId)
                .delete()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void restoreNotification(String userId, NotificationItem item, RepositoryCallback<Void> callback) {
        if (item == null || item.getNotificationId() == null) {
            callback.onFailure(new Exception("Invalid notification item"));
            return;
        }
        db.collection("users")
                .document(userId)
                .collection("notifications")
                .document(item.getNotificationId())
                .set(item)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void clearAllNotifications(String userId, List<String> notificationIds, RepositoryCallback<Void> callback) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            callback.onSuccess(null);
            return;
        }
        com.google.firebase.firestore.WriteBatch batch = db.batch();
        for (String id : notificationIds) {
            batch.delete(db.collection("users").document(userId).collection("notifications").document(id));
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void autoClearOldNotifications(String userId, RepositoryCallback<Integer> callback) {
        long currentTime = System.currentTimeMillis();
        long readCutOff = currentTime - (7L * 24 * 60 * 60 * 1000); // 7 days
        long unreadCutOff = currentTime - (30L * 24 * 60 * 60 * 1000); // 30 days

        db.collection("users")
                .document(userId)
                .collection("notifications")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        callback.onSuccess(0);
                        return;
                    }

                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    int count = 0;

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Boolean isRead = doc.getBoolean("isRead");
                        Long timestamp = doc.getLong("timestamp");

                        if (timestamp != null) {
                            if (isRead != null && isRead && timestamp < readCutOff) {
                                batch.delete(doc.getReference());
                                count++;
                            } else if ((isRead == null || !isRead) && timestamp < unreadCutOff) {
                                batch.delete(doc.getReference());
                                count++;
                            }
                        }
                    }

                    final int finalCount = count;
                    if (count > 0) {
                        batch.commit()
                                .addOnSuccessListener(aVoid -> callback.onSuccess(finalCount))
                                .addOnFailureListener(callback::onFailure);
                    } else {
                        callback.onSuccess(0);
                    }
                })
                .addOnFailureListener(callback::onFailure);
    }
}
