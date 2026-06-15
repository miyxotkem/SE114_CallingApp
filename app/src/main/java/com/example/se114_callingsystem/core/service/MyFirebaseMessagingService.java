package com.example.se114_callingsystem.core.service;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.example.se114_callingsystem.MainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "MyFirebaseMessagingService";
    private static final String CHANNEL_ID = "MessageChannel";
    private static final int CALL_NOTIFICATION_ID = 10001;

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed FCM token: " + token);
        sendTokenToServer(token);
    }

    private void sendTokenToServer(String token) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId != null) {
            FirebaseFirestore.getInstance().collection("users")
                    .document(currentUserId)
                    .update("fcmToken", token)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token saved successfully to Firestore"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to save FCM token to Firestore", e));
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains data payload
        Map<String, String> data = remoteMessage.getData();
        if (data.size() > 0) {
            String type = data.get("type"); // "dm", "mention", "call_ringing", "call_ended"
            if ("call_ringing".equals(type)) {
                handleIncomingCallPayload(data);
            } else if ("call_ended".equals(type)) {
                handleCallEndedPayload();
            } else {
                handleMessagePayload(data);
            }
        }
    }

    private void handleIncomingCallPayload(Map<String, String> data) {
        String callerId = data.get("callerId");
        String callerName = data.get("callerName");
        String channelName = data.get("channelName");
        String callType = data.get("callType");

        // Trigger local call receiver in MainActivity if active
        Intent broadcastIntent = new Intent("com.example.se114_callingsystem.INCOMING_CALL");
        broadcastIntent.putExtra("CALLER_ID", callerId);
        broadcastIntent.putExtra("CALLER_NAME", callerName);
        broadcastIntent.putExtra("CALL_CHANNEL_NAME", channelName);
        broadcastIntent.putExtra("CALL_TYPE", callType);
        broadcastIntent.setPackage(getPackageName());
        sendBroadcast(broadcastIntent);

        // Start local ringtone and full screen activity via background service
        Intent serviceIntent = new Intent(this, MessageNotificationService.class);
        serviceIntent.setAction("com.example.se114_callingsystem.ACTION_INCOMING_CALL");
        serviceIntent.putExtra("CALLER_ID", callerId);
        serviceIntent.putExtra("CALLER_NAME", callerName);
        serviceIntent.putExtra("CALL_CHANNEL_NAME", channelName);
        serviceIntent.putExtra("CALL_TYPE", callType);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void handleCallEndedPayload() {
        stopRingtoneAndCancelCallNotif();
    }

    private void stopRingtoneAndCancelCallNotif() {
        Intent serviceIntent = new Intent(this, MessageNotificationService.class);
        serviceIntent.setAction("com.example.se114_callingsystem.ACTION_DECLINE_CALL");
        startService(serviceIntent);

        // Dismiss local dialogues
        Intent dismissBroadcast = new Intent("com.example.se114_callingsystem.DISMISS_CALL_DIALOG");
        dismissBroadcast.setPackage(getPackageName());
        sendBroadcast(dismissBroadcast);
    }

    private void handleMessagePayload(Map<String, String> data) {
        String title = data.get("title");
        String contentText = data.get("content");
        String chatId = data.get("chatId");
        String chatName = data.get("chatName");
        String senderName = data.get("senderName");

        // Push new notification
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("CHAT_ID", chatId);
        intent.putExtra("CHAT_NAME", chatName);
        intent.putExtra("SERVER_COLOR", "#5865F2");
        intent.putExtra("SERVER_ID", (String) null);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                chatId != null ? chatId.hashCode() : 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String groupKey = "com.example.se114_callingsystem.CHAT_GROUP";
        String notifId = data.get("notificationId") != null ? data.get("notificationId") : String.valueOf(System.currentTimeMillis());

        Intent readIntent = new Intent(this, NotificationActionReceiver.class);
        readIntent.setAction("com.example.se114_callingsystem.ACTION_MARK_AS_READ");
        readIntent.putExtra("NOTIFICATION_ID", notifId);
        readIntent.putExtra("CHAT_ID", chatId);
        
        PendingIntent readPendingIntent = PendingIntent.getBroadcast(
                this,
                notifId.hashCode(),
                readIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setGroup(groupKey)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.checkbox_on_background, "Đã đọc", readPendingIntent);

        NotificationCompat.Builder summaryBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("Tin nhắn mới")
                .setContentText("Bạn có tin nhắn mới chưa đọc")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && chatId != null) {
            manager.notify(chatId.hashCode(), builder.build());
            manager.notify(999, summaryBuilder.build());
        }
    }
}
