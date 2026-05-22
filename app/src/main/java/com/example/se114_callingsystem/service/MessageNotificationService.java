package com.example.se114_callingsystem.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.example.se114_callingsystem.chat.ChatDetailActivity;
import com.example.se114_callingsystem.model.Firebase;
import com.example.se114_callingsystem.model.Message;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class MessageNotificationService extends Service {
    private static final String TAG = "MessageNotificationService";
    private static final String CHANNEL_ID = "MessageChannel";
    private static final String CHANNEL_NAME = "Message Notifications";

    private long serviceStartTime;
    private ListenerRegistration firestoreListener;
    private final Map<String, ChildEventListener> dbListeners = new HashMap<>();
    private final Map<String, String> channelNames = new HashMap<>();
    private String currentUserUsername;

    @Override
    public void onCreate() {
        super.onCreate();
        serviceStartTime = System.currentTimeMillis();
        createNotificationChannel();
        listenToChannels();
        loadCurrentUserUsername();
    }

    private void loadCurrentUserUsername() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId != null) {
            FirebaseFirestore.getInstance().collection("users").document(currentUserId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUserUsername = documentSnapshot.getString("username");
                    }
                });
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up Firestore listener
        if (firestoreListener != null) {
            firestoreListener.remove();
        }
        // Clean up Realtime Database listeners
        for (Map.Entry<String, ChildEventListener> entry : dbListeners.entrySet()) {
            DatabaseReference ref = Firebase.getMessagesRefByRoom(entry.getKey());
            ref.removeEventListener(entry.getValue());
        }
        dbListeners.clear();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Thông báo khi có tin nhắn mới");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void listenToChannels() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        firestoreListener = db.collection("Channels")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to channels", error);
                        return;
                    }
                    if (value != null) {
                        Set<String> activeChannelIds = new HashSet<>();
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            String chatId = doc.getId();
                            String chatName = doc.getString("chatName");
                            if (chatId != null && chatName != null) {
                                channelNames.put(chatId, chatName);
                                activeChannelIds.add(chatId);
                                if (!dbListeners.containsKey(chatId)) {
                                    attachMessageListener(chatId, chatName);
                                }
                            }
                        }

                        // Remove database listeners for deleted channels
                        Iterator<Map.Entry<String, ChildEventListener>> iterator = dbListeners.entrySet().iterator();
                        while (iterator.hasNext()) {
                            Map.Entry<String, ChildEventListener> entry = iterator.next();
                            String chatId = entry.getKey();
                            if (!activeChannelIds.contains(chatId)) {
                                DatabaseReference ref = Firebase.getMessagesRefByRoom(chatId);
                                ref.removeEventListener(entry.getValue());
                                iterator.remove();
                            }
                        }
                    }
                });
    }

    private void attachMessageListener(String chatId, String chatName) {
        DatabaseReference ref = Firebase.getMessagesRefByRoom(chatId);
        // Only listen for new messages sent after service started
        Query newMessagesQuery = ref.orderByChild("timestamp").startAt(serviceStartTime);

        ChildEventListener listener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Message message = snapshot.getValue(Message.class);
                if (message == null) return;

                String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                        ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

                // Ignore if sent by the current user
                if (currentUserId != null && currentUserId.equals(message.getSenderId())) {
                    return;
                }

                // Ignore if the user is currently viewing this chat room
                if (chatId.equals(ChatDetailActivity.activeChatId)) {
                    return;
                }

                // Ignore if muted
                android.content.SharedPreferences prefs = getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
                if (prefs.getBoolean("mute_" + chatId, false)) {
                    return;
                }

                // Fetch sender name from Firestore and show notification
                fetchSenderNameAndShowNotification(chatId, chatName, message);
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        newMessagesQuery.addChildEventListener(listener);
        dbListeners.put(chatId, listener);
    }

    private void fetchSenderNameAndShowNotification(String chatId, String chatName, Message message) {
        String senderId = message.getSenderId();
        FirebaseFirestore.getInstance().collection("users").document(senderId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    String senderName = "Ai đó";
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("username");
                        if (name != null && !name.isEmpty()) {
                            senderName = name;
                        }
                    }
                    sendNotification(chatId, chatName, senderName, message);
                })
                .addOnFailureListener(e -> {
                    sendNotification(chatId, chatName, "Ai đó", message);
                });
    }

    private void sendNotification(String chatId, String chatName, String senderName, Message message) {
        String contentText;
        if ("image".equals(message.getType())) {
            contentText = "📷 Đã gửi một ảnh";
        } else if ("file".equals(message.getType())) {
            contentText = "📎 Đã gửi một tài liệu";
        } else {
            contentText = message.getContent();
        }

        String title = senderName + " (#" + chatName + ")";
        if (currentUserUsername != null && !currentUserUsername.isEmpty()) {
            String mentionTag = "@" + currentUserUsername.toLowerCase();
            if (contentText != null && contentText.toLowerCase().contains(mentionTag)) {
                title = "📌 Nhắc tới bạn: " + senderName + " (#" + chatName + ")";
            }
        }

        Intent intent = new Intent(this, ChatDetailActivity.class);
        intent.putExtra("CHAT_ID", chatId);
        intent.putExtra("CHAT_NAME", chatName);
        intent.putExtra("SERVER_COLOR", "#6C63FF");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                chatId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(chatId.hashCode(), builder.build());
        }
    }
}
