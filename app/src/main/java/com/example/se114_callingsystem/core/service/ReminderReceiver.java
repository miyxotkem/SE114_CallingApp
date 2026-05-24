package com.example.se114_callingsystem.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.example.se114_callingsystem.chat.ChatDetailActivity;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "ReminderChannel";
    private static final String CHANNEL_NAME = "Lịch hẹn";

    @Override
    public void onReceive(Context context, Intent intent) {
        String chatId = intent.getStringExtra("CHAT_ID");
        String chatName = intent.getStringExtra("CHAT_NAME");
        String content = intent.getStringExtra("CONTENT");
        int notificationId = intent.getIntExtra("NOTIFICATION_ID", (int) System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Thông báo lời nhắc");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Intent openIntent = new Intent(context, ChatDetailActivity.class);
        if (chatId != null) openIntent.putExtra("CHAT_ID", chatId);
        if (chatName != null) openIntent.putExtra("CHAT_NAME", chatName);
        openIntent.putExtra("SERVER_COLOR", "#6C63FF");
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("⏰ Lời nhắc trong #" + (chatName != null ? chatName : ""))
                .setContentText(content != null ? content : "Bạn có một lời nhắc.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, builder.build());
        }
    }
}
