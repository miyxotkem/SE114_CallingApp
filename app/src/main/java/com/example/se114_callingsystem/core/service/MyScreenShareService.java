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
        // Táº¡o channel ngay khi service vá»«a Ä‘Æ°á»£c táº¡o ra
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
                .setContentText("Há»‡ thá»‘ng Ä‘ang chia sáº» mÃ n hÃ¬nh cá»§a báº¡n")
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true) // Quan trá»ng: Giá»¯ notification khÃ´ng bá»‹ gáº¡t máº¥t
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Äáº£m báº£o dÃ¹ng Ä‘Ãºng Type lÃ  MEDIA_PROJECTION
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e("SERVICE_ERROR", "Lá»—i FGS: " + e.getMessage());
            // Náº¿u váº«n crash, hÃ£y thá»­ gá»i stopSelf() Ä‘á»ƒ trÃ¡nh treo app
        }

        return START_STICKY; // Äá»•i sang START_STICKY Ä‘á»ƒ service tá»± khá»Ÿi Ä‘á»™ng láº¡i náº¿u bá»‹ kill
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}

