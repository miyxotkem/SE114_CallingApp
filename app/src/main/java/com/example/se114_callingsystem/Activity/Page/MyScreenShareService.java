package com.example.se114_callingsystem.Activity.Page;

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
        // Tạo channel ngay khi service vừa được tạo ra
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Screen Share Service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Sharing")
                .setContentText("Hệ thống đang chia sẻ màn hình của bạn")
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true) // Quan trọng: Giữ notification không bị gạt mất
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Đảm bảo dùng đúng Type là MEDIA_PROJECTION
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e("SERVICE_ERROR", "Lỗi FGS: " + e.getMessage());
            // Nếu vẫn crash, hãy thử gọi stopSelf() để tránh treo app
        }

        return START_STICKY; // Đổi sang START_STICKY để service tự khởi động lại nếu bị kill
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}