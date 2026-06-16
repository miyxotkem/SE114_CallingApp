package com.example.se114_callingsystem.core.service;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.RemoteInput;
import com.example.se114_callingsystem.core.model.Firebase;
import com.example.se114_callingsystem.core.model.Message;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class NotificationActionReceiver extends BroadcastReceiver {
    private static final String TAG = "NotificationActionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        String chatId = intent.getStringExtra("CHAT_ID");
        String notificationId = intent.getStringExtra("NOTIFICATION_ID");

        // Cancel the active notification from status bar
        if (chatId != null) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(chatId.hashCode());
            }
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

        if ("com.example.se114_callingsystem.ACTION_MARK_AS_READ".equals(action)) {
            return;
        }

        if ("com.example.se114_callingsystem.ACTION_LIKE".equals(action)) {
            if (currentUserId != null && chatId != null) {
                DatabaseReference newMsgRef = Firebase.getMessagesRefByRoom(chatId).push();
                Message message = new Message(currentUserId, chatId, "👍", System.currentTimeMillis());
                message.setMessageId(newMsgRef.getKey());
                newMsgRef.setValue(message)
                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Like message sent from notification"))
                        .addOnFailureListener(e -> Log.e(TAG, "Failed to send Like message", e));
            }
        } else if ("com.example.se114_callingsystem.ACTION_REPLY".equals(action)) {
            Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
            if (remoteInput != null) {
                CharSequence replyText = remoteInput.getCharSequence("key_text_reply");
                if (replyText != null && replyText.length() > 0 && currentUserId != null && chatId != null) {
                    DatabaseReference newMsgRef = Firebase.getMessagesRefByRoom(chatId).push();
                    Message message = new Message(currentUserId, chatId, replyText.toString(), System.currentTimeMillis());
                    message.setMessageId(newMsgRef.getKey());
                    newMsgRef.setValue(message)
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Reply message sent from notification"))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to send Reply message", e));
                }
            }
        } else if ("com.example.se114_callingsystem.ACTION_MUTE".equals(action)) {
            if (chatId != null) {
                android.content.SharedPreferences prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("mute_" + chatId, true).apply();
                Toast.makeText(context, "Đã tắt thông báo cho phòng chat này", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
