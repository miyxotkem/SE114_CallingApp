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
        String senderId = data.get("senderId");
        if (senderId != null && !senderId.isEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(senderId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String avatarUrl = null;
                        if (documentSnapshot.exists()) {
                            avatarUrl = documentSnapshot.getString("profilePic");
                            if (avatarUrl == null || avatarUrl.isEmpty()) {
                                avatarUrl = documentSnapshot.getString("avatarUrl");
                            }
                        }
                        
                        final String finalAvatarUrl = avatarUrl;
                        new Thread(() -> {
                            android.graphics.Bitmap avatarBitmap = null;
                            if (finalAvatarUrl != null && !finalAvatarUrl.isEmpty()) {
                                try {
                                    avatarBitmap = com.bumptech.glide.Glide.with(MyFirebaseMessagingService.this)
                                            .asBitmap()
                                            .load(finalAvatarUrl)
                                            .circleCrop()
                                            .submit()
                                            .get();
                                } catch (Exception e) {
                                    Log.e(TAG, "Error loading sender avatar bitmap", e);
                                }
                            }
                            android.graphics.Bitmap finalBitmap = avatarBitmap;
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                sendNotification(data, finalBitmap);
                            });
                        }).start();
                    })
                    .addOnFailureListener(e -> {
                        sendNotification(data, null);
                    });
        } else {
            sendNotification(data, null);
        }
    }

    private void sendNotification(Map<String, String> data, android.graphics.Bitmap avatarBitmap) {
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

        Intent likeIntent = new Intent(this, NotificationActionReceiver.class);
        likeIntent.setAction("com.example.se114_callingsystem.ACTION_LIKE");
        likeIntent.putExtra("CHAT_ID", chatId);
        likeIntent.putExtra("CHAT_NAME", chatName);
        likeIntent.putExtra("NOTIFICATION_ID", notifId);
        PendingIntent likePendingIntent = PendingIntent.getBroadcast(
                this,
                chatId != null ? chatId.hashCode() + 10 : 10,
                likeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        androidx.core.app.RemoteInput remoteInput = new androidx.core.app.RemoteInput.Builder("key_text_reply")
                .setLabel("Trả lời...")
                .build();

        Intent replyIntent = new Intent(this, NotificationActionReceiver.class);
        replyIntent.setAction("com.example.se114_callingsystem.ACTION_REPLY");
        replyIntent.putExtra("CHAT_ID", chatId);
        replyIntent.putExtra("CHAT_NAME", chatName);
        replyIntent.putExtra("NOTIFICATION_ID", notifId);
        PendingIntent replyPendingIntent = PendingIntent.getBroadcast(
                this,
                chatId != null ? chatId.hashCode() + 20 : 20,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(
                0,
                "Trả lời",
                replyPendingIntent)
                .addRemoteInput(remoteInput)
                .build();

        Intent muteIntent = new Intent(this, NotificationActionReceiver.class);
        muteIntent.setAction("com.example.se114_callingsystem.ACTION_MUTE");
        muteIntent.putExtra("CHAT_ID", chatId);
        muteIntent.putExtra("CHAT_NAME", chatName);
        muteIntent.putExtra("NOTIFICATION_ID", notifId);
        PendingIntent mutePendingIntent = PendingIntent.getBroadcast(
                this,
                chatId != null ? chatId.hashCode() + 30 : 30,
                muteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        androidx.core.app.Person localUser = new androidx.core.app.Person.Builder()
                .setName("Tôi")
                .build();

        androidx.core.app.Person.Builder personBuilder = new androidx.core.app.Person.Builder()
                .setName(senderName);
        if (avatarBitmap != null) {
            personBuilder.setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(avatarBitmap));
        }
        androidx.core.app.Person sender = personBuilder.build();

        androidx.core.app.NotificationCompat.MessagingStyle messagingStyle = 
                new androidx.core.app.NotificationCompat.MessagingStyle(localUser)
                        .setConversationTitle(chatId.startsWith("dm_") ? null : chatName)
                        .addMessage(contentText, System.currentTimeMillis(), sender);

         NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(com.example.se114_callingsystem.R.drawable.ic_notification)
                .setStyle(messagingStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setGroup(groupKey)
                .setContentIntent(pendingIntent)
                .addAction(0, "Thích", likePendingIntent)
                .addAction(replyAction)
                .addAction(0, "Tắt thông báo", mutePendingIntent);

        if (avatarBitmap != null) {
            builder.setLargeIcon(avatarBitmap);
        }

        NotificationCompat.Builder summaryBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(com.example.se114_callingsystem.R.drawable.ic_notification)
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
