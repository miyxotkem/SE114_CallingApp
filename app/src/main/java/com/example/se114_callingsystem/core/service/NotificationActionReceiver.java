package com.example.se114_callingsystem.core.service;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class NotificationActionReceiver extends BroadcastReceiver {
    private static final String TAG = "NotificationActionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        if ("com.example.se114_callingsystem.ACTION_MARK_AS_READ".equals(action)) {
            String notificationId = intent.getStringExtra("NOTIFICATION_ID");
            String chatId = intent.getStringExtra("CHAT_ID");
            
            // Cancel the active notification from status bar
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null && chatId != null) {
                manager.cancel(chatId.hashCode());
            }

            // Update isRead = true on Firestore
            String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

            if (currentUserId != null && notificationId != null) {
                FirebaseFirestore.getInstance().collection("users")
                        .document(currentUserId)
                        .collection("notifications")
                        .document(notificationId)
                        .update("isRead", true)
                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Notification marked as read from broadcast"))
                        .addOnFailureListener(e -> Log.e(TAG, "Failed to mark notification as read from broadcast", e));
            }
        }
    }
}
