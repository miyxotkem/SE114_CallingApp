package com.example.se114_callingsystem.Activity.Page;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se114_callingsystem.Model.Participant;
import com.example.se114_callingsystem.Adapter.ParticipantAdapter;
import com.example.se114_callingsystem.R;

import java.util.ArrayList;
import java.util.List;

import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.ScreenCaptureParameters;
import io.agora.rtc2.video.VideoEncoderConfiguration;

public class CallDetailActivity extends AppCompatActivity {
    private final String appId = "54381d815bd74264923f243e5a1f0660";
    private RtcEngine mRtcEngine;

    // NHÃ ƠI: Nhớ chỉnh UID này khác nhau trên 2 máy để không bị đá nhau nhé!
    int uid = 400;

    private String channelName = "TestChannel";
    private boolean isSharingScreen = false;
    private static final int SCREEN_SHARE_REQUEST_CODE = 1001;
    private MediaProjectionManager mProjectionManager;
    private boolean isUiVisible = true;

    private RecyclerView rvParticipants;
    private LinearLayout callHeader;
    private CardView controlPanel;
    private TextView tvCallChannelName, tvParticipantCount;

    private ParticipantAdapter adapter;
    private List<Participant> participantList = new ArrayList<>();

    private static final int PERMISSION_REQ_ID = 22;
    private static final String[] REQUESTED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_PHONE_STATE // Thêm quyền này để fix SecurityException
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_call_detail);
        setupVideoSDKEngine();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rvParticipants), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top + 180, systemBars.right, systemBars.bottom + 250);
            return insets;
        });

        initViews();
        setupTapToHide();

        if (checkSelfPermission(REQUESTED_PERMISSIONS[0], PERMISSION_REQ_ID) &&
                checkSelfPermission(REQUESTED_PERMISSIONS[1], PERMISSION_REQ_ID) &&
                checkSelfPermission(REQUESTED_PERMISSIONS[2], PERMISSION_REQ_ID)) {
            initAgoraAndJoinChannel();
        }
    }

    private void initViews() {
        rvParticipants = findViewById(R.id.rvParticipants);
        callHeader = findViewById(R.id.callHeader);
        controlPanel = findViewById(R.id.controlPanel);
        tvCallChannelName = findViewById(R.id.tvCallChannelName);
        tvParticipantCount = findViewById(R.id.tvParticipantCount);

        String passedChannel = getIntent().getStringExtra("CALL_CHANNEL_NAME");
        if (passedChannel != null) {
            channelName = passedChannel;
            tvCallChannelName.setText(channelName);
        }

        ImageButton btnMinimize = findViewById(R.id.btnMinimize);
        btnMinimize.setOnClickListener(v -> finish());
    }

    private void showUserJoinedAlert(String displayId) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Thông báo hệ thống")
                .setMessage("Bạn đang join với UID: " + displayId)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private void setupVideoSDKEngine() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getBaseContext();
            config.mAppId = appId; // PHẢI CÓ APP ID Ở ĐÂY
            config.mEventHandler = mRtcEventHandler;
            mRtcEngine = RtcEngine.create(config);

            if (mRtcEngine != null) {
                mRtcEngine.enableVideo();
                Log.d("AGORA_DEBUG", "Engine đã khởi tạo thành công!");
            } else {
                Log.e("AGORA_DEBUG", "Engine khởi tạo thất bại (null)");
            }
        } catch (Exception e) {
            Log.e("AGORA_DEBUG", "Lỗi khởi tạo: " + e.getMessage());
        }
    }
    private void initAgoraAndJoinChannel() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getBaseContext();
            config.mAppId = appId;
            config.mEventHandler = mRtcEventHandler;
            // 1. Ép vùng kết nối là Toàn cầu để máy thật và máy ảo gặp nhau dễ hơn // Thêm chữ AL ở cuối
            mRtcEngine = RtcEngine.create(config);

            // 2. Thiết lập cấu hình Video trước khi Join
            mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION); // Chế độ gọi điện
            mRtcEngine.enableVideo();
//            mRtcEngine.startPreview();
            mRtcEngine.muteLocalVideoStream(true);
            mRtcEngine.enableAudioVolumeIndication(200, 3, true);
            // 3. THÊM DÒNG NÀY: Ép SDK sử dụng giao thức kết nối mạnh nhất
            mRtcEngine.setParameters("{\"rtc.force_unified_communication_mode\":true}");

            setupRecyclerView();

            // 4. Join Channel - Dùng UID đã sửa thủ công
            int res = mRtcEngine.joinChannel(null, channelName, "", uid);
            setupControls();
            if (res != 0) {
                Log.e("AgoraCheck", "Join failed với mã: " + res);
            }
        } catch (Exception e) {
            Log.e("AgoraCheck", "Lỗi khởi tạo: " + e.getMessage());
        }
    }

    private void setupRecyclerView() {
        adapter = new ParticipantAdapter(this, participantList, mRtcEngine);
        rvParticipants.setAdapter(adapter);
        updateGridLayout();
    }

    private void updateGridLayout() {
        int count = participantList.size();
        int spanCount = (count <= 2) ? 1 : (count <= 4 ? 2 : 3);

        if (rvParticipants.getLayoutManager() instanceof GridLayoutManager) {
            ((GridLayoutManager) rvParticipants.getLayoutManager()).setSpanCount(spanCount);
        } else {
            GridLayoutManager layoutManager = new GridLayoutManager(this, spanCount);
            rvParticipants.setLayoutManager(layoutManager);
        }
    }

    private void updateParticipantCount() {
        tvParticipantCount.setText(participantList.size() + " participants");
    }

    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onUserJoined(int uid, int elapsed) {
            runOnUiThread(() -> {
                // Nhã ơi dùng Alert ở đây nếu muốn báo người khác vào
                Toast.makeText(CallDetailActivity.this, "User " + uid + " đã vào phòng!", Toast.LENGTH_SHORT).show();
                Participant newUser = new Participant(uid, "User " + uid);
                newUser.isVideoOff = true;
                mRtcEngine.setEnableSpeakerphone(true);
                participantList.add(newUser);
                updateGridLayout();
                adapter.notifyDataSetChanged();
                updateParticipantCount();
            });
        }

        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            runOnUiThread(() -> {
                boolean exists = false;
                for (Participant p : participantList) {
                    if (p.uid == uid) { exists = true; break; }
                }

                if (!exists) {
                    Participant me = new Participant(uid, "Me (" + uid + ")");
                    me.isVideoOff = true;
                    participantList.add(0, me);
                    updateGridLayout();
                    adapter.notifyItemInserted(0);
                    updateParticipantCount();
                }
            });
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            runOnUiThread(() -> {
                for (int i = 0; i < participantList.size(); i++) {
                    if (participantList.get(i).uid == uid) {
                        participantList.remove(i);
                        updateGridLayout();
                        adapter.notifyItemRemoved(i);
                        updateParticipantCount();
                        break;
                    }
                }
            });
        }

        @Override
        public void onAudioVolumeIndication(AudioVolumeInfo[] speakers, int totalVolume) {
            runOnUiThread(() -> {
                List<Integer> activeSpeakers = new ArrayList<>();
                for (AudioVolumeInfo speaker : speakers) {
                    if (speaker.volume > 0) {
                        activeSpeakers.add(speaker.uid);
                    }
                }

                for (int i = 0; i < participantList.size(); i++) {
                    Participant p = participantList.get(i);
                    // Local user (index 0) luôn báo volume với ID 0
                    int checkUid = (i == 0) ? 0 : p.uid;

                    boolean isNowSpeaking = activeSpeakers.contains(checkUid);
                    if (p.isSpeaking != isNowSpeaking) {
                        p.isSpeaking = isNowSpeaking;
                        adapter.notifyItemChanged(i, "border_update");
                    }
                }
            });
        }

        @Override
        public void onUserMuteVideo(int uid, boolean muted) {
            runOnUiThread(() -> {
                for (int i = 0; i < participantList.size(); i++) {
                    if (participantList.get(i).uid == uid) {
                        participantList.get(i).isVideoOff = muted;
                        adapter.notifyItemChanged(i, "state_update");
                        break;
                    }
                }
            });
        }
        @Override
        public void onRemoteVideoStateChanged(int uid, int state, int reason, int elapsed) {
            runOnUiThread(() -> {
                for (int i = 0; i < participantList.size(); i++) {
                    if (participantList.get(i).uid == uid) {
                        // state == 0 nghĩa là STOPPED (Tắt cam)
                        // state == 1 hoặc 2 nghĩa là STARTING/DECODING (Bật cam)
                        boolean isOff = (state == 0);

                        if (participantList.get(i).isVideoOff != isOff) {
                            participantList.get(i).isVideoOff = isOff;
                            adapter.notifyItemChanged(i, "state_update");
                        }
                        break;
                    }
                }
            });
        }
    };
    // 1. Hàm bắt đầu quá trình share màn hình
    private void startScreenShare() {
        if (mRtcEngine == null) return;

        // 1. Chạy Service
        Intent serviceIntent = new Intent(this, MyScreenShareService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // 2. Chờ 100ms rồi mới xin quyền (Để Android 14 kịp nhận diện FGS)
        new android.os.Handler().postDelayed(() -> {
            mProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (mProjectionManager != null) {
                Intent intent = mProjectionManager.createScreenCaptureIntent();
                startActivityForResult(intent, SCREEN_SHARE_REQUEST_CODE);
            }
        }, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SCREEN_SHARE_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                // Đã có quyền, tiến hành share
                startAgoraScreenCapture(data);
            } else {
                // Nếu người dùng nhấn "Hủy", phải dừng Service ngay để không bị lỗi
                stopScreenShare();
                Toast.makeText(this, "Đã hủy chia sẻ màn hình", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void startAgoraScreenCapture(Intent data) {
        ScreenCaptureParameters params = new ScreenCaptureParameters();
        params.captureVideo = true;
        params.captureAudio = true;
        mRtcEngine.startScreenCapture(params);

        ChannelMediaOptions options = new ChannelMediaOptions();
        options.publishCameraTrack = false;
        options.publishScreenCaptureVideo = true;
        mRtcEngine.updateChannelMediaOptions(options);

        isSharingScreen = true;
        updateShareButtonUI();
    }



    // 4. Hàm dừng share màn hình
    private void stopScreenShare() {
        mRtcEngine.stopScreenCapture();

        // Quay lại dùng camera
        ChannelMediaOptions options = new ChannelMediaOptions();
        options.publishCameraTrack = true;
        options.publishScreenCaptureVideo = false;
        mRtcEngine.updateChannelMediaOptions(options);

        // Dừng service
        Intent serviceIntent = new Intent(this, MyScreenShareService.class);
        stopService(serviceIntent);

        isSharingScreen = false;
    }

    private void updateShareButtonUI() {
        ImageButton btnShareScreen = findViewById(R.id.btnShareScreen);
        if (btnShareScreen != null && !participantList.isEmpty()) {
            boolean sharing = participantList.get(0).isSharingScreen;
            btnShareScreen.setSelected(sharing);
            btnShareScreen.setAlpha(sharing ? 1.0f : 0.5f);
        }
    }
    private void setupControls() {
        ImageButton btnMute = findViewById(R.id.btnMute);
        ImageButton btnToggleVideo = findViewById(R.id.btnToggleVideo);
        ImageButton btnEndCall = findViewById(R.id.btnEndCall);

        btnToggleVideo.setSelected(true);
        btnToggleVideo.setAlpha(0.5f);

        btnMute.setOnClickListener(v -> {
            boolean isMuted = !v.isSelected();
            v.setSelected(isMuted);
            mRtcEngine.muteLocalAudioStream(isMuted);
            v.setAlpha(isMuted ? 0.5f : 1.0f);
            if (!participantList.isEmpty()) {
                participantList.get(0).isMuted = isMuted;
                adapter.notifyItemChanged(0, "state_update");
            }
        });

        btnToggleVideo.setOnClickListener(v -> {
            boolean isVideoOff = !v.isSelected();
            v.setSelected(isVideoOff);
            mRtcEngine.muteLocalVideoStream(isVideoOff);
            v.setAlpha(isVideoOff ? 0.5f : 1.0f);
            if (!participantList.isEmpty()) {
                participantList.get(0).isVideoOff = isVideoOff;
                adapter.notifyItemChanged(0, "state_update");
            }
        });

        btnEndCall.setOnClickListener(v -> finish());

        ImageButton btnShareScreen = findViewById(R.id.btnShareScreen);
        if (btnShareScreen != null) {
            btnShareScreen.setOnClickListener(v -> {
                if (participantList.isEmpty()) return;

                // Kiểm tra trạng thái share từ đối tượng Participant của chính mình
                if (!participantList.get(0).isSharingScreen) {
                    startScreenShare();
                } else {
                    stopScreenShare();
                }
            });
        }

        findViewById(R.id.btnEndCall).setOnClickListener(v -> finish());
    }

    private void setupTapToHide() {
        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                isUiVisible = !isUiVisible;
                int visibility = isUiVisible ? View.VISIBLE : View.GONE;
                callHeader.setVisibility(visibility);
                controlPanel.setVisibility(visibility);
                return true;
            }
        });

        rvParticipants.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                gestureDetector.onTouchEvent(e);
                return false;
            }
        });
    }

    private boolean checkSelfPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, requestCode);
            return false;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mRtcEngine != null) {
            mRtcEngine.leaveChannel();
            RtcEngine.destroy();
            mRtcEngine = null;
        }
    }
}
