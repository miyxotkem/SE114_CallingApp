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
import com.example.se114_callingsystem.features.chat.ui.ChatFragment;
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
    private boolean isRingingState = false;
    private String currentCallCallerId = null;
    private String currentCallCallerName = null;
    private String currentCallChannelName = null;
    private String currentCallType = null;

    private ListenerRegistration incomingCallListener;
    private android.media.MediaPlayer ringtonePlayer;
    private static final int CALL_NOTIFICATION_ID = 10001;
    private static final String CALL_CHANNEL_ID = "CallChannel";
    private static final String CALL_CHANNEL_NAME = "Incoming Calls";

    @Override
    public void onCreate() {
        super.onCreate();
        serviceStartTime = System.currentTimeMillis();
        createNotificationChannel();
        createCallNotificationChannel();
        createForegroundChannel();
        startForegroundService();
        listenToChannels();
        listenToDMs();
        loadCurrentUserUsername();
        listenToIncomingCalls();
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
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            String channelName = intent.getStringExtra("CALL_CHANNEL_NAME");
            String callType = intent.getStringExtra("CALL_TYPE");

            if ("com.example.se114_callingsystem.ACTION_ANSWER_CALL".equals(action)) {
                handleAnswerCall(channelName, callType);
            } else if ("com.example.se114_callingsystem.ACTION_DECLINE_CALL".equals(action)) {
                handleDeclineCall();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up Firestore listener
        if (firestoreListener != null) {
            firestoreListener.remove();
        }
        if (incomingCallListener != null) {
            incomingCallListener.remove();
            incomingCallListener = null;
        }
        stopRingtone();
        cancelCallNotification();
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
                    String avatarUrl = null;
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("username");
                        if (name != null && !name.isEmpty()) {
                            senderName = name;
                        }
                        avatarUrl = documentSnapshot.getString("profilePic");
                        if (avatarUrl == null || avatarUrl.isEmpty()) {
                            avatarUrl = documentSnapshot.getString("avatarUrl");
                        }
                    }
                    
                    final String finalSenderName = senderName;
                    final String finalAvatarUrl = avatarUrl;
                    new Thread(() -> {
                        android.graphics.Bitmap avatarBitmap = null;
                        if (finalAvatarUrl != null && !finalAvatarUrl.isEmpty()) {
                            try {
                                avatarBitmap = com.bumptech.glide.Glide.with(MessageNotificationService.this)
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
                            sendNotification(chatId, chatName, finalSenderName, message, finalBitmap);
                        });
                    }).start();
                })
                .addOnFailureListener(e -> {
                    sendNotification(chatId, chatName, "Ai đó", message, null);
                });
    }

    private void sendNotification(String chatId, String chatName, String senderName, Message message, android.graphics.Bitmap avatarBitmap) {
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

        String groupKey = "com.example.se114_callingsystem.CHAT_GROUP";
        String notifId = message.getMessageId() != null ? message.getMessageId() : String.valueOf(System.currentTimeMillis());

        Intent likeIntent = new Intent(this, NotificationActionReceiver.class);
        likeIntent.setAction("com.example.se114_callingsystem.ACTION_LIKE");
        likeIntent.putExtra("CHAT_ID", chatId);
        likeIntent.putExtra("CHAT_NAME", chatName);
        likeIntent.putExtra("NOTIFICATION_ID", notifId);
        PendingIntent likePendingIntent = PendingIntent.getBroadcast(
                this,
                chatId.hashCode() + 10,
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
                chatId.hashCode() + 20,
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
                chatId.hashCode() + 30,
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
                .setSmallIcon(com.example.se114_callingsystem.R.mipmap.ic_launcher)
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
                .setSmallIcon(com.example.se114_callingsystem.R.mipmap.ic_launcher)
                .setContentTitle("Tin nhắn mới")
                .setContentText("Bạn có tin nhắn mới chưa đọc")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(chatId.hashCode(), builder.build());
            manager.notify(999, summaryBuilder.build());
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

    private void createCallNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CALL_CHANNEL_ID,
                    CALL_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Thông báo cuộc gọi đến");
            channel.enableVibration(true);
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void listenToIncomingCalls() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId == null) return;

        incomingCallListener = FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUserId)
                .collection("incomingCall")
                .document("activeCall")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error listening to incoming calls", e);
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        String status = snapshot.getString("status");
                        String callerId = snapshot.getString("callerId");
                        String callerName = snapshot.getString("callerName");
                        String channelName = snapshot.getString("channelName");
                        String callType = snapshot.getString("callType");

                        if ("ringing".equals(status)) {
                            isRingingState = true;
                            currentCallCallerId = callerId;
                            currentCallCallerName = callerName;
                            currentCallChannelName = channelName;
                            currentCallType = callType;

                            playRingtone();
                            showIncomingCallNotification(callerId, callerName, channelName, callType);
                            
                            Intent intent = new Intent("com.example.se114_callingsystem.INCOMING_CALL");
                            intent.putExtra("CALLER_ID", callerId);
                            intent.putExtra("CALLER_NAME", callerName);
                            intent.putExtra("CALL_CHANNEL_NAME", channelName);
                            intent.putExtra("CALL_TYPE", callType);
                            intent.setPackage(getPackageName());
                            sendBroadcast(intent);
                        } else if ("ended".equals(status) || "rejected".equals(status) || "answered".equals(status)) {
                            if (isRingingState && ("ended".equals(status) || "rejected".equals(status))) {
                                recordMissedCall(currentCallCallerId, currentCallCallerName, currentCallChannelName, currentCallType);
                            }
                            isRingingState = false;

                            stopRingtone();
                            cancelCallNotification();
                            Intent intent = new Intent("com.example.se114_callingsystem.DISMISS_CALL_DIALOG");
                            intent.setPackage(getPackageName());
                            sendBroadcast(intent);
                        }
                    } else {
                        if (isRingingState) {
                            recordMissedCall(currentCallCallerId, currentCallCallerName, currentCallChannelName, currentCallType);
                        }
                        isRingingState = false;

                        stopRingtone();
                        cancelCallNotification();
                        Intent intent = new Intent("com.example.se114_callingsystem.DISMISS_CALL_DIALOG");
                        intent.setPackage(getPackageName());
                        sendBroadcast(intent);
                    }
                });
    }

    private void recordMissedCall(String callerId, String callerName, String channelName, String callType) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId == null || callerId == null) return;
        if (callerId.equals(currentUserId)) return;

        String notifId = FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUserId)
                .collection("notifications")
                .document().getId();

        String callTypeStr = "voice".equals(callType) ? "cuộc gọi thoại" : "cuộc gọi video";
        String content = "Bạn có cuộc gọi nhỡ từ " + (callerName != null ? callerName : "Người dùng");

        java.util.Map<String, Object> notifMap = new java.util.HashMap<>();
        notifMap.put("notificationId", notifId);
        notifMap.put("title", "Cuộc gọi nhỡ");
        notifMap.put("content", content);
        notifMap.put("type", "missed_call");
        notifMap.put("senderId", callerId);
        notifMap.put("senderName", callerName != null ? callerName : "Người dùng");
        notifMap.put("targetId", channelName);
        notifMap.put("timestamp", System.currentTimeMillis());
        notifMap.put("isRead", false);

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUserId)
                .collection("notifications")
                .document(notifId)
                .set(notifMap)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Missed call recorded in Firestore"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to record missed call", e));

        FirebaseFirestore.getInstance().collection("users").document(callerId).get()
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
                                avatarBitmap = com.bumptech.glide.Glide.with(MessageNotificationService.this)
                                        .asBitmap()
                                        .load(finalAvatarUrl)
                                        .circleCrop()
                                        .submit()
                                        .get();
                            } catch (Exception e) {
                                Log.e(TAG, "Error loading caller avatar bitmap", e);
                            }
                        }
                        
                        android.graphics.Bitmap finalBitmap = avatarBitmap;
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            showMissedCallNotification(callerName, callType, finalBitmap);
                        });
                    }).start();
                })
                .addOnFailureListener(e -> {
                    showMissedCallNotification(callerName, callType, null);
                });
    }

    private void showMissedCallNotification(String callerName, String callType, android.graphics.Bitmap avatarBitmap) {
        String contentText = "Bạn có cuộc gọi nhỡ từ " + (callerName != null ? callerName : "Người dùng");
        Intent intent = new Intent(this, com.example.se114_callingsystem.MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("OPEN_TAB", "notifications");
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                201,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.sym_call_missed)
                .setContentTitle("Cuộc gọi nhỡ")
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (avatarBitmap != null) {
            builder.setLargeIcon(avatarBitmap);
        }

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void playRingtone() {
        if (ringtonePlayer != null) return;
        try {
            android.net.Uri ringtoneUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE);
            if (ringtoneUri == null) {
                ringtoneUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION);
            }
            ringtonePlayer = new android.media.MediaPlayer();
            ringtonePlayer.setDataSource(this, ringtoneUri);
            ringtonePlayer.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            ringtonePlayer.setLooping(true);
            ringtonePlayer.prepare();
            ringtonePlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Error playing ringtone", e);
        }
    }

    private void stopRingtone() {
        if (ringtonePlayer != null) {
            try {
                if (ringtonePlayer.isPlaying()) {
                    ringtonePlayer.stop();
                }
                ringtonePlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping ringtone", e);
            }
            ringtonePlayer = null;
        }
    }

    private void showIncomingCallNotification(String callerId, String callerName, String channelName, String callType) {
        Intent answerIntent = new Intent(this, com.example.se114_callingsystem.features.call.ui.CallActivity.class);
        answerIntent.setAction("com.example.se114_callingsystem.ACTION_ANSWER_CALL");
        answerIntent.putExtra("CALL_CHANNEL_NAME", channelName);
        answerIntent.putExtra("CALL_TYPE", callType);
        answerIntent.putExtra("IS_CALLER", false);
        answerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent answerPendingIntent = PendingIntent.getActivity(
                this,
                101,
                answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent declineIntent = new Intent(this, MessageNotificationService.class);
        declineIntent.setAction("com.example.se114_callingsystem.ACTION_DECLINE_CALL");
        declineIntent.putExtra("CALL_CHANNEL_NAME", channelName);
        PendingIntent declinePendingIntent = PendingIntent.getService(
                this,
                102,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent contentIntent = new Intent(this, com.example.se114_callingsystem.features.call.ui.IncomingCallActivity.class);
        contentIntent.putExtra("CALLER_ID", callerId);
        contentIntent.putExtra("CALLER_NAME", callerName);
        contentIntent.putExtra("CALL_CHANNEL_NAME", channelName);
        contentIntent.putExtra("CALL_TYPE", callType);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this,
                103,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String callTypeStr = "voice".equals(callType) ? "cuộc gọi thoại" : "cuộc gọi video";
        String title = "Cuộc gọi đến từ " + callerName;
        String contentText = "Đang mời bạn tham gia " + callTypeStr + "...";

        androidx.core.app.Person caller = new androidx.core.app.Person.Builder()
                .setName(callerName)
                .build();

        androidx.core.app.NotificationCompat.CallStyle callStyle = 
                androidx.core.app.NotificationCompat.CallStyle.forIncomingCall(
                        caller,
                        declinePendingIntent,
                        answerPendingIntent
                );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CALL_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setContentTitle(title)
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(false)
                .setOngoing(true)
                .setStyle(callStyle)
                .setContentIntent(contentPendingIntent);

        builder.setFullScreenIntent(contentPendingIntent, true);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(CALL_NOTIFICATION_ID, builder.build());
        }
    }

    private void cancelCallNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(CALL_NOTIFICATION_ID);
        }
    }

    private void handleAnswerCall(String channelName, String callType) {
        stopRingtone();
        cancelCallNotification();

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId != null) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUserId)
                    .collection("incomingCall")
                    .document("activeCall")
                    .update("status", "answered")
                    .addOnFailureListener(e -> Log.e(TAG, "Error updating status to answered", e));
        }

        Intent callIntent = new Intent(this, com.example.se114_callingsystem.features.call.ui.CallActivity.class);
        callIntent.putExtra("CALL_CHANNEL_NAME", channelName);
        callIntent.putExtra("SERVER_ID", (String) null);
        callIntent.putExtra("IS_CALLER", false);
        callIntent.putExtra("CALL_TYPE", callType);
        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(callIntent);

        Intent broadcastIntent = new Intent("com.example.se114_callingsystem.DISMISS_CALL_DIALOG");
        broadcastIntent.setPackage(getPackageName());
        sendBroadcast(broadcastIntent);
    }

    private void handleDeclineCall() {
        stopRingtone();
        cancelCallNotification();

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId != null) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUserId)
                    .collection("incomingCall")
                    .document("activeCall")
                    .update("status", "rejected")
                    .addOnFailureListener(e -> Log.e(TAG, "Error updating status to rejected", e));
        }

        Intent broadcastIntent = new Intent("com.example.se114_callingsystem.DISMISS_CALL_DIALOG");
        broadcastIntent.setPackage(getPackageName());
        sendBroadcast(broadcastIntent);
    }
}
