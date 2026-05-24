package com.example.se114_callingsystem.features.call;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.databinding.FragmentCallVoiceBinding;
import com.example.se114_callingsystem.core.model.Participant;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.example.se114_callingsystem.core.service.MyScreenShareService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcConnection;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import java.util.ArrayList;
import java.util.List;

public class VoiceCallFragment extends Fragment {

    private static final String TAG = "VoiceCallFragment";

    private final String appId = "54381d815bd74264923f243e5a1f0660";
    private RtcEngine mRtcEngine;
    private int uid;

    private String channelName = "TestChannel";
    private RtcConnection screenShareConnection;
    private final int SCREEN_SHARE_UID_OFFSET = 1000;
    private boolean isSharingScreen = false;
    private boolean isUiVisible = true;

    private FragmentCallVoiceBinding binding;
    private ParticipantAdapter adapter;
    private List<Participant> participantList = new ArrayList<>();

    private static final int PERMISSION_REQ_ID = 22;
    private static final String[] REQUESTED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_PHONE_STATE
    };

    private String serverColor = "#5865F2";
    private String serverId;
    private List<ServerMember> serverMembers = new ArrayList<>();
    private ListenerRegistration membersListener;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            uid = FirebaseAuth.getInstance().getCurrentUser().getUid().hashCode();
        } else {
            uid = ("GUEST_" + System.currentTimeMillis()).hashCode();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCallVoiceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Fetch Bundle arguments
        if (getArguments() != null) {
            serverColor = getArguments().getString("SERVER_COLOR", "#5865F2");
            serverId = getArguments().getString("SERVER_ID");
            String passedChannel = getArguments().getString("CALL_CHANNEL_NAME");
            if (passedChannel != null) {
                channelName = passedChannel;
                binding.tvCallChannelName.setText(channelName);
            }
        }

        setupServerMembersListener();
        setupTapToHide();

        binding.btnMinimize.setOnClickListener(v -> {
            Navigation.findNavController(v).popBackStack();
        });

        if (checkPermissions()) {
            initAgoraAndJoinChannel();
        } else {
            requestPermissions(REQUESTED_PERMISSIONS, PERMISSION_REQ_ID);
        }
    }

    private boolean checkPermissions() {
        for (String permission : REQUESTED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_ID) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                initAgoraAndJoinChannel();
            } else {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Permissions denied.", Toast.LENGTH_SHORT).show();
                }
                Navigation.findNavController(requireView()).popBackStack();
            }
        }
    }

    private void initAgoraAndJoinChannel() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = requireContext().getApplicationContext();
            config.mAppId = appId;
            config.mEventHandler = mRtcEventHandler;
            mRtcEngine = RtcEngine.create(config);

            mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION);
            mRtcEngine.enableAudio();
            mRtcEngine.setDefaultAudioRoutetoSpeakerphone(false);
            mRtcEngine.setParameters("{\"che.audio.enable.aec\":true}");
            mRtcEngine.setParameters("{\"che.audio.enable.ans\":true}");
            mRtcEngine.setParameters("{\"che.audio.enable.agc\":true}");

            mRtcEngine.enableVideo();
            mRtcEngine.muteLocalVideoStream(true);
            mRtcEngine.enableAudioVolumeIndication(200, 3, true);
            mRtcEngine.setParameters("{\"rtc.force_unified_communication_mode\":true}");

            setupRecyclerView();

            int res = mRtcEngine.joinChannel(null, channelName, "", uid);
            setupControls();
            if (res != 0) {
                Log.e(TAG, "Join failed: " + res);
            }
        } catch (Exception e) {
            Log.e(TAG, "Agora initialisation error: " + e.getMessage());
        }
    }

    private void setupRecyclerView() {
        if (getContext() == null || binding == null) return;
        adapter = new ParticipantAdapter(requireContext(), participantList, mRtcEngine);
        binding.rvParticipants.setAdapter(adapter);
        updateGridLayout();
    }

    private void updateGridLayout() {
        if (binding == null) return;
        int count = participantList.size();
        int spanCount = (count <= 2) ? 1 : (count <= 4 ? 2 : 3);

        if (binding.rvParticipants.getLayoutManager() instanceof GridLayoutManager) {
            ((GridLayoutManager) binding.rvParticipants.getLayoutManager()).setSpanCount(spanCount);
        } else {
            GridLayoutManager layoutManager = new GridLayoutManager(getContext(), spanCount);
            binding.rvParticipants.setLayoutManager(layoutManager);
        }
    }

    private void updateParticipantCount() {
        if (binding != null) {
            binding.tvParticipantCount.setText(participantList.size() + " connected");
        }
    }

    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onUserJoined(int userUid, int elapsed) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (userUid == (uid + SCREEN_SHARE_UID_OFFSET)) {
                    mRtcEngine.muteRemoteVideoStream(userUid, true);
                    mRtcEngine.muteRemoteAudioStream(userUid, true);
                    return;
                }
                if (getContext() != null) {
                    Toast.makeText(getContext(), "User " + userUid + " joined!", Toast.LENGTH_SHORT).show();
                }
                Participant newUser = new Participant(userUid, resolveNameForUid(userUid));
                newUser.isVideoOff = true;
                participantList.add(newUser);
                updateGridLayout();
                if (adapter != null) adapter.notifyDataSetChanged();
                updateParticipantCount();
            });
        }

        @Override
        public void onJoinChannelSuccess(String channel, int userUid, int elapsed) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                boolean exists = false;
                for (Participant p : participantList) {
                    if (p.uid == userUid) { exists = true; break; }
                }

                if (!exists) {
                    Participant me = new Participant(userUid, resolveNameForUid(userUid));
                    me.isVideoOff = true;
                    participantList.add(0, me);
                    updateGridLayout();
                    if (adapter != null) adapter.notifyItemInserted(0);
                    updateParticipantCount();
                }
            });
        }

        @Override
        public void onUserOffline(int userUid, int reason) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (userUid == (uid + SCREEN_SHARE_UID_OFFSET)) return;
                for (int i = 0; i < participantList.size(); i++) {
                    if (participantList.get(i).uid == userUid) {
                        participantList.remove(i);
                        updateGridLayout();
                        if (adapter != null) adapter.notifyItemRemoved(i);
                        updateParticipantCount();
                        break;
                    }
                }
            });
        }

        @Override
        public void onAudioVolumeIndication(AudioVolumeInfo[] speakers, int totalVolume) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                List<Integer> activeSpeakers = new ArrayList<>();
                for (AudioVolumeInfo speaker : speakers) {
                    if (speaker.volume > 0) {
                        activeSpeakers.add(speaker.uid);
                    }
                }

                for (int i = 0; i < participantList.size(); i++) {
                    Participant p = participantList.get(i);
                    int checkUid = (i == 0) ? 0 : p.uid;

                    boolean isNowSpeaking = activeSpeakers.contains(checkUid);
                    if (p.isSpeaking != isNowSpeaking) {
                        p.isSpeaking = isNowSpeaking;
                        if (adapter != null) adapter.notifyItemChanged(i, "border_update");
                    }
                }
            });
        }

        @Override
        public void onUserMuteVideo(int userUid, boolean muted) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                for (int i = 0; i < participantList.size(); i++) {
                    if (participantList.get(i).uid == userUid) {
                        participantList.get(i).isVideoOff = muted;
                        if (adapter != null) adapter.notifyItemChanged(i, "state_update");
                        break;
                    }
                }
            });
        }

        @Override
        public void onRemoteVideoStateChanged(int userUid, int state, int reason, int elapsed) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                for (int i = 0; i < participantList.size(); i++) {
                    if (participantList.get(i).uid == userUid) {
                        boolean isOff = (state == 0);
                        if (participantList.get(i).isVideoOff != isOff) {
                            participantList.get(i).isVideoOff = isOff;
                            if (adapter != null) adapter.notifyItemChanged(i, "state_update");
                        }
                        break;
                    }
                }
            });
        }

        @Override
        public void onLocalVideoStateChanged(io.agora.rtc2.Constants.VideoSourceType source, int state, int error) {
            super.onLocalVideoStateChanged(source, state, error);
            if (source == io.agora.rtc2.Constants.VideoSourceType.VIDEO_SOURCE_SCREEN_PRIMARY && getActivity() != null) {
                if (state == io.agora.rtc2.Constants.LOCAL_VIDEO_STREAM_STATE_CAPTURING) {
                    getActivity().runOnUiThread(() -> {
                        setupScreenShareExConnection();
                        isSharingScreen = true;
                        updateShareButtonUI();
                    });
                } else if (state == io.agora.rtc2.Constants.LOCAL_VIDEO_STREAM_STATE_FAILED) {
                    getActivity().runOnUiThread(() -> {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Screen sharing cancelled.", Toast.LENGTH_SHORT).show();
                        }
                        stopScreenShare();
                    });
                }
            }
        }
    };

    private void setupScreenShareExConnection() {
        if (mRtcEngine == null) return;
        io.agora.rtc2.RtcEngineEx engineEx = (io.agora.rtc2.RtcEngineEx) mRtcEngine;
        screenShareConnection = new io.agora.rtc2.RtcConnection();
        screenShareConnection.channelId = channelName;
        screenShareConnection.localUid = uid + SCREEN_SHARE_UID_OFFSET;

        io.agora.rtc2.ChannelMediaOptions options = new io.agora.rtc2.ChannelMediaOptions();
        options.publishCameraTrack = false;
        options.publishMicrophoneTrack = false;
        options.publishScreenCaptureVideo = true;
        options.publishScreenCaptureAudio = true;
        options.clientRoleType = io.agora.rtc2.Constants.CLIENT_ROLE_BROADCASTER;

        engineEx.joinChannelEx(null, screenShareConnection, options, new io.agora.rtc2.IRtcEngineEventHandler() {});

        Participant myScreen = new Participant(screenShareConnection.localUid, resolveNameForUid(screenShareConnection.localUid));
        myScreen.isVideoOff = false;
        participantList.add(myScreen);
        updateGridLayout();
        if (adapter != null) adapter.notifyItemInserted(participantList.size() - 1);
        updateParticipantCount();
    }

    private void stopScreenShare() {
        if (mRtcEngine != null) {
            mRtcEngine.stopPreview(Constants.VideoSourceType.VIDEO_SOURCE_SCREEN_PRIMARY);
            mRtcEngine.stopScreenCapture();
        }

        if (screenShareConnection != null && mRtcEngine != null) {
            io.agora.rtc2.RtcEngineEx engineEx = (io.agora.rtc2.RtcEngineEx) mRtcEngine;
            engineEx.leaveChannelEx(screenShareConnection);
            screenShareConnection = null;
        }

        for (int i = 0; i < participantList.size(); i++) {
            if (participantList.get(i).name.equals("Màn hình của tôi")) {
                participantList.remove(i);
                updateGridLayout();
                if (adapter != null) adapter.notifyItemRemoved(i);
                updateParticipantCount();
                break;
            }
        }

        if (getContext() != null) {
            Intent serviceIntent = new Intent(getContext(), MyScreenShareService.class);
            requireContext().stopService(serviceIntent);
        }

        isSharingScreen = false;
        updateShareButtonUI();
    }

    private void updateShareButtonUI() {
        if (binding == null) return;
        if (isSharingScreen) {
            binding.btnShareScreen.setImageResource(R.drawable.ic_screen_share_on);
            binding.btnShareScreen.setBackgroundColor(Color.parseColor(serverColor));
            binding.btnShareScreen.setColorFilter(Color.WHITE);
        } else {
            binding.btnShareScreen.setImageResource(R.drawable.ic_screen_share_off);
            binding.btnShareScreen.setBackgroundResource(R.drawable.bg_chat_input);
            binding.btnShareScreen.setBackgroundTintList(null);
            binding.btnShareScreen.setColorFilter(Color.parseColor("#B5BAC1"));
        }
    }

    private void updateMuteButtonUI(boolean isMuted) {
        if (binding == null) return;
        if (isMuted) {
            binding.btnMute.setImageResource(R.drawable.ic_mic_off);
            binding.btnMute.setBackgroundResource(R.drawable.bg_chat_input);
            binding.btnMute.setBackgroundTintList(null);
            binding.btnMute.setColorFilter(Color.parseColor("#B5BAC1"));
        } else {
            binding.btnMute.setImageResource(R.drawable.ic_mic_on);
            binding.btnMute.setBackgroundColor(Color.parseColor(serverColor));
            binding.btnMute.setColorFilter(Color.WHITE);
        }
    }

    private void updateVideoButtonUI(boolean isVideoOff) {
        if (binding == null) return;
        if (isVideoOff) {
            binding.btnToggleVideo.setImageResource(R.drawable.ic_videocam_off);
            binding.btnToggleVideo.setBackgroundResource(R.drawable.bg_chat_input);
            binding.btnToggleVideo.setBackgroundTintList(null);
            binding.btnToggleVideo.setColorFilter(Color.parseColor("#B5BAC1"));
        } else {
            binding.btnToggleVideo.setImageResource(R.drawable.ic_videocam_on);
            binding.btnToggleVideo.setBackgroundColor(Color.parseColor(serverColor));
            binding.btnToggleVideo.setColorFilter(Color.WHITE);
        }
    }

    private void setupControls() {
        updateVideoButtonUI(true);
        updateMuteButtonUI(false);

        binding.btnMute.setOnClickListener(v -> {
            boolean isMuted = !v.isSelected();
            v.setSelected(isMuted);
            mRtcEngine.muteLocalAudioStream(isMuted);
            updateMuteButtonUI(isMuted);
            if (!participantList.isEmpty()) {
                participantList.get(0).isMuted = isMuted;
                if (adapter != null) adapter.notifyItemChanged(0, "state_update");
            }
        });

        binding.btnToggleVideo.setOnClickListener(v -> {
            boolean isVideoOff = !v.isSelected();
            v.setSelected(isVideoOff);
            mRtcEngine.muteLocalVideoStream(isVideoOff);
            updateVideoButtonUI(isVideoOff);
            if (!participantList.isEmpty()) {
                participantList.get(0).isVideoOff = isVideoOff;
                if (adapter != null) adapter.notifyItemChanged(0, "state_update");
            }
        });

        binding.btnEndCall.setOnClickListener(v -> {
            Navigation.findNavController(v).popBackStack();
        });

        binding.btnShareScreen.setOnClickListener(v -> {
            if (!isSharingScreen) {
                if (getContext() != null) {
                    Intent intent = new Intent(getContext(), MyScreenShareService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        requireContext().startForegroundService(intent);
                    } else {
                        requireContext().startService(intent);
                    }
                }

                io.agora.rtc2.ScreenCaptureParameters params = new io.agora.rtc2.ScreenCaptureParameters();
                params.captureVideo = true;
                params.captureAudio = true;
                params.videoCaptureParameters.width = 720;
                params.videoCaptureParameters.height = 1280;

                mRtcEngine.startScreenCapture(params);
            } else {
                stopScreenShare();
            }
        });
    }

    private void setupTapToHide() {
        GestureDetector gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                if (binding == null) return false;
                isUiVisible = !isUiVisible;
                int visibility = isUiVisible ? View.VISIBLE : View.GONE;
                binding.callHeader.setVisibility(visibility);
                binding.controlPanel.setVisibility(visibility);
                return true;
            }
        });

        binding.rvParticipants.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                gestureDetector.onTouchEvent(e);
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (membersListener != null) {
            membersListener.remove();
        }
        if (mRtcEngine != null) {
            mRtcEngine.leaveChannel();
            RtcEngine.destroy();
            mRtcEngine = null;
        }
    }

    private void setupServerMembersListener() {
        if (serverId == null || serverId.isEmpty()) return;
        
        FirebaseFirestore dbFS = FirebaseFirestore.getInstance();
        membersListener = dbFS.collection("servers").document(serverId).collection("members")
            .addSnapshotListener((snapshots, error) -> {
                if (error != null) return;
                if (snapshots != null && binding != null) {
                    serverMembers.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        ServerMember m = doc.toObject(ServerMember.class);
                        if (m != null) {
                            serverMembers.add(m);
                        }
                    }
                    resolveParticipantNames();
                }
            });
    }

    private String resolveNameForUid(int agoraUid) {
        if (agoraUid == this.uid + SCREEN_SHARE_UID_OFFSET) {
            return "Màn hình của tôi";
        }
        
        boolean isScreenShare = false;
        int targetUid = agoraUid;
        
        if (serverMembers != null) {
            for (ServerMember m : serverMembers) {
                if (m.getUserId() != null) {
                    int memberHash = m.getUserId().hashCode();
                    if (memberHash + SCREEN_SHARE_UID_OFFSET == agoraUid) {
                        isScreenShare = true;
                        targetUid = memberHash;
                        break;
                    }
                }
            }
        }

        if (targetUid == this.uid) {
            String name = "Me";
            if (serverMembers != null) {
                for (ServerMember m : serverMembers) {
                    if (m.getUserId() != null && m.getUserId().hashCode() == targetUid) {
                        String disp = m.getNickname();
                        if (disp == null || disp.trim().isEmpty()) {
                            disp = m.getUserName();
                        }
                        if (disp != null && !disp.trim().isEmpty()) {
                            name = disp;
                        }
                        break;
                    }
                }
            }
            return isScreenShare ? "Màn hình của " + name : name + " (Me)";
        }

        if (serverMembers != null) {
            for (ServerMember m : serverMembers) {
                if (m.getUserId() != null && m.getUserId().hashCode() == targetUid) {
                    String name = m.getNickname();
                    if (name == null || name.trim().isEmpty()) {
                        name = m.getUserName();
                    }
                    if (name != null && !name.trim().isEmpty()) {
                        return isScreenShare ? "Màn hình của " + name : name;
                    }
                }
            }
        }

        if (isScreenShare) {
            return "Màn hình của User " + targetUid;
        }
        return "User " + agoraUid;
    }

    private void resolveParticipantNames() {
        if (participantList == null || adapter == null) return;
        for (Participant p : participantList) {
            p.name = resolveNameForUid(p.uid);
        }
        adapter.notifyDataSetChanged();
    }
}

