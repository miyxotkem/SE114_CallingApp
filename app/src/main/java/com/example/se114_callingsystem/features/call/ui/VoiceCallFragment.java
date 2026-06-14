package com.example.se114_callingsystem.features.call.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.FrameLayout;
import io.agora.rtc2.video.VideoCanvas;
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
import com.example.se114_callingsystem.features.call.viewmodel.VoiceCallViewModel;
import androidx.lifecycle.ViewModelProvider;
import dagger.hilt.android.AndroidEntryPoint;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcConnection;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.video.BeautyOptions;
import io.agora.rtc2.video.ColorEnhanceOptions;
import io.agora.rtc2.video.LowLightEnhanceOptions;
import java.util.ArrayList;
import java.util.List;

@AndroidEntryPoint
public class VoiceCallFragment extends Fragment {

    private static final String TAG = "VoiceCallFragment";

    public static io.agora.rtc2.RtcEngine sRtcEngine;
    public static List<com.example.se114_callingsystem.core.model.Participant> sParticipantList = new java.util.ArrayList<>();
    public static String sChannelName;
    public static int sUid;
    public static String sServerColor;
    public static String sServerId;
    public static boolean isMinimized = false;
    public static final androidx.lifecycle.MutableLiveData<Boolean> minimizedCallEvent = new androidx.lifecycle.MutableLiveData<>(false);
    public static String sSelectedFilter = "Normal";
    public static String sSelectedSticker = "None";

    private RtcEngine mRtcEngine;
    private int uid;

    private String channelName = "TestChannel";
    private RtcConnection screenShareConnection;
    private final int SCREEN_SHARE_UID_OFFSET = 1000;
    private boolean isSharingScreen = false;
    private boolean isUiVisible = true;
    private boolean isBeautyEnabled = false;
    private boolean isColorEnhanceEnabled = false;
    private boolean isLowlightEnabled = false;
    private boolean isDeafened = false;

    private FragmentCallVoiceBinding binding;
    private ParticipantAdapter adapter;
    private final List<Participant> participantList = new ArrayList<>();

    private static final int PERMISSION_REQ_ID = 22;
    private static final String[] REQUESTED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_PHONE_STATE
    };

    private String serverColor = "#5865F2";
    private String serverId;
    private final List<ServerMember> serverMembers = new ArrayList<>();
    private VoiceCallViewModel viewModel;

    private androidx.appcompat.app.AlertDialog reconnectDialog;
    private final android.os.Handler reconnectHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable reconnectRunnable = () -> {
        if (getContext() != null) {
            Toast.makeText(getContext(), "Không thể kết nối lại. Đang thoát cuộc gọi.", Toast.LENGTH_SHORT).show();
        }
        leaveAndExit();
    };

    private final android.content.BroadcastReceiver serviceReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null || binding == null) return;
            String action = intent.getAction();
            if (action.equals(com.example.se114_callingsystem.features.call.data.CallForegroundService.BROADCAST_HANGUP)) {
                leaveAndExit();
            } else if (action.equals(com.example.se114_callingsystem.features.call.data.CallForegroundService.BROADCAST_MUTE_TOGGLE)) {
                boolean isMuted = intent.getBooleanExtra("IS_MUTED", false);
                binding.btnMute.setSelected(isMuted);
                updateMuteButtonUI(isMuted);
                if (!participantList.isEmpty()) {
                    participantList.get(0).isMuted = isMuted;
                    if (adapter != null) adapter.notifyItemChanged(0, "state_update");
                }
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(VoiceCallViewModel.class);

        if (viewModel.getCurrentUserId() != null) {
            uid = viewModel.getCurrentUserId().hashCode() & 0x7FFFFFFF;
        } else {
            uid = ("GUEST_" + System.currentTimeMillis()).hashCode() & 0x7FFFFFFF;
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

        setupObservers();
        setupTapToHide();

        binding.btnMinimize.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    android.app.PictureInPictureParams params = new android.app.PictureInPictureParams.Builder()
                            .setAspectRatio(new android.util.Rational(3, 4))
                            .build();
                    requireActivity().enterPictureInPictureMode(params);
                } catch (Exception e) {
                    Log.e(TAG, "Error entering PiP mode: " + e.getMessage());
                    requireActivity().moveTaskToBack(true);
                }
            } else {
                requireActivity().moveTaskToBack(true);
            }
        });

        // Đăng ký BroadcastReceiver để đồng bộ từ Service
        IntentFilter filter = new IntentFilter();
        filter.addAction(com.example.se114_callingsystem.features.call.data.CallForegroundService.BROADCAST_HANGUP);
        filter.addAction(com.example.se114_callingsystem.features.call.data.CallForegroundService.BROADCAST_MUTE_TOGGLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(serviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            ContextCompat.registerReceiver(requireContext(), serviceReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        }

        if (checkPermissions()) {
            initAgoraAndJoinChannel();
        } else {
            requestPermissions(REQUESTED_PERMISSIONS, PERMISSION_REQ_ID);
        }

        viewModel.loadServerMembers(serverId);
    }

    private void setupObservers() {
        viewModel.getAgoraToken().observe(getViewLifecycleOwner(), response -> {
            if (response != null) {
                joinChannelWithToken(response.token, response.appId);
            }
        });

        viewModel.getServerMembers().observe(getViewLifecycleOwner(), members -> {
            if (members != null) {
                serverMembers.clear();
                serverMembers.addAll(members);
                resolveParticipantNames();
            }
        });

        viewModel.getOperationStatus().observe(getViewLifecycleOwner(), status -> {
            if (status == null || getContext() == null) return;
            Log.e(TAG, "Operation status error: " + status);
            Toast.makeText(getContext(), status, Toast.LENGTH_SHORT).show();
            viewModel.resetStatus();
        });
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
        if (viewModel.getCurrentUser() == null) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "You must be logged in to join a call.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        viewModel.initCall(channelName, uid);
    }

    private void joinChannelWithToken(String rtcToken, String appId) {
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            config.mContext = requireContext().getApplicationContext();
            config.mAppId = appId;
            config.mEventHandler = mRtcEventHandler;
            config.addExtension("agora_segmentation_extension");
            mRtcEngine = RtcEngine.create(config);
            mRtcEngine.enableExtension("agora_segmentation", "PortraitSegmentation", true);

            mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION);
            mRtcEngine.enableAudio();
            mRtcEngine.enableLocalAudio(true); // Đảm bảo mic được bật để thu âm
            mRtcEngine.setDefaultAudioRoutetoSpeakerphone(true); // Chuyển sang loa ngoài làm mặc định
            mRtcEngine.setParameters("{\"che.audio.enable.aec\":true}");
            mRtcEngine.setParameters("{\"che.audio.enable.ans\":true}");
            mRtcEngine.setParameters("{\"che.audio.enable.agc\":true}");
            mRtcEngine.setParameters("{\"che.audio.enable.ns\":true}");

            mRtcEngine.enableVideo();
            mRtcEngine.muteLocalVideoStream(true);
            mRtcEngine.enableAudioVolumeIndication(200, 3, true);
            mRtcEngine.setParameters("{\"rtc.force_unified_communication_mode\":true}");

            setupRecyclerView();

            // Sử dụng ChannelMediaOptions hiện đại của Agora v4.x để đảm bảo publish mic và camera
            ChannelMediaOptions options = new ChannelMediaOptions();
            options.publishMicrophoneTrack = true;
            options.publishCameraTrack = true;
            options.autoSubscribeAudio = true;
            options.autoSubscribeVideo = true;
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;

            int res = mRtcEngine.joinChannel(rtcToken, channelName, uid, options);
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
        adapter.setLocalUid(uid);
        adapter.setServerMembers(serverMembers);
        adapter.setOnParticipantClickListener(this::showParticipantSettingsDialog);
        binding.rvParticipants.setAdapter(adapter);
        updateGridLayout();
    }

    private void updateGridLayout() {
        if (binding == null) return;
        if (getActivity() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && getActivity().isInPictureInPictureMode()) {
            binding.rvParticipants.setLayoutManager(new GridLayoutManager(getContext(), 1));
            return;
        }
        int count = participantList.size();
        
        // Count video and voice users
        int videoCount = 0;
        for (Participant p : participantList) {
            if (!p.isVideoOff) {
                videoCount++;
            }
        }
        
        GridLayoutManager layoutManager;
        if (videoCount == 0) {
            int spanCount = (count <= 2) ? 1 : 2;
            layoutManager = new GridLayoutManager(getContext(), spanCount);
        } else {
            final int totalVideo = videoCount;
            layoutManager = new GridLayoutManager(getContext(), 2);
            layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (position >= 0 && position < participantList.size()) {
                        Participant p = participantList.get(position);
                        if (!p.isVideoOff) {
                            // Video / Screen Share item
                            if (totalVideo <= 2) {
                                return 2;
                            } else {
                                if (totalVideo % 2 != 0 && position == totalVideo - 1) {
                                    return 2; // Last odd video takes full width
                                }
                                return 1;
                            }
                        } else {
                            // Voice-only item
                            return 1;
                        }
                    }
                    return 1;
                }
            });
        }
        binding.rvParticipants.setLayoutManager(layoutManager);
    }

    private void updateParticipantCount() {
        if (binding != null) {
            int count = participantList.size();
            binding.tvParticipantCount.setText(count + " người tham gia");
            
            if (count == 0) {
                binding.layoutWaiting.setVisibility(View.VISIBLE);
                binding.rvParticipants.setVisibility(View.GONE);
            } else {
                binding.layoutWaiting.setVisibility(View.GONE);
                binding.rvParticipants.setVisibility(View.VISIBLE);
            }
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
                String userName = resolveNameForUid(userUid);
                if (getContext() != null) {
                    Toast.makeText(getContext(), userName + " đã tham gia cuộc gọi!", Toast.LENGTH_SHORT).show();
                }
                Participant newUser = new Participant(userUid, userName);
                newUser.isVideoOff = true;
                participantList.add(newUser);
                sortParticipantList();
                updateGridLayout();
                if (adapter != null) adapter.notifyDataSetChanged();
                updateParticipantCount();
                updatePiPParams();
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
                    sortParticipantList();
                    updateGridLayout();
                    if (adapter != null) adapter.notifyDataSetChanged();
                    updateParticipantCount();
                }

                sRtcEngine = mRtcEngine;
                sParticipantList = participantList;
                sChannelName = channelName;

                // Khởi động Foreground Service khi kết nối thành công
                if (getContext() != null) {
                    Intent serviceIntent = new Intent(getContext(), com.example.se114_callingsystem.features.call.data.CallForegroundService.class);
                    serviceIntent.setAction(com.example.se114_callingsystem.features.call.data.CallForegroundService.ACTION_START);
                    serviceIntent.putExtra("CHANNEL_NAME", channelName);
                    serviceIntent.putExtra("IS_MUTED", binding.btnMute.isSelected());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        requireContext().startForegroundService(serviceIntent);
                    } else {
                        requireContext().startService(serviceIntent);
                    }
                }

                // Update current user's voice channel in Firestore
                viewModel.updateVoiceChannel(channelName);
            });
        }

        @Override
        public void onUserOffline(int userUid, int reason) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (userUid == (uid + SCREEN_SHARE_UID_OFFSET)) return;
                for (int i = 0; i < participantList.size(); i++) {
                    if (participantList.get(i).uid == userUid) {
                        String displayName = participantList.get(i).name;
                        if (getContext() != null) {
                            Toast.makeText(getContext(), displayName + " đã rời cuộc gọi!", Toast.LENGTH_SHORT).show();
                        }
                        participantList.remove(i);
                        sortParticipantList();
                        updateGridLayout();
                        if (adapter != null) adapter.notifyDataSetChanged();
                        updateParticipantCount();
                        updatePiPParams();
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

                boolean speakerChanged = false;
                for (int i = 0; i < participantList.size(); i++) {
                    Participant p = participantList.get(i);
                    boolean isNowSpeaking;
                    if (p.uid == uid) {
                        isNowSpeaking = activeSpeakers.contains(0) || activeSpeakers.contains(uid);
                    } else {
                        isNowSpeaking = activeSpeakers.contains(p.uid);
                    }

                    if (p.isSpeaking != isNowSpeaking) {
                        p.isSpeaking = isNowSpeaking;
                        speakerChanged = true;
                        if (adapter != null && !getActivity().isInPictureInPictureMode()) {
                            adapter.notifyItemChanged(i, "border_update");
                        }
                    }
                }

                if (speakerChanged && adapter != null && getActivity() != null && getActivity().isInPictureInPictureMode()) {
                    adapter.notifyDataSetChanged();
                }
            });
        }

        @Override
        public void onUserMuteAudio(int userUid, boolean muted) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                for (int i = 0; i < participantList.size(); i++) {
                    if (participantList.get(i).uid == userUid) {
                        participantList.get(i).isMuted = muted;
                        if (adapter != null) adapter.notifyItemChanged(i, "state_update");
                        break;
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
                        sortParticipantList();
                        updateGridLayout();
                        if (adapter != null) adapter.notifyDataSetChanged();
                        updatePiPParams();
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
                        boolean isOff = (state == 0 || state == 4);
                        if (participantList.get(i).isVideoOff != isOff) {
                            participantList.get(i).isVideoOff = isOff;
                            sortParticipantList();
                            updateGridLayout();
                            if (adapter != null) adapter.notifyDataSetChanged();
                            updatePiPParams();
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

        @Override
        public void onConnectionStateChanged(int state, int reason) {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                Log.d(TAG, "onConnectionStateChanged: state = " + state + ", reason = " + reason);
                if (state == io.agora.rtc2.Constants.CONNECTION_STATE_RECONNECTING) {
                    showReconnectingUI();
                } else if (state == io.agora.rtc2.Constants.CONNECTION_STATE_CONNECTED) {
                    hideReconnectingUI();
                } else if (state == io.agora.rtc2.Constants.CONNECTION_STATE_FAILED) {
                    hideReconnectingUI();
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Kết nối cuộc gọi thất bại.", Toast.LENGTH_SHORT).show();
                    }
                    leaveAndExit();
                }
            });
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
        myScreen.isSharingScreen = true;
        participantList.add(myScreen);
        sortParticipantList();
        updateGridLayout();
        if (adapter != null) adapter.notifyDataSetChanged();
        updateParticipantCount();
        updatePiPParams();
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
                sortParticipantList();
                updateGridLayout();
                if (adapter != null) adapter.notifyDataSetChanged();
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
        updatePiPParams();
    }

    private int getControlBgColor() {
        if (getContext() != null) {
            return ContextCompat.getColor(requireContext(), R.color.call_control_bg);
        }
        return Color.parseColor("#232428");
    }

    private void updateShareButtonUI() {
        if (binding == null) return;
        if (isSharingScreen) {
            binding.btnShareScreen.setImageResource(R.drawable.ic_screen_share_on);
            binding.btnShareScreen.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(serverColor)));
            binding.btnShareScreen.setColorFilter(Color.WHITE);
        } else {
            binding.btnShareScreen.setImageResource(R.drawable.ic_screen_share_off);
            binding.btnShareScreen.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getControlBgColor()));
            binding.btnShareScreen.setColorFilter(Color.parseColor("#B5BAC1"));
        }
    }

    private void updateMuteButtonUI(boolean isMuted) {
        if (binding == null) return;
        if (isMuted) {
            binding.btnMute.setImageResource(R.drawable.ic_mic_off);
            binding.btnMute.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getControlBgColor()));
            binding.btnMute.setColorFilter(Color.parseColor("#B5BAC1"));
        } else {
            binding.btnMute.setImageResource(R.drawable.ic_mic_on);
            binding.btnMute.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(serverColor)));
            binding.btnMute.setColorFilter(Color.WHITE);
        }
    }

    private void updateVideoButtonUI(boolean isVideoOff) {
        if (binding == null) return;
        if (isVideoOff) {
            binding.btnToggleVideo.setImageResource(R.drawable.ic_videocam_off);
            binding.btnToggleVideo.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getControlBgColor()));
            binding.btnToggleVideo.setColorFilter(Color.parseColor("#B5BAC1"));
        } else {
            binding.btnToggleVideo.setImageResource(R.drawable.ic_videocam_on);
            binding.btnToggleVideo.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(serverColor)));
            binding.btnToggleVideo.setColorFilter(Color.WHITE);
        }
    }

    private void setupControls() {
        updateVideoButtonUI(true);
        updateMuteButtonUI(true);
        binding.btnMute.setSelected(true); // Mic ban đầu tắt
        binding.btnToggleVideo.setSelected(true); // Video ban đầu tắt nên set selected = true để click lần đầu bật lên

        if (mRtcEngine != null) {
            mRtcEngine.muteLocalAudioStream(true);
        }

        binding.btnMoreOptions.setOnClickListener(v -> {
            showVirtualBgDialog();
        });

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
            if (!isVideoOff) {
                mRtcEngine.startPreview();
                binding.btnSwitchCamera.setVisibility(View.VISIBLE);
            } else {
                if (!isSharingScreen) {
                    mRtcEngine.stopPreview();
                }
                binding.btnSwitchCamera.setVisibility(View.GONE);
            }
            updateVideoButtonUI(isVideoOff);
            if (!participantList.isEmpty()) {
                participantList.get(0).isVideoOff = isVideoOff;
                sortParticipantList();
                updateGridLayout();
                if (adapter != null) adapter.notifyDataSetChanged();
            }
        });

        binding.btnSwitchCamera.setOnClickListener(v -> {
            if (mRtcEngine != null) {
                mRtcEngine.switchCamera();
            }
        });

        binding.btnEndCall.setOnClickListener(v -> {
            leaveAndExit();
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
        // Dừng Foreground Service
        if (getContext() != null) {
            Intent serviceIntent = new Intent(getContext(), com.example.se114_callingsystem.features.call.data.CallForegroundService.class);
            serviceIntent.setAction(com.example.se114_callingsystem.features.call.data.CallForegroundService.ACTION_STOP);
            getContext().startService(serviceIntent);
        }

        if (isSharingScreen) {
            stopScreenShare();
        }
        
        if (mRtcEngine != null) {
            mRtcEngine.stopPreview();
            mRtcEngine.removeHandler(mRtcEventHandler);
            mRtcEngine.leaveChannel();
            RtcEngine.destroy();
            mRtcEngine = null;
        }

        sRtcEngine = null;
        if (sParticipantList != null) {
            sParticipantList.clear();
        }
        isMinimized = false;
        minimizedCallEvent.setValue(false);

        // Huỷ đăng ký BroadcastReceiver
        if (getContext() != null) {
            try {
                getContext().unregisterReceiver(serviceReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Receiver unregister error: " + e.getMessage());
            }
        }

        // Clear user's active call channel in Firestore
        viewModel.clearVoiceChannel();

        super.onDestroy();
    }

    private void showParticipantSettingsDialog(Participant participant) {
        if (getContext() == null || mRtcEngine == null) return;

        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = 
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        
        View view = getLayoutInflater().inflate(R.layout.dialog_call_participant_settings, null);
        bottomSheetDialog.setContentView(view);
        
        try {
            View bottomSheet = (View) view.getParent();
            bottomSheet.setBackgroundResource(android.R.color.transparent);
        } catch (Exception e) {
            e.printStackTrace();
        }

        TextView tvTitle = view.findViewById(R.id.tvSettingsTitle);
        tvTitle.setText("Cài đặt: " + participant.name);

        // Map views
        View llViewProfile = view.findViewById(R.id.llViewProfile);
        View llMute = view.findViewById(R.id.llMute);
        TextView tvMuteLabel = view.findViewById(R.id.tvMuteLabel);
        com.google.android.material.checkbox.MaterialCheckBox cbMute = view.findViewById(R.id.cbMute);
        View dividerMute = view.findViewById(R.id.dividerMute);

        View llDeafen = view.findViewById(R.id.llDeafen);
        com.google.android.material.checkbox.MaterialCheckBox cbDeafen = view.findViewById(R.id.cbDeafen);
        View dividerDeafen = view.findViewById(R.id.dividerDeafen);

        View llCamera = view.findViewById(R.id.llCamera);
        TextView tvCameraLabel = view.findViewById(R.id.tvCameraLabel);
        com.google.android.material.checkbox.MaterialCheckBox cbCamera = view.findViewById(R.id.cbCamera);
        View dividerCamera = view.findViewById(R.id.dividerCamera);

        View llPreviewCamera = view.findViewById(R.id.llPreviewCamera);
        View dividerPreviewCamera = view.findViewById(R.id.dividerPreviewCamera);

        View llVoiceSettings = view.findViewById(R.id.llVoiceSettings);

        // Determine if this participant is Me (local user)
        boolean isMe = (participant.uid == this.uid) || participant.name.contains("Me");

        if (isMe) {
            // Options for local user (Me)
            tvMuteLabel.setText("Tắt âm");
            cbMute.setChecked(binding.btnMute.isSelected());
            
            cbDeafen.setChecked(isDeafened);

            tvCameraLabel.setText("Hiển Thị Camera Của Bản Thân");
            cbCamera.setChecked(!participant.isVideoOff);

            llViewProfile.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
                    showUserProfileSheet(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid());
                }
            });

            llMute.setOnClickListener(v -> {
                boolean checked = !cbMute.isChecked();
                cbMute.setChecked(checked);
                mRtcEngine.muteLocalAudioStream(checked);
                binding.btnMute.setSelected(checked);
                updateMuteButtonUI(checked);
                participant.isMuted = checked;
                if (adapter != null) adapter.notifyItemChanged(0, "state_update");
            });

            llDeafen.setOnClickListener(v -> {
                boolean checked = !cbDeafen.isChecked();
                cbDeafen.setChecked(checked);
                isDeafened = checked;
                mRtcEngine.muteAllRemoteAudioStreams(checked);
                Toast.makeText(getContext(), checked ? "Đã tắt tiếng cuộc gọi" : "Đã bật tiếng cuộc gọi", Toast.LENGTH_SHORT).show();
            });

            llCamera.setOnClickListener(v -> {
                boolean checked = !cbCamera.isChecked();
                cbCamera.setChecked(checked);
                boolean isVideoOff = !checked;
                mRtcEngine.muteLocalVideoStream(isVideoOff);
                binding.btnToggleVideo.setSelected(isVideoOff);
                if (!isVideoOff) {
                    mRtcEngine.startPreview();
                    binding.btnSwitchCamera.setVisibility(View.VISIBLE);
                } else {
                    if (!isSharingScreen) {
                        mRtcEngine.stopPreview();
                    }
                    binding.btnSwitchCamera.setVisibility(View.GONE);
                }
                updateVideoButtonUI(isVideoOff);
                participant.isVideoOff = isVideoOff;
                sortParticipantList();
                updateGridLayout();
                if (adapter != null) adapter.notifyDataSetChanged();
            });

            llPreviewCamera.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                showCameraPreviewDialog();
            });

            llVoiceSettings.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                showVirtualBgDialog();
            });

        } else {
            // Options for remote user
            tvMuteLabel.setText("Tắt âm cục bộ");
            cbMute.setChecked(participant.isMutedLocally);

            tvCameraLabel.setText("Ẩn video");
            cbCamera.setChecked(participant.isVideoMutedLocally);

            llDeafen.setVisibility(View.GONE);
            dividerDeafen.setVisibility(View.GONE);
            llPreviewCamera.setVisibility(View.GONE);
            dividerPreviewCamera.setVisibility(View.GONE);
            llVoiceSettings.setVisibility(View.GONE);

            llViewProfile.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                String targetUserId = resolveUserIdForAgoraUid(participant.uid);
                if (targetUserId != null) {
                    showUserProfileSheet(targetUserId);
                } else {
                    Toast.makeText(getContext(), "Không tìm thấy thông tin thành viên", Toast.LENGTH_SHORT).show();
                }
            });

            llMute.setOnClickListener(v -> {
                boolean checked = !cbMute.isChecked();
                cbMute.setChecked(checked);
                participant.isMutedLocally = checked;
                mRtcEngine.muteRemoteAudioStream(participant.uid, checked);
                if (adapter != null) {
                    int idx = participantList.indexOf(participant);
                    if (idx != -1) {
                        adapter.notifyItemChanged(idx, "state_update");
                    }
                }
            });

            llCamera.setOnClickListener(v -> {
                boolean checked = !cbCamera.isChecked();
                cbCamera.setChecked(checked);
                participant.isVideoMutedLocally = checked;
                mRtcEngine.muteRemoteVideoStream(participant.uid, checked);
                sortParticipantList();
                updateGridLayout();
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            });
        }

        bottomSheetDialog.show();
    }

    private String resolveUserIdForAgoraUid(int agoraUid) {
        String currentMyUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
            
        int targetUid = agoraUid;
        if (currentMyUid != null && (currentMyUid.hashCode() & 0x7FFFFFFF) + SCREEN_SHARE_UID_OFFSET == agoraUid) {
            return currentMyUid;
        } else if (currentMyUid != null && (currentMyUid.hashCode() & 0x7FFFFFFF) == agoraUid) {
            return currentMyUid;
        }

        if (serverMembers != null) {
            for (ServerMember m : serverMembers) {
                if (m.getUserId() != null) {
                    int memberHash = m.getUserId().hashCode() & 0x7FFFFFFF;
                    if (memberHash == targetUid || memberHash + SCREEN_SHARE_UID_OFFSET == targetUid) {
                        return m.getUserId();
                    }
                }
            }
        }
        return null;
    }

    private void showUserProfileSheet(String userId) {
        if (getContext() == null) return;
        
        com.google.android.material.bottomsheet.BottomSheetDialog profileSheet = 
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        
        View view = getLayoutInflater().inflate(R.layout.dialog_call_user_profile, null);
        profileSheet.setContentView(view);
        
        try {
            View bottomSheet = (View) view.getParent();
            bottomSheet.setBackgroundResource(android.R.color.transparent);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ImageView ivCoverPhoto = view.findViewById(R.id.ivCoverPhoto);
        com.google.android.material.imageview.ShapeableImageView ivAvatar = view.findViewById(R.id.ivAvatar);
        View viewStatusIndicator = view.findViewById(R.id.viewStatusIndicator);
        TextView tvUsername = view.findViewById(R.id.tvUsername);
        TextView tvUserStatus = view.findViewById(R.id.tvUserStatus);
        TextView tvBio = view.findViewById(R.id.tvBio);
        TextView tvWorkplace = view.findViewById(R.id.tvWorkplace);
        TextView tvHobbies = view.findViewById(R.id.tvHobbies);
        TextView tvDob = view.findViewById(R.id.tvDob);

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users").document(userId).get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists() && getContext() != null) {
                    String username = documentSnapshot.getString("username");
                    String status = documentSnapshot.getString("status");
                    String bio = documentSnapshot.getString("bio");
                    String workplace = documentSnapshot.getString("workplace");
                    String hobbies = documentSnapshot.getString("hobbies");
                    String dob = documentSnapshot.getString("dob");
                    String profilePic = documentSnapshot.getString("profilePic");
                    String coverPic = documentSnapshot.getString("coverPic");
                    String plan = documentSnapshot.getString("plan");

                    tvUsername.setText(username != null ? username : "User");
                    
                    if ("Pro".equals(plan)) {
                        float density = getResources().getDisplayMetrics().density;
                        ivAvatar.setStrokeWidth(3f * density);
                        ivAvatar.setStrokeColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFD700")));
                        int padding = (int)(3 * density);
                        ivAvatar.setPadding(padding, padding, padding, padding);
                    }

                    if (bio != null && !bio.isEmpty()) {
                        tvBio.setText(bio);
                    } else {
                        tvBio.setText("This user has no bio.");
                    }

                    if (workplace != null && !workplace.isEmpty()) {
                        tvWorkplace.setText(workplace);
                    } else {
                        tvWorkplace.setText("Chưa cập nhật");
                    }

                    if (hobbies != null && !hobbies.isEmpty()) {
                        tvHobbies.setText(hobbies);
                    } else {
                        tvHobbies.setText("Trống");
                    }

                    if (dob != null && !dob.isEmpty()) {
                        tvDob.setText(dob);
                    } else {
                        tvDob.setText("Chưa cập nhật");
                    }

                    if (status == null) status = "online";
                    int colorRes = R.color.discord_green;
                    String displayText = "Online";
                    switch (status.toLowerCase()) {
                        case "idle":
                        case "idling":
                            colorRes = R.color.discord_yellow;
                            displayText = "Idle";
                            break;
                        case "dnd":
                        case "do not disturb":
                            colorRes = R.color.discord_red;
                            displayText = "Do Not Disturb";
                            break;
                        case "offline":
                        case "invisible":
                            colorRes = R.color.discord_text_muted;
                            displayText = "Invisible";
                            break;
                        case "sleeping":
                            colorRes = R.color.discord_blurple;
                            displayText = "Sleeping 💤";
                            break;
                        case "eating":
                            colorRes = R.color.discord_blurple;
                            displayText = "Eating 🍕";
                            break;
                        default:
                            if (!status.equalsIgnoreCase("online")) {
                                colorRes = R.color.discord_blurple;
                                displayText = status;
                            }
                            break;
                    }
                    tvUserStatus.setText(displayText);
                    tvUserStatus.setTextColor(getResources().getColor(colorRes));
                    viewStatusIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(colorRes)));

                    try {
                        if (profilePic != null && !profilePic.isEmpty()) {
                            com.bumptech.glide.Glide.with(requireContext()).load(profilePic).placeholder(R.drawable.ic_user).into(ivAvatar);
                        } else {
                            ivAvatar.setImageResource(R.drawable.ic_user);
                        }
                        
                        if (coverPic != null && !coverPic.isEmpty()) {
                            com.bumptech.glide.Glide.with(requireContext()).load(coverPic).into(ivCoverPhoto);
                        } else {
                            ivCoverPhoto.setImageResource(0);
                            ivCoverPhoto.setBackgroundColor(getResources().getColor(R.color.discord_blurple));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

        profileSheet.show();
    }

    private void showCameraPreviewDialog() {
        if (getContext() == null || mRtcEngine == null || participantList.isEmpty()) return;

        com.google.android.material.bottomsheet.BottomSheetDialog previewDialog = 
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        
        View view = getLayoutInflater().inflate(R.layout.dialog_call_camera_preview, null);
        previewDialog.setContentView(view);
        
        try {
            View bottomSheet = (View) view.getParent();
            bottomSheet.setBackgroundResource(android.R.color.transparent);
        } catch (Exception e) {
            e.printStackTrace();
        }

        FrameLayout previewContainer = view.findViewById(R.id.previewContainer);
        com.google.android.material.button.MaterialButton btnStartCamera = view.findViewById(R.id.btnStartCamera);
        com.google.android.material.button.MaterialButton btnCancel = view.findViewById(R.id.btnCancel);

        android.view.TextureView textureView = new android.view.TextureView(requireContext());
        previewContainer.addView(textureView);

        mRtcEngine.setupLocalVideo(new VideoCanvas(textureView, VideoCanvas.RENDER_MODE_HIDDEN, 0));
        mRtcEngine.startPreview();

        btnCancel.setOnClickListener(v -> previewDialog.dismiss());

        btnStartCamera.setOnClickListener(v -> {
            previewDialog.dismiss();
            
            mRtcEngine.muteLocalVideoStream(false);
            binding.btnToggleVideo.setSelected(false);
            mRtcEngine.startPreview();
            binding.btnSwitchCamera.setVisibility(View.VISIBLE);
            
            updateVideoButtonUI(false);
            
            if (!participantList.isEmpty()) {
                participantList.get(0).isVideoOff = false;
                sortParticipantList();
                updateGridLayout();
                if (adapter != null) adapter.notifyDataSetChanged();
            }
        });

        previewDialog.setOnDismissListener(dialog -> {
            if (!participantList.isEmpty() && participantList.get(0).isVideoOff) {
                mRtcEngine.stopPreview();
                mRtcEngine.setupLocalVideo(new VideoCanvas(null, VideoCanvas.RENDER_MODE_HIDDEN, 0));
            } else {
                if (adapter != null) {
                    adapter.notifyItemChanged(0);
                }
            }
        });

        previewDialog.show();
    }

    private void showVirtualBgDialog() {
        if (getContext() == null || mRtcEngine == null) return;
        
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = 
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        
        View view = getLayoutInflater().inflate(R.layout.layout_call_bottom_sheet_virtual_bg, null);
        bottomSheetDialog.setContentView(view);
        
        // Cần set transparent background để thấy được bo góc của layout custom
        try {
            View bottomSheet = (View) view.getParent();
            bottomSheet.setBackgroundResource(android.R.color.transparent);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        view.findViewById(R.id.btnBgNormal).setOnClickListener(v -> {
            mRtcEngine.enableVirtualBackground(false, null, null);
            Toast.makeText(getContext(), "Đã tắt bộ lọc nền", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });
        
        view.findViewById(R.id.btnBgBlur).setOnClickListener(v -> {
            if (mRtcEngine.isFeatureAvailableOnDevice(io.agora.rtc2.Constants.FEATURE_VIDEO_VIRTUAL_BACKGROUND) == false) {
                Toast.makeText(getContext(), "Máy của bạn (hoặc máy ảo) không hỗ trợ tính năng này do phần cứng.", Toast.LENGTH_LONG).show();
                bottomSheetDialog.dismiss();
                return;
            }
            
            io.agora.rtc2.video.VirtualBackgroundSource source = new io.agora.rtc2.video.VirtualBackgroundSource();
            source.backgroundSourceType = io.agora.rtc2.video.VirtualBackgroundSource.BACKGROUND_BLUR;
            source.blurDegree = io.agora.rtc2.video.VirtualBackgroundSource.BLUR_DEGREE_MEDIUM;
            
            io.agora.rtc2.video.SegmentationProperty segProperty = new io.agora.rtc2.video.SegmentationProperty();
            segProperty.modelType = io.agora.rtc2.video.SegmentationProperty.SEG_MODEL_AI;
            
            int res = mRtcEngine.enableVirtualBackground(true, source, segProperty);
            if (res == 0) {
                Toast.makeText(getContext(), "Đã bật làm mờ nền", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Lỗi kích hoạt: " + res, Toast.LENGTH_SHORT).show();
            }
            bottomSheetDialog.dismiss();
        });
        
        view.findViewById(R.id.btnBgColor).setOnClickListener(v -> {
            if (mRtcEngine.isFeatureAvailableOnDevice(io.agora.rtc2.Constants.FEATURE_VIDEO_VIRTUAL_BACKGROUND) == false) {
                Toast.makeText(getContext(), "Máy của bạn (hoặc máy ảo) không hỗ trợ tính năng này do phần cứng.", Toast.LENGTH_LONG).show();
                bottomSheetDialog.dismiss();
                return;
            }

            io.agora.rtc2.video.VirtualBackgroundSource source = new io.agora.rtc2.video.VirtualBackgroundSource();
            source.backgroundSourceType = io.agora.rtc2.video.VirtualBackgroundSource.BACKGROUND_COLOR;
            source.color = 0x2B2D31; // màu tối Discord
            
            io.agora.rtc2.video.SegmentationProperty segProperty = new io.agora.rtc2.video.SegmentationProperty();
            segProperty.modelType = io.agora.rtc2.video.SegmentationProperty.SEG_MODEL_AI;
            
            int res = mRtcEngine.enableVirtualBackground(true, source, segProperty);
            if (res == 0) {
                Toast.makeText(getContext(), "Đã đặt màu nền tối Discord", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Lỗi kích hoạt: " + res, Toast.LENGTH_SHORT).show();
            }
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.btnBgStudy).setOnClickListener(v -> {
            applyImageVirtualBackground("bg_cozy_study.png", "Phòng Làm Việc Ấm Áp");
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.btnBgGaming).setOnClickListener(v -> {
            applyImageVirtualBackground("bg_cyberpunk_office.png", "Góc Gaming Cyberpunk");
            bottomSheetDialog.dismiss();
        });

        view.findViewById(R.id.btnBgNature).setOnClickListener(v -> {
            applyImageVirtualBackground("bg_misty_forest.png", "Rừng Thông Sương Mù");
            bottomSheetDialog.dismiss();
        });

        // Setup Filter Buttons
        java.util.List<com.google.android.material.card.MaterialCardView> filterButtons = java.util.Arrays.asList(
            view.findViewById(R.id.btnFilterNormal),
            view.findViewById(R.id.btnFilterApeFace),
            view.findViewById(R.id.btnFilterBigEyes),
            view.findViewById(R.id.btnFilterBigMouth),
            view.findViewById(R.id.btnFilterAlien),
            view.findViewById(R.id.btnFilterSmallEyes),
            view.findViewById(R.id.btnFilterSmallMouth)
        );
        java.util.List<String> filterNames = java.util.Arrays.asList(
            "Normal", "ApeFace", "BigEyes", "BigMouth", "Alien", "SmallEyes", "SmallMouth"
        );

        for (int i = 0; i < filterButtons.size(); i++) {
            final int index = i;
            com.google.android.material.card.MaterialCardView btn = filterButtons.get(i);
            if (btn == null) continue;
            String name = filterNames.get(i);
            
            // Set initial stroke state
            if (name.equals(sSelectedFilter)) {
                btn.setStrokeWidth(dpToPx(2));
                btn.setStrokeColor(Color.parseColor("#5865F2")); // Discord Accent Blue
            } else {
                btn.setStrokeWidth(0);
            }

            btn.setOnClickListener(v -> {
                sSelectedFilter = name;
                for (int j = 0; j < filterButtons.size(); j++) {
                    com.google.android.material.card.MaterialCardView b = filterButtons.get(j);
                    if (b != null) {
                        b.setStrokeWidth(filterNames.get(j).equals(sSelectedFilter) ? dpToPx(2) : 0);
                        b.setStrokeColor(Color.parseColor("#5865F2"));
                    }
                }
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                Toast.makeText(getContext(), "Đã chọn bộ lọc: " + getFilterDisplayName(name), Toast.LENGTH_SHORT).show();
            });
        }

        // Setup Sticker Buttons
        com.google.android.material.card.MaterialCardView btnStickerNone = view.findViewById(R.id.btnStickerNone);
        com.google.android.material.card.MaterialCardView btnStickerCrown = view.findViewById(R.id.btnStickerCrown);
        com.google.android.material.card.MaterialCardView btnStickerGlasses = view.findViewById(R.id.btnStickerGlasses);
        com.google.android.material.card.MaterialCardView btnStickerCatEars = view.findViewById(R.id.btnStickerCatEars);
        com.google.android.material.card.MaterialCardView btnStickerFrame = view.findViewById(R.id.btnStickerFrame);

        java.util.List<com.google.android.material.card.MaterialCardView> stickerButtons = java.util.Arrays.asList(
            btnStickerNone, btnStickerCrown, btnStickerGlasses, btnStickerCatEars, btnStickerFrame
        );
        java.util.List<String> stickerNames = java.util.Arrays.asList(
            "None", "Crown", "Glasses", "CatEars", "Frame"
        );

        for (int i = 0; i < stickerButtons.size(); i++) {
            final int index = i;
            com.google.android.material.card.MaterialCardView btn = stickerButtons.get(i);
            if (btn == null) continue;
            String name = stickerNames.get(i);
            
            // Set initial stroke state
            if (name.equals(sSelectedSticker)) {
                btn.setStrokeWidth(dpToPx(2));
                btn.setStrokeColor(Color.parseColor("#5865F2"));
            } else {
                btn.setStrokeWidth(0);
            }

            btn.setOnClickListener(v -> {
                sSelectedSticker = name;
                for (int j = 0; j < stickerButtons.size(); j++) {
                    com.google.android.material.card.MaterialCardView b = stickerButtons.get(j);
                    if (b != null) {
                        b.setStrokeWidth(stickerNames.get(j).equals(sSelectedSticker) ? dpToPx(2) : 0);
                        b.setStrokeColor(Color.parseColor("#5865F2"));
                    }
                }
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                Toast.makeText(getContext(), "Đã chọn trang trí: " + getStickerDisplayName(name), Toast.LENGTH_SHORT).show();
            });
        }
        
        bottomSheetDialog.show();
    }

    private void applyImageVirtualBackground(String assetFileName, String displayName) {
        if (mRtcEngine == null) return;
        if (mRtcEngine.isFeatureAvailableOnDevice(io.agora.rtc2.Constants.FEATURE_VIDEO_VIRTUAL_BACKGROUND) == false) {
            Toast.makeText(getContext(), "Máy của bạn (hoặc máy ảo) không hỗ trợ tính năng này do phần cứng.", Toast.LENGTH_LONG).show();
            return;
        }

        String imagePath = getAssetBgPath(assetFileName);
        if (imagePath == null) {
            Toast.makeText(getContext(), "Lỗi tải ảnh nền", Toast.LENGTH_SHORT).show();
            return;
        }

        io.agora.rtc2.video.VirtualBackgroundSource source = new io.agora.rtc2.video.VirtualBackgroundSource();
        source.backgroundSourceType = io.agora.rtc2.video.VirtualBackgroundSource.BACKGROUND_IMG;
        source.source = imagePath;

        io.agora.rtc2.video.SegmentationProperty segProperty = new io.agora.rtc2.video.SegmentationProperty();
        segProperty.modelType = io.agora.rtc2.video.SegmentationProperty.SEG_MODEL_AI;

        int res = mRtcEngine.enableVirtualBackground(true, source, segProperty);
        if (res == 0) {
            Toast.makeText(getContext(), "Đã bật hình nền: " + displayName, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "Lỗi kích hoạt: " + res, Toast.LENGTH_SHORT).show();
        }
    }

    private String getAssetBgPath(String assetFileName) {
        if (getContext() == null) return null;
        java.io.File file = new java.io.File(getContext().getCacheDir(), assetFileName);
        if (file.exists()) {
            return file.getAbsolutePath();
        }
        try {
            java.io.InputStream is = getContext().getAssets().open(assetFileName);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            byte[] buffer = new byte[1024 * 4];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
            fos.close();
            is.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getFilterDisplayName(String name) {
        switch (name) {
            case "ApeFace": return "Mặt Vượn";
            case "BigEyes": return "Mắt To";
            case "BigMouth": return "Mồm Rộng";
            case "Alien": return "Mặt Alien";
            case "SmallEyes": return "Mắt Híp";
            case "SmallMouth": return "Mồm Nhỏ";
            default: return "Gốc";
        }
    }

    private String getStickerDisplayName(String name) {
        switch (name) {
            case "Crown": return "Vương miện";
            case "Glasses": return "Kính ngầu";
            case "CatEars": return "Tai mèo";
            case "Frame": return "Trái tim";
            default: return "Không dùng";
        }
    }

    private int dpToPx(int dp) {
        if (getContext() == null) return dp;
        return (int) (dp * getResources().getDisplayMetrics().density);
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
                    int memberHash = m.getUserId().hashCode() & 0x7FFFFFFF;
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
                    if (m.getUserId() != null && (m.getUserId().hashCode() & 0x7FFFFFFF) == targetUid) {
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
                if (m.getUserId() != null && (m.getUserId().hashCode() & 0x7FFFFFFF) == targetUid) {
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
        sortParticipantList();
        adapter.setServerMembers(serverMembers);
        adapter.notifyDataSetChanged();
    }

    private void sortParticipantList() {
        if (participantList == null || participantList.isEmpty()) return;
        java.util.Collections.sort(participantList, (p1, p2) -> {
            boolean isScreen1 = p1.isSharingScreen || p1.name.contains("Màn hình");
            boolean isScreen2 = p2.isSharingScreen || p2.name.contains("Màn hình");
            if (isScreen1 != isScreen2) {
                return isScreen1 ? -1 : 1;
            }

            if (p1.isVideoOff != p2.isVideoOff) {
                return p1.isVideoOff ? 1 : -1;
            }

            boolean isMe1 = p1.uid == this.uid || p1.name.contains("(Me)") || p1.name.equals("Me");
            boolean isMe2 = p2.uid == this.uid || p2.name.contains("(Me)") || p2.name.equals("Me");
            if (isMe1 != isMe2) {
                return isMe1 ? -1 : 1;
            }

            return p1.name.compareToIgnoreCase(p2.name);
        });
    }

    private void showReconnectingUI() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (reconnectDialog == null) {
                reconnectDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Đang kết nối lại...")
                        .setMessage("Kết nối mạng không ổn định. Đang thử kết nối lại cuộc gọi. Tự động ngắt sau 20s.")
                        .setCancelable(false)
                        .create();
            }
            if (!reconnectDialog.isShowing()) {
                reconnectDialog.show();
                reconnectHandler.postDelayed(reconnectRunnable, 20000); // 20 seconds timeout
            }
        });
    }

    private void hideReconnectingUI() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (reconnectDialog != null && reconnectDialog.isShowing()) {
                reconnectDialog.dismiss();
            }
            reconnectHandler.removeCallbacks(reconnectRunnable);
        });
    }

    private void leaveAndExit() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });
    }

    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        if (binding == null) return;
        if (isInPictureInPictureMode) {
            // Ẩn tiêu đề và thanh công cụ
            binding.callHeader.setVisibility(View.GONE);
            binding.controlPanel.setVisibility(View.GONE);
            
            // Xoá padding RecyclerView
            binding.rvParticipants.setPadding(0, 0, 0, 0);
            
            // Đặt LayoutManager thành 1 cột để item chiếm trọn màn hình PiP
            binding.rvParticipants.setLayoutManager(new GridLayoutManager(getContext(), 1));

            // Cấu hình adapter ở chế độ PiP
            if (adapter != null) {
                adapter.setInPiPMode(true);
                adapter.notifyDataSetChanged();
            }

            // Đồng bộ Aspect Ratio của PiP dựa trên Screen Share hiện tại
            updatePiPParams();
        } else {
            // Hiện lại tiêu đề và thanh công cụ
            binding.callHeader.setVisibility(View.VISIBLE);
            binding.controlPanel.setVisibility(View.VISIBLE);
            
            // Khôi phục padding RecyclerView
            int paddingPx = (int) (8 * getResources().getDisplayMetrics().density);
            binding.rvParticipants.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
            
            // Khôi phục LayoutManager bình thường
            updateGridLayout();

            // Cấu hình adapter về chế độ bình thường
            if (adapter != null) {
                adapter.setInPiPMode(false);
                adapter.notifyDataSetChanged();
            }
        }
    }

    public void updatePiPParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && getActivity() != null) {
            try {
                android.app.PictureInPictureParams.Builder builder = new android.app.PictureInPictureParams.Builder();
                // Luôn giữ tỉ lệ đứng dọc (3:4) cho ứng dụng di động theo thiết kế mobile
                builder.setAspectRatio(new android.util.Rational(3, 4));
                getActivity().setPictureInPictureParams(builder.build());
            } catch (Exception e) {
                Log.e(TAG, "Error updating PiP params: " + e.getMessage());
            }
        }
    }
}
