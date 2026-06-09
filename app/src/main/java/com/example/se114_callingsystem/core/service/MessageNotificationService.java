package com.example.se114_callingsystem.core.service;

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
import com.example.se114_callingsystem.features.chat.ChatFragment;
import com.example.se114_callingsystem.MainActivity;
import com.example.se114_callingsystem.core.model.Firebase;
import com.example.se114_callingsystem.core.model.Message;
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
    
    private static final String FOREGROUND_CHANNEL_ID = "MessageServiceChannel";
    private static final String FOREGROUND_CHANNEL_NAME = "Message Service";
    private static final int FOREGROUND_NOTIF_ID = 9999;

    private long serviceStartTime;
    private ListenerRegistration firestoreListener;
    private com.google.firebase.database.ValueEventListener friendsListener;
    private final Map<String, ChildEventListener> dbListeners = new HashMap<>();
    private final Map<String, String> channelNames = new HashMap<>();
    private final Map<String, ListenerRegistration> friendProfileListeners = new HashMap<>();
    private String currentUserUsername;

    @Override
    public void onCreate() {
        super.onCreate();
        serviceStartTime = System.currentTimeMillis();
        createNotificationChannel();
        createForegroundChannel();
        startForegroundService();
        listenToChannels();
        listenToDMs();
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

        // Clean up Realtime Database friends listener
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId != null && friendsListener != null) {
            Firebase.getDatabase().getReference("friends").child(currentUserId).removeEventListener(friendsListener);
        }

        // Clean up friend profile listener registrations
        for (ListenerRegistration reg : friendProfileListeners.values()) {
            if (reg != null) {
                reg.remove();
            }
        }
        friendProfileListeners.clear();
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

    private void createForegroundChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    FOREGROUND_CHANNEL_ID,
                    FOREGROUND_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("Duy trì kết nối nhận tin nhắn");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundService() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Calling App is running")
                .setContentText("Listening for new messages...")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(pendingIntent);

        startForeground(FOREGROUND_NOTIF_ID, builder.build());
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
                            if (!chatId.startsWith("dm_") && !activeChannelIds.contains(chatId)) {
                                DatabaseReference ref = Firebase.getMessagesRefByRoom(chatId);
                                ref.removeEventListener(entry.getValue());
                                iterator.remove();
                            }
                        }
                    }
                });
    }

    private void listenToDMs() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId == null) return;

        friendsListener = Firebase.getDatabase().getReference("friends").child(currentUserId)
                .addValueEventListener(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Set<String> activeDMRoomIds = new HashSet<>();
                Set<String> currentFriendUids = new HashSet<>();

                if (snapshot.exists()) {
                    for (DataSnapshot snap : snapshot.getChildren()) {
                        String friendUid = snap.getKey();
                        if (friendUid != null) {
                            currentFriendUids.add(friendUid);
                            
                            // Compute DM Room ID
                            String dmRoomId = currentUserId.compareTo(friendUid) < 0 
                                    ? "dm_" + currentUserId + "_" + friendUid 
                                    : "dm_" + friendUid + "_" + currentUserId;
                            activeDMRoomIds.add(dmRoomId);

                            // Listen to friend profile to resolve display name
                            if (!friendProfileListeners.containsKey(friendUid)) {
                                listenToFriendProfileForName(friendUid, dmRoomId);
                            }
                        }
                    }
                }

                // Clean up profile listeners for friends that are no longer friends
                Iterator<String> friendIter = friendProfileListeners.keySet().iterator();
                while (friendIter.hasNext()) {
                    String friendUid = friendIter.next();
                    if (!currentFriendUids.contains(friendUid)) {
                        friendProfileListeners.get(friendUid).remove();
                        friendIter.remove();
                    }
                }

                // Clean up DM message listeners
                Iterator<Map.Entry<String, ChildEventListener>> dbIter = dbListeners.entrySet().iterator();
                while (dbIter.hasNext()) {
                    Map.Entry<String, ChildEventListener> entry = dbIter.next();
                    String roomId = entry.getKey();
                    if (roomId.startsWith("dm_")) {
                        if (!activeDMRoomIds.contains(roomId)) {
                            DatabaseReference ref = Firebase.getMessagesRefByRoom(roomId);
                            ref.removeEventListener(entry.getValue());
                            dbIter.remove();
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error listening to friends in service", error.toException());
            }
        });
    }

    private void listenToFriendProfileForName(String friendUid, String dmRoomId) {
        ListenerRegistration reg = FirebaseFirestore.getInstance().collection("users").document(friendUid)
                .addSnapshotListener((doc, err) -> {
                    if (err != null) return;
                    if (doc != null && doc.exists()) {
                        String username = doc.getString("username");
                        if (username == null || username.trim().isEmpty()) {
                            username = doc.getString("email");
                        }
                        if (username != null) {
                            channelNames.put(dmRoomId, username);
                            // Attach message listener if not already attached
                            if (!dbListeners.containsKey(dmRoomId)) {
                                attachMessageListener(dmRoomId, username);
                            }
                        }
                    }
                });
        friendProfileListeners.put(friendUid, reg);
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

                if ("reminder".equals(message.getType())) {
                    if (message.getReminderTime() > System.currentTimeMillis()) {
                        scheduleReminder(chatId, chatName, message);
                    }
                    return; // Don't show immediate notification for reminder
                }

                // Ignore if sent by the current user
                if (currentUserId != null && currentUserId.equals(message.getSenderId())) {
                    return;
                }

                // Ignore if the user is currently viewing this chat room
                if (chatId.equals(ChatFragment.activeChatId)) {
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

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Message message = snapshot.getValue(Message.class);
                if (message == null) return;
                
                if ("reminder".equals(message.getType())) {
                    if (message.isDeleted()) {
                        cancelReminder(message);
                    } else if (message.getReminderTime() > System.currentTimeMillis()) {
                        scheduleReminder(chatId, chatName, message);
                    }
                }
            }

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

        String title;
        if (chatId.startsWith("dm_")) {
            title = senderName;
        } else {
            title = senderName + " (#" + chatName + ")";
            if (currentUserUsername != null && !currentUserUsername.isEmpty()) {
                String mentionTag = "@" + currentUserUsername.toLowerCase();
                if (contentText != null && contentText.toLowerCase().contains(mentionTag)) {
                    title = "📌 Nhắc tới bạn: " + senderName + " (#" + chatName + ")";
                }
            }
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("CHAT_ID", chatId);
        intent.putExtra("CHAT_NAME", chatName);
        intent.putExtra("SERVER_COLOR", "#5865F2");
        intent.putExtra("SERVER_ID", (String) null);
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

        // Save this notification event to Firestore history
        saveNotificationToFirestore(chatId, chatName, senderName, message);
    }

    private void saveNotificationToFirestore(String chatId, String chatName, String senderName, Message message) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId == null) return;

        String contentText;
        if ("image".equals(message.getType())) {
            contentText = "📷 Đã gửi một ảnh";
        } else if ("file".equals(message.getType())) {
            contentText = "📎 Đã gửi một tài liệu";
        } else {
            contentText = message.getContent();
        }

        String title;
        String type;
        if (chatId.startsWith("dm_")) {
            title = "Tin nhắn từ " + senderName;
            type = "dm";
        } else {
            title = "Nhắc tới bạn ở #" + chatName;
            type = "mention";

            // If it is a channel, only save notification history if user was mentioned
            if (currentUserUsername != null && !currentUserUsername.isEmpty()) {
                String mentionTag = "@" + currentUserUsername.toLowerCase();
                if (contentText == null || !contentText.toLowerCase().contains(mentionTag)) {
                    return;
                }
            } else {
                return;
            }
        }

        Map<String, Object> notif = new HashMap<>();
        String notifId = message.getMessageId() != null ? message.getMessageId() : String.valueOf(System.currentTimeMillis());
        notif.put("notificationId", notifId);
        notif.put("title", title);
        notif.put("content", contentText);
        notif.put("type", type);
        notif.put("senderId", message.getSenderId());
        notif.put("senderName", senderName);
        notif.put("targetId", chatId);
        notif.put("timestamp", message.getTimestamp());
        notif.put("isRead", false);

        FirebaseFirestore.getInstance().collection("users")
                .document(currentUserId)
                .collection("notifications")
                .document(notifId)
                .set(notif)
                .addOnFailureListener(e -> Log.e(TAG, "Error saving notification to Firestore", e));
    }

    private void scheduleReminder(String chatId, String chatName, Message message) {
        android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("CHAT_ID", chatId);
        intent.putExtra("CHAT_NAME", chatName);
        intent.putExtra("CONTENT", message.getContent());
        int notificationId = (message.getMessageId() != null) ? message.getMessageId().hashCode() : (int) System.currentTimeMillis();
        intent.putExtra("NOTIFICATION_ID", notificationId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, message.getReminderTime(), pendingIntent);
                } else {
                    alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, message.getReminderTime(), pendingIntent);
                }
            } catch (SecurityException e) {
                alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, message.getReminderTime(), pendingIntent);
            }
        }
    }

    private void cancelReminder(Message message) {
        android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        int notificationId = (message.getMessageId() != null) ? message.getMessageId().hashCode() : 0;
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }
}
