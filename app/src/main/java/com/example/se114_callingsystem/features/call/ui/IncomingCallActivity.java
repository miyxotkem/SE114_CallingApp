package com.example.se114_callingsystem.features.call.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.R;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.firestore.FirebaseFirestore;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class IncomingCallActivity extends AppCompatActivity {

    private String callerId;
    private String callerName;
    private String channelName;
    private String callType;

    private ShapeableImageView ivCallerAvatar;
    private TextView tvCallerName;
    private TextView tvCallTypeDesc;
    private MaterialCardView btnDeclineCall;
    private MaterialCardView btnAnswerCall;
    private AnimatorSet answerBtnAnimator;

    private final BroadcastReceiver dismissReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.se114_callingsystem.DISMISS_CALL_DIALOG".equals(intent.getAction())) {
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Cấu hình hiển thị đè màn hình khóa và bật màn hình
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }

        // Kiểm tra xem màn hình thiết bị có đang bị khóa hay không
        boolean isLocked = false;
        android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null) {
            isLocked = km.isKeyguardLocked();
        }

        if (isLocked) {
            // Trường hợp 1: Màn hình khóa -> Dùng giao diện toàn màn hình sang trọng
            setContentView(R.layout.activity_incoming_call);
        } else {
            // Trường hợp 2 & 3: Không khóa (đang mở máy) -> Dùng giao diện floating banner ở trên cùng
            android.view.Window window = getWindow();
            if (window != null) {
                window.setGravity(android.view.Gravity.TOP);
                window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                
                android.view.WindowManager.LayoutParams lp = window.getAttributes();
                lp.y = (int) (16 * getResources().getDisplayMetrics().density);
                window.setAttributes(lp);
            }
            setContentView(R.layout.layout_incoming_call_banner);
        }

        // Đọc dữ liệu từ Intent
        callerId = getIntent().getStringExtra("CALLER_ID");
        callerName = getIntent().getStringExtra("CALLER_NAME");
        channelName = getIntent().getStringExtra("CALL_CHANNEL_NAME");
        callType = getIntent().getStringExtra("CALL_TYPE");

        // Ánh xạ UI
        ivCallerAvatar = findViewById(R.id.ivCallerAvatar);
        tvCallerName = findViewById(R.id.tvCallerName);
        tvCallTypeDesc = findViewById(R.id.tvCallTypeDesc);
        btnDeclineCall = findViewById(R.id.btnDeclineCall);
        btnAnswerCall = findViewById(R.id.btnAnswerCall);

        // Cập nhật thông tin UI
        tvCallerName.setText(callerName != null ? callerName : "Ai đó");
        String desc = "voice".equals(callType) ? "Cuộc gọi thoại đến..." : "Cuộc gọi video đến...";
        tvCallTypeDesc.setText(desc);

        // Thiết lập màu sắc card theo đúng theme
        btnAnswerCall.setCardBackgroundColor(Color.parseColor("#43B581")); // discord green
        btnDeclineCall.setCardBackgroundColor(Color.parseColor("#F04747")); // discord red

        // Tải ảnh đại diện người gọi
        if (callerId != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(callerId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && !isFinishing()) {
                        String avatarUrl = documentSnapshot.getString("profilePic");
                        if (avatarUrl == null || avatarUrl.isEmpty()) {
                            avatarUrl = documentSnapshot.getString("avatarUrl");
                        }
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            Glide.with(IncomingCallActivity.this)
                                .load(avatarUrl)
                                .placeholder(R.drawable.ic_user)
                                .into(ivCallerAvatar);
                        }
                    }
                });
        }

        // Sự kiện nút Từ chối
        btnDeclineCall.setOnClickListener(v -> {
            Intent declineIntent = new Intent(this, com.example.se114_callingsystem.core.service.MessageNotificationService.class);
            declineIntent.setAction("com.example.se114_callingsystem.ACTION_DECLINE_CALL");
            declineIntent.putExtra("CALL_CHANNEL_NAME", channelName);
            startService(declineIntent);
            finish();
        });

        // Sự kiện nút Nhận cuộc gọi
        btnAnswerCall.setOnClickListener(v -> {
            String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null 
                    ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
            if (currentUserId != null) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(currentUserId)
                        .collection("incomingCall")
                        .document("activeCall")
                        .update("status", "answered");
            }

            Intent callIntent = new Intent(this, com.example.se114_callingsystem.features.call.ui.CallActivity.class);
            callIntent.putExtra("CALL_CHANNEL_NAME", channelName);
            callIntent.putExtra("SERVER_ID", (String) null);
            callIntent.putExtra("IS_CALLER", false);
            callIntent.putExtra("CALL_TYPE", callType);
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(callIntent);
            finish();
        });

        // Hiệu ứng nhịp đập cho nút Nhận cuộc gọi
        try {
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(btnAnswerCall, "scaleX", 1f, 1.15f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(btnAnswerCall, "scaleY", 1f, 1.15f, 1f);
            scaleX.setDuration(1200);
            scaleY.setDuration(1200);
            scaleX.setRepeatCount(ValueAnimator.INFINITE);
            scaleY.setRepeatCount(ValueAnimator.INFINITE);
            
            answerBtnAnimator = new AnimatorSet();
            answerBtnAnimator.playTogether(scaleX, scaleY);
            answerBtnAnimator.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Đăng ký nhận broadcast đóng màn hình cuộc gọi đến
        IntentFilter filter = new IntentFilter("com.example.se114_callingsystem.DISMISS_CALL_DIALOG");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(dismissReceiver, filter);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (answerBtnAnimator != null) {
            try {
                answerBtnAnimator.cancel();
            } catch (Exception ignored) {}
            answerBtnAnimator = null;
        }
        try {
            unregisterReceiver(dismissReceiver);
        } catch (Exception ignored) {}
    }
}
