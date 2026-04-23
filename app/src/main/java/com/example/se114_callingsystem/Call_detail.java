package com.example.se114_callingsystem;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;

public class Call_detail extends AppCompatActivity {
    private final String appId = "f2aab838c1c24eb8b03ae0129d1044ed";
    private RtcEngine mRtcEngine;

    private String channelName = "TestChannel";
    private boolean isUiVisible = true;

    // UI Elements
    private RecyclerView rvParticipants;
    private LinearLayout callHeader;
    private CardView controlPanel;
    private TextView tvCallChannelName, tvParticipantCount;

    private ParticipantAdapter adapter;
    private List<Participant> participantList = new ArrayList<>();

    // Permissions
    private static final int PERMISSION_REQ_ID = 22;
    private static final String[] REQUESTED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_call_detail);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rvParticipants), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top + 180, systemBars.right, systemBars.bottom + 250);
            return insets;
        });

        initViews();
        setupTapToHide();

        if (checkSelfPermission(REQUESTED_PERMISSIONS[0], PERMISSION_REQ_ID) &&
                checkSelfPermission(REQUESTED_PERMISSIONS[1], PERMISSION_REQ_ID)) {
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

    private void initAgoraAndJoinChannel() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = getBaseContext();
            config.mAppId = appId;
            config.mEventHandler = mRtcEventHandler;
            mRtcEngine = RtcEngine.create(config);

            mRtcEngine.enableVideo();
            mRtcEngine.startPreview();

            // ---> CAMERA OFF BY DEFAULT HERE <---
            mRtcEngine.muteLocalVideoStream(true);

            // Tells Agora to report volume every 200ms
            mRtcEngine.enableAudioVolumeIndication(200, 3, true);

        } catch (Exception e) {
            Toast.makeText(this, "Failed to initialize Agora", Toast.LENGTH_LONG).show();
            return;
        }

        // ---> SET LOCAL USER DATA TO CAMERA OFF <---
        Participant localUser = new Participant(0, "Me");
        localUser.isVideoOff = true;
        participantList.add(localUser);

        setupRecyclerView();
        updateParticipantCount();

        mRtcEngine.joinChannel(null, channelName, "", 0);

        setupControls();
    }

    private void setupRecyclerView() {
        adapter = new ParticipantAdapter(this, participantList, mRtcEngine);
        rvParticipants.setAdapter(adapter);
        updateGridLayout();
    }

    private void updateGridLayout() {
        int count = participantList.size();
        int spanCount;

        if (count <= 2) spanCount = 1;
        else if (count <= 4) spanCount = 2;
        else spanCount = 3;

        GridLayoutManager layoutManager = new GridLayoutManager(this, spanCount);
        rvParticipants.setLayoutManager(layoutManager);
    }

    private void updateParticipantCount() {
        tvParticipantCount.setText(participantList.size() + " participants");
    }

    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onUserJoined(int uid, int elapsed) {
            runOnUiThread(() -> {
                participantList.add(new Participant(uid, "User " + uid));
                updateGridLayout();
                adapter.notifyItemInserted(participantList.size() - 1);
                updateParticipantCount();
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

        // --- TRACK REMOTE USER MUTES ---
        @Override
        public void onUserMuteAudio(int uid, boolean muted) {
            runOnUiThread(() -> {
                for (int i = 0; i < participantList.size(); i++) {
                    if (participantList.get(i).uid == uid) {
                        participantList.get(i).isMuted = muted;
                        adapter.notifyItemChanged(i, "state_update");
                        break;
                    }
                }
            });
        }

        // --- TRACK REMOTE USER CAMERA ---
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
        public void onAudioVolumeIndication(AudioVolumeInfo[] speakers, int totalVolume) {
            runOnUiThread(() -> {
                List<Integer> activeSpeakers = new ArrayList<>();
                for (AudioVolumeInfo speaker : speakers) {
                    // <--- CHANGED FROM > 3 to > 0
                    // This forces the border to trigger on the quietest emulator noises
                    if (speaker.volume > 0) {
                        activeSpeakers.add(speaker.uid);
                    }
                }

                for (int i = 0; i < participantList.size(); i++) {
                    Participant p = participantList.get(i);
                    // In Agora, your local user is always reported as UID 0 in this callback
                    int checkUid = (i == 0) ? 0 : p.uid;

                    boolean isNowSpeaking = activeSpeakers.contains(checkUid);

                    if (p.isSpeaking != isNowSpeaking) {
                        p.isSpeaking = isNowSpeaking;
                        adapter.notifyItemChanged(i, "border_update");
                    }
                }
            });
        }
    };

    private void setupControls() {
        ImageButton btnMute = findViewById(R.id.btnMute);
        ImageButton btnToggleVideo = findViewById(R.id.btnToggleVideo);
        ImageButton btnEndCall = findViewById(R.id.btnEndCall);

        // Make the UI match the default "Camera OFF" state
        btnToggleVideo.setSelected(true);
        btnToggleVideo.setAlpha(0.5f);

        btnMute.setOnClickListener(v -> {
            boolean isMuted = !v.isSelected();
            v.setSelected(isMuted);
            mRtcEngine.muteLocalAudioStream(isMuted);
            v.setAlpha(isMuted ? 0.5f : 1.0f);

            // --- FIXED: Uses state_update payload to prevent freezing ---
            participantList.get(0).isMuted = isMuted;
            adapter.notifyItemChanged(0, "state_update");
        });

        btnToggleVideo.setOnClickListener(v -> {
            boolean isVideoOff = !v.isSelected();
            v.setSelected(isVideoOff);
            mRtcEngine.muteLocalVideoStream(isVideoOff);
            v.setAlpha(isVideoOff ? 0.5f : 1.0f);

            // --- FIXED: Uses state_update payload to prevent freezing ---
            participantList.get(0).isVideoOff = isVideoOff;
            adapter.notifyItemChanged(0, "state_update");
        });

        btnEndCall.setOnClickListener(v -> finish());
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_ID) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initAgoraAndJoinChannel();
            } else {
                Toast.makeText(this, "Camera & Mic permissions required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
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