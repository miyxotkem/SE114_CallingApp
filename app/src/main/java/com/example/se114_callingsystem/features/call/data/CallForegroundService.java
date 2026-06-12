package com.example.se114_callingsystem.features.call.data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.features.call.ui.CallActivity;
import com.example.se114_callingsystem.features.call.ui.VoiceCallFragment;

public class CallForegroundService extends Service {
    private static final String TAG = "CallForegroundService";
    private static final String CHANNEL_ID = "VoiceCallChannel";
    private static final int NOTIFICATION_ID = 2;

    public static final String ACTION_START = "com.example.se114_callingsystem.ACTION_START";
    public static final String ACTION_STOP = "com.example.se114_callingsystem.ACTION_STOP";
    public static final String ACTION_MUTE = "com.example.se114_callingsystem.ACTION_MUTE";
    public static final String ACTION_HANGUP = "com.example.se114_callingsystem.ACTION_HANGUP";

    public static final String BROADCAST_HANGUP = "com.example.se114_callingsystem.BROADCAST_HANGUP";
    public static final String BROADCAST_MUTE_TOGGLE = "com.example.se114_callingsystem.BROADCAST_MUTE_TOGGLE";

    private String mChannelName = "Cuộc gọi thoại";
    private boolean mIsMuted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Voice Call Service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Kênh thông báo cho cuộc gọi thoại đang diễn ra");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "onStartCommand Action: " + action);

            if (action.equals(ACTION_START)) {
                mChannelName = intent.getStringExtra("CHANNEL_NAME");
                if (mChannelName == null) mChannelName = "Kênh cuộc gọi";
                mIsMuted = intent.getBooleanExtra("IS_MUTED", false);
                startForegroundWithNotification();
            } else if (action.equals(ACTION_MUTE)) {
                toggleMute();
            } else if (action.equals(ACTION_HANGUP)) {
                hangUp();
            } else if (action.equals(ACTION_STOP)) {
                stopForeground(true);
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private void startForegroundWithNotification() {
        Notification notification = buildNotification(mChannelName, mIsMuted);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "startForeground error: " + e.getMessage());
            stopSelf();
        }
    }

    private Notification buildNotification(String channelName, boolean isMuted) {
        Intent notificationIntent = new Intent(this, CallActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        
        int pendingFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT;
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, pendingFlags);

        // Intent for Mute Action
        Intent muteIntent = new Intent(this, CallForegroundService.class);
        muteIntent.setAction(ACTION_MUTE);
        PendingIntent mutePendingIntent = PendingIntent.getService(
                this, 1, muteIntent, pendingFlags);

        // Intent for Hangup Action
        Intent hangupIntent = new Intent(this, CallForegroundService.class);
        hangupIntent.setAction(ACTION_HANGUP);
        PendingIntent hangupPendingIntent = PendingIntent.getService(
                this, 2, hangupIntent, pendingFlags);

        String muteText = isMuted ? "Bật mic" : "Tắt mic";
        int muteIcon = isMuted ? android.R.drawable.button_onoff_indicator_off : android.R.drawable.button_onoff_indicator_on;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Cuộc gọi đang diễn ra")
                .setContentText("Phòng: " + channelName)
                .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .addAction(muteIcon, muteText, mutePendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Gác máy", hangupPendingIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }

        return builder.build();
    }

    private void toggleMute() {
        mIsMuted = !mIsMuted;
        
        // Cập nhật trạng thái Agora RtcEngine
        if (VoiceCallFragment.sRtcEngine != null) {
            VoiceCallFragment.sRtcEngine.muteLocalAudioStream(mIsMuted);
        }

        // Cập nhật danh sách người tham gia cục bộ nếu có
        if (VoiceCallFragment.sParticipantList != null && !VoiceCallFragment.sParticipantList.isEmpty()) {
            VoiceCallFragment.sParticipantList.get(0).isMuted = mIsMuted;
        }

        // Cập nhật Notification hiển thị trạng thái mới
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(mChannelName, mIsMuted));
        }

        // Gửi Broadcast thông báo cho VoiceCallFragment cập nhật UI
        Intent intent = new Intent(BROADCAST_MUTE_TOGGLE);
        intent.putExtra("IS_MUTED", mIsMuted);
        sendBroadcast(intent);
    }

    private void hangUp() {
        Log.d(TAG, "Hang Up from Notification");
        
        // Rời khỏi Agora
        if (VoiceCallFragment.sRtcEngine != null) {
            try {
                VoiceCallFragment.sRtcEngine.stopPreview();
                VoiceCallFragment.sRtcEngine.leaveChannel();
            } catch (Exception e) {
                Log.e(TAG, "Error leaving Agora: " + e.getMessage());
            }
        }

        // Gửi Broadcast thông báo ngắt cuộc gọi để tắt Activity/Fragment
        Intent intent = new Intent(BROADCAST_HANGUP);
        sendBroadcast(intent);

        stopForeground(true);
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
