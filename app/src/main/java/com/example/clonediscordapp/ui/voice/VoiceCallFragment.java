package com.example.clonediscordapp.ui.voice;

import static android.content.Context.MEDIA_PROJECTION_SERVICE;
import static androidx.core.content.ContextCompat.getSystemService;
import static androidx.core.content.ContextCompat.startForegroundService;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.clonediscordapp.R;
import com.example.clonediscordapp.data.model.Participant;
import com.example.clonediscordapp.databinding.FragmentVoiceCallBinding;
import com.example.clonediscordapp.ui.adapters.VoiceParticipantAdapter;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.RtcEngineEx;
import io.agora.rtc2.RtcConnection;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.ScreenCaptureParameters;
import io.agora.rtc2.video.VideoEncoderConfiguration;

public class VoiceCallFragment extends Fragment {

    private static final String TAG = "VoiceCallFragment";
    private final String appId = "54381d815bd74264923f243e5a1f0660";

    private FragmentVoiceCallBinding binding;
    private RtcEngine mRtcEngine;
    private static final int SCREEN_SHARE_REQUEST_CODE = 1001;
    private int uid;
    private String channelName = "s1";
    private String channelTitle = "Fantasy World";

    private RtcConnection screenShareConnection;
    private final int SCREEN_SHARE_UID_OFFSET = 1000;
    private boolean isSharingScreen = false;
    private MediaProjectionManager mProjectionManager;

    private List<Participant> participantList = new ArrayList<>();
    private VoiceParticipantAdapter adapter;

    private boolean isMuted = false;
    private boolean isDeafened = false;
    private boolean isVideoOn = false;
    private boolean isSpeakerOn = true;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                Boolean recordAudioGranted = result.getOrDefault(Manifest.permission.RECORD_AUDIO, false);
                Boolean cameraGranted = result.getOrDefault(Manifest.permission.CAMERA, false);
                Boolean readPhoneStateGranted = result.getOrDefault(Manifest.permission.READ_PHONE_STATE, false);
                if (recordAudioGranted && cameraGranted && readPhoneStateGranted) {
                    initAgoraAndJoinChannel();
                } else {
                    if (getContext() != null) {
                        Toast.makeText(requireContext(), "Microphone, Camera and Phone permissions are required for voice calls!", Toast.LENGTH_LONG).show();
                        Navigation.findNavController(requireView()).navigateUp();
                    }
                }
            }
    );


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentVoiceCallBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Fetch passed arguments
        if (getArguments() != null) {
            channelName = getArguments().getString("SERVER_ID", "s1");
            channelTitle = getArguments().getString("SERVER_NAME", "Fantasy World");
        }

        binding.tvVoiceChannelTitle.setText(channelTitle);

        // Derive unique UID from firebase email hash
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            uid = FirebaseAuth.getInstance().getCurrentUser().getUid().hashCode();
        } else {
            uid = (int) (Math.random() * 10000);
        }

        // Initialize Recycler Adapter with list reference
        adapter = new VoiceParticipantAdapter(requireContext(), participantList, null);
        binding.rvVoiceParticipants.setAdapter(adapter);
        updateGridLayout();

        // Check & request runtime calling permissions giống Backend (Thêm READ_PHONE_STATE)
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            initAgoraAndJoinChannel();
        } else {
            requestPermissionLauncher.launch(new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_PHONE_STATE
            });
        }

        setupControls();
    }

    private void initAgoraAndJoinChannel() {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = requireActivity().getBaseContext();
            config.mAppId = appId;
            config.mEventHandler = mRtcEventHandler;
            mRtcEngine = RtcEngine.create(config);

            // Configure audio and video pipelines giống Backend
            mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION);
            mRtcEngine.enableAudio();
            mRtcEngine.setDefaultAudioRoutetoSpeakerphone(false); // Mặc định tai nghe, tránh echo giống Backend

            // Audio quality adjustments (echo & noise cancel) giống Backend
            mRtcEngine.setParameters("{\"che.audio.enable.aec\":true}");
            mRtcEngine.setParameters("{\"che.audio.enable.ans\":true}");
            mRtcEngine.setParameters("{\"che.audio.enable.agc\":true}");

            mRtcEngine.enableVideo();
            mRtcEngine.muteLocalVideoStream(true); // start camera disabled by default
            mRtcEngine.enableAudioVolumeIndication(200, 3, true);
            // Ép SDK sử dụng giao thức kết nối mạnh nhất giống Backend
            mRtcEngine.setParameters("{\"rtc.force_unified_communication_mode\":true}");

            // Bind RtcEngine to Adapter so it can attach surface views
            adapter.setRtcEngine(mRtcEngine);

            // Join Agora voice channel
            int res = mRtcEngine.joinChannel(null, channelName, "", uid);
            if (res != 0) {
                Log.e(TAG, "Agora joinChannel failed with error: " + res);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Agora RTC engine: " + e.getMessage());
        }
    }

    private void updateGridLayout() {
        int count = participantList.size();
        int spanCount = (count <= 1) ? 1 : 2; // Maximum 2 columns for premium layout styling
        binding.rvVoiceParticipants.setLayoutManager(new GridLayoutManager(requireContext(), spanCount));
    }

    private final IRtcEngineEventHandler mRtcEventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onUserJoined(int remoteUid, int elapsed) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (remoteUid == (uid + SCREEN_SHARE_UID_OFFSET)) {
                    // Suppress drawing own remote screen share frame
                    mRtcEngine.muteRemoteVideoStream(remoteUid, true);
                    mRtcEngine.muteRemoteAudioStream(remoteUid, true);
                    return;
                }

                Toast.makeText(requireContext(), "User " + remoteUid + " joined the voice channel!", Toast.LENGTH_SHORT).show();
                Participant newUser = new Participant(remoteUid, "User " + remoteUid);
                newUser.isVideoOff = true;
                participantList.add(newUser);
                updateGridLayout();
                adapter.notifyItemInserted(participantList.size() - 1);
            });
        }

        @Override
        public void onJoinChannelSuccess(String channel, int localUid, int elapsed) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                boolean exists = false;
                for (Participant p : participantList) {
                    if (p.uid == localUid) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    Participant me = new Participant(localUid, "Me (" + localUid + ")");
                    me.isVideoOff = true;
                    me.isMuted = isMuted;
                    participantList.add(0, me);
                    updateGridLayout();
                    adapter.notifyItemInserted(0);
                }
            });
        }

        @Override
        public void onUserOffline(int remoteUid, int reason) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (remoteUid == (uid + SCREEN_SHARE_UID_OFFSET)) {
                    return;
                }
                for (int i = 0; i < participantList.size(); i++) {
                    if (participantList.get(i).uid == remoteUid) {
                        participantList.remove(i);
                        updateGridLayout();
                        adapter.notifyItemRemoved(i);
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
                        adapter.notifyItemChanged(i, "border_update");
                    }
                }
            });
        }

        @Override
        public void onUserMuteVideo(int remoteUid, boolean muted) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                for (int i = 0; i < participantList.size(); i++) {
                    Participant p = participantList.get(i);
                    if (p.uid == remoteUid) {
                        p.isVideoOff = muted;
                        adapter.notifyItemChanged(i, "state_update");
                        break;
                    }
                }
            });
        }

        @Override
        public void onRemoteVideoStateChanged(int remoteUid, int state, int reason, int elapsed) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                for (int i = 0; i < participantList.size(); i++) {
                    Participant p = participantList.get(i);
                    if (p.uid == remoteUid) {
                        boolean isOff = (state == 0);
                        if (p.isVideoOff != isOff) {
                            p.isVideoOff = isOff;
                            adapter.notifyItemChanged(i, "state_update");
                        }
                        break;
                    }
                }
            });
        }

        @Override
        public void onLocalVideoStateChanged(Constants.VideoSourceType source, int state, int error) {
            super.onLocalVideoStateChanged(source, state, error);
            if (source == io.agora.rtc2.Constants.VideoSourceType.VIDEO_SOURCE_SCREEN_PRIMARY) {
                if (state == io.agora.rtc2.Constants.LOCAL_VIDEO_STREAM_STATE_CAPTURING) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {

                        setupScreenShareExConnection();
                        isSharingScreen = true;
                        updateScreenShareUI();
                    });
                } else if (state == io.agora.rtc2.Constants.LOCAL_VIDEO_STREAM_STATE_FAILED) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Screen sharing cancelled", Toast.LENGTH_SHORT).show();
                        stopScreenShare();
                    });
                }
            }
        }
    };

    private void startScreenShare() {
        if (mRtcEngine == null) return;
        mRtcEngine.muteLocalVideoStream(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            mProjectionManager = (MediaProjectionManager)
                    requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }
        if (mProjectionManager != null) {
            Intent intent = mProjectionManager.createScreenCaptureIntent();
            startActivityForResult(intent, SCREEN_SHARE_REQUEST_CODE);
        }
    }

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
                break;
            }
        }

        // 4. Dừng cái Service của bạn lại để tắt Notification
        Intent serviceIntent = new Intent(requireContext(), MyScreenShareService.class);
        requireContext().stopService(serviceIntent);

        isSharingScreen = false;
        updateScreenShareUI();
    }

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
    }

    private void setupControls() {
        // Toggle Audio output speaker vs earpiece
        binding.btnSpeakerToggle.setOnClickListener(v -> {
            isSpeakerOn = !isSpeakerOn;
            if (mRtcEngine != null) {
                mRtcEngine.setEnableSpeakerphone(isSpeakerOn);
            }
            if (isSpeakerOn) {
                binding.btnSpeakerToggle.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
                Toast.makeText(requireContext(), "Audio output: Speakerphone", Toast.LENGTH_SHORT).show();
            } else {
                binding.btnSpeakerToggle.setImageTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.discord_text_muted, null)));
                Toast.makeText(requireContext(), "Audio output: Earpiece", Toast.LENGTH_SHORT).show();
            }
        });

        // Microphone mute toggle
        binding.btnMute.setOnClickListener(v -> {
            isMuted = !isMuted;
            if (mRtcEngine != null) {
                mRtcEngine.muteLocalAudioStream(isMuted);
            }
            updateMuteUI();
        });

        // Deafen toggle (mutes both incoming voice and outgoing micro)
        binding.btnDeafen.setOnClickListener(v -> {
            isDeafened = !isDeafened;
            if (isDeafened) {
                isMuted = true;
                if (mRtcEngine != null) {
                    mRtcEngine.muteAllRemoteAudioStreams(true);
                    mRtcEngine.muteLocalAudioStream(true);
                }
            } else {
                isMuted = false;
                if (mRtcEngine != null) {
                    mRtcEngine.muteAllRemoteAudioStreams(false);
                    mRtcEngine.muteLocalAudioStream(false);
                }
            }
            updateMuteUI();
            updateDeafenUI();
        });

        // Local Camera Toggle giống Backend (Hoạt động hoàn toàn độc lập với Screen Share)
        binding.btnVideo.setOnClickListener(v -> {
            isVideoOn = !isVideoOn;
            if (mRtcEngine != null) {
                mRtcEngine.muteLocalVideoStream(!isVideoOn);
            }
            updateCameraUI();
        });

        // Screen share toggle
        binding.btnScreenShare.setOnClickListener(v -> {
            if (!isSharingScreen) {
                Intent intent = new Intent(requireContext(), MyScreenShareService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireContext().startForegroundService(intent);
                } else {
                    requireContext().startService(intent);
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

        binding.btnMinimize.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.btnEndCall.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        // Initial UI styling configs
        updateMuteUI();
        updateDeafenUI();
        updateCameraUI();
        updateScreenShareUI();
    }

    private void updateMuteUI() {
        if (!participantList.isEmpty()) {
            participantList.get(0).isMuted = isMuted;
            adapter.notifyItemChanged(0, "state_update");
        }

        if (isMuted) {
            binding.ivMuteIcon.setImageResource(R.drawable.ic_voice_mic_off);
            binding.ivMuteIcon.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.RED));
            binding.btnMute.setBackgroundResource(R.drawable.bg_circle_elevated);
            binding.btnMute.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
        } else {
            binding.ivMuteIcon.setImageResource(R.drawable.ic_voice_mic);
            binding.ivMuteIcon.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
            binding.btnMute.setBackgroundResource(R.drawable.bg_circle_dark);
            binding.btnMute.setBackgroundTintList(null);
        }
    }

    private void updateDeafenUI() {
        if (isDeafened) {
            binding.ivDeafenIcon.setImageResource(R.drawable.ic_voice_deafen_off);
            binding.ivDeafenIcon.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.RED));
            binding.btnDeafen.setBackgroundResource(R.drawable.bg_circle_elevated);
            binding.btnDeafen.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
        } else {
            binding.ivDeafenIcon.setImageResource(R.drawable.ic_voice_deafen);
            binding.ivDeafenIcon.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
            binding.btnDeafen.setBackgroundResource(R.drawable.bg_circle_dark);
            binding.btnDeafen.setBackgroundTintList(null);
        }
    }

    private void updateCameraUI() {
        if (!participantList.isEmpty()) {
            participantList.get(0).isVideoOff = !isVideoOn;
            adapter.notifyItemChanged(0, "state_update");
        }

        if (isVideoOn) {
            binding.ivVideoIcon.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
            binding.btnVideo.setBackgroundResource(R.drawable.bg_circle_dark);
            binding.btnVideo.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.discord_blurple, null)));
        } else {
            binding.ivVideoIcon.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
            binding.btnVideo.setBackgroundResource(R.drawable.bg_circle_dark);
            binding.btnVideo.setBackgroundTintList(null);
        }
    }

    private void updateScreenShareUI() {
        if (isSharingScreen) {
            binding.ivScreenShareIcon.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
            binding.btnScreenShare.setBackgroundResource(R.drawable.bg_circle_dark);
            binding.btnScreenShare.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.discord_green, null)));
        } else {
            binding.ivScreenShareIcon.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
            binding.btnScreenShare.setBackgroundResource(R.drawable.bg_circle_dark);
            binding.btnScreenShare.setBackgroundTintList(null);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isSharingScreen) {
            stopScreenShare();
        }
        if (mRtcEngine != null) {
            mRtcEngine.leaveChannel();
            RtcEngine.destroy();
            mRtcEngine = null;
        }
        binding = null;
    }
}
