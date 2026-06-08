package com.example.se114_callingsystem.core.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class MyScreenShareService extends Service {
    private static final String CHANNEL_ID = "ScreenShareChannel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        // Tạo channel ngay khi service vừa được tạo ra với độ ưu tiên cao để tránh bị OS kill
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Screen Share Service",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Kênh thông báo cho chức năng chia sẻ màn hình cuộc gọi");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Tạo content intent để khi nhấn vào notification sẽ quay lại app
        Intent notificationIntent = new Intent(this, com.example.se114_callingsystem.MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        int pendingFlags = android.app.PendingIntent.FLAG_IMMUTABLE;
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                this, 0, notificationIntent, pendingFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Sharing")
                .setContentText("Hệ thống đang chia sẻ màn hình của bạn")
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Ưu tiên hiển thị cao để giữ app tồn tại
                .setOngoing(true) // Giữ notification cố định không cho gạt bỏ
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent);

        // Quy chuẩn Android 14+ yêu cầu hiển thị notification lập tức khi bắt đầu Foreground Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }

        Notification notification = builder.build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Sử dụng FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION bắt buộc từ Android 11 trở lên
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e("SERVICE_ERROR", "Lỗi startForeground: " + e.getMessage());
            stopSelf(); // Dừng service nếu không thể nâng lên foreground để tránh đơ app
        }

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}

