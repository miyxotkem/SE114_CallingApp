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
import com.example.se114_callingsystem.Util.ThemeHelper;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineEx;
import io.agora.rtc2.RtcConnection;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.ScreenCaptureParameters;
import io.agora.rtc2.video.VideoEncoderConfiguration;

public class CallDetailActivity extends AppCompatActivity {
    private final String appId = "54381d815bd74264923f243e5a1f0660";
    private RtcEngine mRtcEngine;

    // NHÃ ƠI: Nhớ chỉnh UID này khác nhau trên 2 máy để không bị đá nhau nhé!
    int uid = FirebaseAuth.getInstance().getCurrentUser().getUid().hashCode();

    private String channelName = "TestChannel";
    private RtcConnection screenShareConnection;
    private final int SCREEN_SHARE_UID_OFFSET = 1000; // UID của màn hình sẽ = UID của bạn + 1000
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
    
    private String serverColor = "#6C63FF";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_call_detail);
        
        if (getIntent().hasExtra("SERVER_COLOR")) {
            serverColor = getIntent().getStringExtra("SERVER_COLOR");
        }
        
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


    private void initAgoraAndJoinChannel() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getBaseContext();
            config.mAppId = appId;
            config.mEventHandler = mRtcEventHandler;
            // 1. Ép vùng kết nối là Toàn cầu để máy thật và máy ảo gặp nhau dễ hơn // Thêm chữ AL ở cuối
            mRtcEngine = RtcEngine.create(config);

            // 2. Thiết lập cấu hình Audio + Video trước khi Join
            mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION); // Chế độ gọi điện

            // === FIX AUDIO: Bật audio engine + chống echo + chống ồn ===
            mRtcEngine.enableAudio();
            mRtcEngine.setDefaultAudioRoutetoSpeakerphone(false); // Mặc định tai nghe, tránh echo
            mRtcEngine.setParameters("{\"che.audio.enable.aec\":true}");  // Echo cancellation
            mRtcEngine.setParameters("{\"che.audio.enable.ans\":true}");  // Noise suppression
            mRtcEngine.setParameters("{\"che.audio.enable.agc\":true}");  // Auto gain control

            mRtcEngine.enableVideo();
            mRtcEngine.muteLocalVideoStream(true);
            mRtcEngine.enableAudioVolumeIndication(200, 3, true);
            // 3. Ép SDK sử dụng giao thức kết nối mạnh nhất
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
                if (uid == (CallDetailActivity.this.uid + SCREEN_SHARE_UID_OFFSET)) {
                    Log.d("AGORA", "Phát hiện luồng màn hình của chính mình, chặn render Remote.");

                    // Ép SDK không tải luồng video/audio này từ server về để tiết kiệm băng thông mạng
                    // (vì màn hình này mình đã tự vẽ ở dạng LocalVideo rồi)
                    mRtcEngine.muteRemoteVideoStream(uid, true);
                    mRtcEngine.muteRemoteAudioStream(uid, true);
                    return; // Lệnh return giúp thoát hàm ngay, không add thêm ô lưới thứ 2 nữa
                }
                // Nhã ơi dùng Alert ở đây nếu muốn báo người khác vào
                Toast.makeText(CallDetailActivity.this, "User " + uid + " đã vào phòng!", Toast.LENGTH_SHORT).show();
                Participant newUser = new Participant(uid, "User " + uid);
                newUser.isVideoOff = true;
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
                if (uid == (CallDetailActivity.this.uid + SCREEN_SHARE_UID_OFFSET)) {
                    return;
                }
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

        @Override
        public void onLocalVideoStateChanged(io.agora.rtc2.Constants.VideoSourceType source, int state, int error) {
            super.onLocalVideoStateChanged(source, state, error);

            // Nếu đây là sự kiện của luồng quay màn hình
            if (source == io.agora.rtc2.Constants.VideoSourceType.VIDEO_SOURCE_SCREEN_PRIMARY) {

                // Trạng thái CAPTURING: Người dùng đã bấm "Bắt đầu ngay" ở Popup thành công
                if (state == io.agora.rtc2.Constants.LOCAL_VIDEO_STREAM_STATE_CAPTURING) {
                    runOnUiThread(() -> {
                        // Lúc này mới được phép join luồng màn hình vào phòng
                        setupScreenShareExConnection();
                        isSharingScreen = true;
                        updateShareButtonUI();
                    });
                }
                // Trạng thái FAILED: Người dùng bấm "Hủy" không cho quay màn hình
                else if (state == io.agora.rtc2.Constants.LOCAL_VIDEO_STREAM_STATE_FAILED) {
                    runOnUiThread(() -> {
                        Toast.makeText(CallDetailActivity.this, "Đã hủy chia sẻ màn hình", Toast.LENGTH_SHORT).show();
                        stopScreenShare(); // Gọi hàm này để tắt cái Service đang chạy lỡ dở đi
                    });
                }
            }
        }
    };
    // 1. Hàm bắt đầu quá trình share màn hình
    private void startScreenShare() {
        if (mRtcEngine == null) return;
        mRtcEngine.muteLocalVideoStream(true);
        mProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (mProjectionManager != null) {
            Intent intent = mProjectionManager.createScreenCaptureIntent();
            startActivityForResult(intent, SCREEN_SHARE_REQUEST_CODE);
        }
    }

//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//
//        if (requestCode == SCREEN_SHARE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
//            // 1. Phải gán 'data' (MediaProjection Intent) cho Agora trước khi start service
//            ScreenCaptureParameters params = new ScreenCaptureParameters();
//            params.captureVideo = true;
//            params.captureAudio = true;
//            params.videoCaptureParameters.width = 720;
//            params.videoCaptureParameters.height = 1280;
//
//            // Truyền 'data' vào đây để Agora giữ Token của hệ thống
//            mRtcEngine.startScreenCapture(params);
//
//            // 2. Sau đó mới start Service để giữ app không bị kill
//            Intent intent = new Intent(this, MyScreenShareService.class);
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                startForegroundService(intent);
//            } else {
//                startService(intent);
//            }
//
//            // 3. Thực hiện joinChannelEx (như code mình đã hướng dẫn trước đó)
//            setupScreenShareExConnection();
//
//            isSharingScreen = true;
//            updateShareButtonUI();
//        }
//    }
// Hàm nối luồng phụ
private void setupScreenShareExConnection() {
    if (mRtcEngine == null) return;
    io.agora.rtc2.RtcEngineEx engineEx = (io.agora.rtc2.RtcEngineEx) mRtcEngine;
    screenShareConnection = new io.agora.rtc2.RtcConnection();
    screenShareConnection.channelId = channelName;
    screenShareConnection.localUid = uid + 1000; // UID màn hình (ví dụ 1400)

    io.agora.rtc2.ChannelMediaOptions options = new io.agora.rtc2.ChannelMediaOptions();
    options.publishCameraTrack = false;
    options.publishMicrophoneTrack = false;
    options.publishScreenCaptureVideo = true;
    options.publishScreenCaptureAudio = true;
    options.clientRoleType = io.agora.rtc2.Constants.CLIENT_ROLE_BROADCASTER;

    engineEx.joinChannelEx(null, screenShareConnection, options, new io.agora.rtc2.IRtcEngineEventHandler() {});

    // Hiện thêm 1 ô màn hình vào UI của máy mình
    Participant myScreen = new Participant(screenShareConnection.localUid, "Màn hình của tôi");
    myScreen.isVideoOff = false;
    participantList.add(myScreen);
    updateGridLayout();
    adapter.notifyItemInserted(participantList.size() - 1);
    updateParticipantCount();
}

    // Hàm dừng share
    private void stopScreenShare() {
        // 0. Dừng preview của luồng screen để tránh leak
        mRtcEngine.stopPreview(Constants.VideoSourceType.VIDEO_SOURCE_SCREEN_PRIMARY);
        // 1. Tắt quay màn hình của Agora
        mRtcEngine.stopScreenCapture();

        // 2. Rời luồng phụ
        if (screenShareConnection != null) {
            io.agora.rtc2.RtcEngineEx engineEx = (io.agora.rtc2.RtcEngineEx) mRtcEngine;
            engineEx.leaveChannelEx(screenShareConnection);
            screenShareConnection = null;
        }

        // 3. Xóa ô màn hình khỏi UI
        for (int i = 0; i < participantList.size(); i++) {
            if (participantList.get(i).name.equals("Màn hình của tôi")) {
                participantList.remove(i);
                updateGridLayout();
                adapter.notifyItemRemoved(i);
                updateParticipantCount();
                break;
            }
        }

        // 4. Dừng cái Service của bạn lại để tắt Notification
        Intent serviceIntent = new Intent(this, MyScreenShareService.class);
        stopService(serviceIntent);

        isSharingScreen = false;
        updateShareButtonUI();
    }

    private void updateShareButtonUI() {
        ImageButton btnShareScreen = findViewById(R.id.btnShareScreen);
        if (btnShareScreen != null) {
            if (isSharingScreen) {
                btnShareScreen.setBackgroundResource(R.drawable.bg_call_tool_active);
                btnShareScreen.setImageResource(R.drawable.ic_screen_share_on);
                try {
                    int color = android.graphics.Color.parseColor(serverColor);
                    btnShareScreen.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                    btnShareScreen.setColorFilter(android.graphics.Color.WHITE);
                } catch (Exception e) {}
            } else {
                btnShareScreen.setBackgroundTintList(null);
                btnShareScreen.setBackgroundResource(R.drawable.bg_call_tool_inactive);
                btnShareScreen.setImageResource(R.drawable.ic_screen_share_off);
                btnShareScreen.setColorFilter(android.graphics.Color.parseColor("#B0B0C8"));
            }
        }
    }

    private void updateMuteButtonUI(ImageButton btnMute, boolean isMuted) {
        if (isMuted) {
            btnMute.setBackgroundTintList(null);
            btnMute.setBackgroundResource(R.drawable.bg_call_tool_inactive);
            btnMute.setImageResource(R.drawable.ic_mic_off);
            btnMute.setColorFilter(android.graphics.Color.parseColor("#B0B0C8"));
        } else {
            btnMute.setBackgroundResource(R.drawable.bg_call_tool_active);
            btnMute.setImageResource(R.drawable.ic_mic_on);
            try {
                int color = android.graphics.Color.parseColor(serverColor);
                btnMute.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                btnMute.setColorFilter(android.graphics.Color.WHITE);
            } catch (Exception e) {}
        }
    }

    private void updateVideoButtonUI(ImageButton btnVideo, boolean isVideoOff) {
        if (isVideoOff) {
            btnVideo.setBackgroundTintList(null);
            btnVideo.setBackgroundResource(R.drawable.bg_call_tool_inactive);
            btnVideo.setImageResource(R.drawable.ic_videocam_off);
            btnVideo.setColorFilter(android.graphics.Color.parseColor("#B0B0C8"));
        } else {
            btnVideo.setBackgroundResource(R.drawable.bg_call_tool_active);
            btnVideo.setImageResource(R.drawable.ic_videocam_on);
            try {
                int color = android.graphics.Color.parseColor(serverColor);
                btnVideo.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                btnVideo.setColorFilter(android.graphics.Color.WHITE);
            } catch (Exception e) {}
        }
    }

    private void setupControls() {
        ImageButton btnMute = findViewById(R.id.btnMute);
        ImageButton btnToggleVideo = findViewById(R.id.btnToggleVideo);
        ImageButton btnEndCall = findViewById(R.id.btnEndCall);

        // Trạng thái ban đầu: Camera TẮT, Mic BẬT
        updateVideoButtonUI(btnToggleVideo, true);   // cam tắt ban đầu
        updateMuteButtonUI(btnMute, false);           // mic bật ban đầu

        btnMute.setOnClickListener(v -> {
            boolean isMuted = !v.isSelected();
            v.setSelected(isMuted);
            mRtcEngine.muteLocalAudioStream(isMuted);
            updateMuteButtonUI(btnMute, isMuted);
            if (!participantList.isEmpty()) {
                participantList.get(0).isMuted = isMuted;
                adapter.notifyItemChanged(0, "state_update");
            }
        });

        btnToggleVideo.setOnClickListener(v -> {
            boolean isVideoOff = !v.isSelected();
            v.setSelected(isVideoOff);
            mRtcEngine.muteLocalVideoStream(isVideoOff);
            updateVideoButtonUI(btnToggleVideo, isVideoOff);
            if (!participantList.isEmpty()) {
                participantList.get(0).isVideoOff = isVideoOff;
                adapter.notifyItemChanged(0, "state_update");
            }
        });

        btnEndCall.setOnClickListener(v -> finish());

        ImageButton btnShareScreen = findViewById(R.id.btnShareScreen);
        if (btnShareScreen != null) {
            btnShareScreen.setOnClickListener(v -> {
                if (!isSharingScreen) {
                    // 1. Chạy Service LÊN TRƯỚC làm "lá chắn" bảo mật
                    Intent intent = new Intent(CallDetailActivity.this, MyScreenShareService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent);
                    } else {
                        startService(intent);
                    }

                    // 2. Gọi Agora mở Popup xin quyền quay màn hình
                    io.agora.rtc2.ScreenCaptureParameters params = new io.agora.rtc2.ScreenCaptureParameters();
                    params.captureVideo = true;
                    params.captureAudio = true;
                    params.videoCaptureParameters.width = 720;
                    params.videoCaptureParameters.height = 1280;

                    mRtcEngine.startScreenCapture(params);
                    // Lưu ý: Tuyệt đối dừng ở đây, không gọi setupScreenShareExConnection() vội.
                } else {
                    stopScreenShare();
                }
            });
        }
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
