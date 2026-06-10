package com.example.se114_callingsystem.features.call.ui;

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
import com.example.se114_callingsystem.features.call.viewmodel.VoiceCallViewModel;
import androidx.lifecycle.ViewModelProvider;
import dagger.hilt.android.AndroidEntryPoint;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcConnection;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
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

    private RtcEngine mRtcEngine;
    private int uid;

    private String channelName = "TestChannel";
    private RtcConnection screenShareConnection;
    private final int SCREEN_SHARE_UID_OFFSET = 1000;
    private boolean isSharingScreen = false;
    private boolean isUiVisible = true;

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

        if (isMinimized) {
            mRtcEngine = sRtcEngine;
            participantList.clear();
            participantList.addAll(sParticipantList);
            channelName = sChannelName;
            uid = sUid;
            serverColor = sServerColor;
            serverId = sServerId;
            isMinimized = false;
            minimizedCallEvent.setValue(false);
            
            if (mRtcEngine != null) {
                mRtcEngine.addHandler(mRtcEventHandler);
            }
            
            binding.tvCallChannelName.setText(channelName);
            setupRecyclerView();
            setupControls();
            updateParticipantCount();
            
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            
            viewModel.loadServerMembers(serverId);
            return;
        }

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
            isMinimized = true;
            sRtcEngine = mRtcEngine;
            sParticipantList.clear();
            sParticipantList.addAll(participantList);
            sChannelName = channelName;
            sUid = uid;
            sServerColor = serverColor;
            sServerId = serverId;
            minimizedCallEvent.setValue(true);

            Navigation.findNavController(v).popBackStack();
        });

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
            mRtcEngine = RtcEngine.create(config);

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
        adapter.setServerMembers(serverMembers);
        binding.rvParticipants.setAdapter(adapter);
        updateGridLayout();
    }

    private void updateGridLayout() {
        if (binding == null) return;
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
            
            if (count <= 1) {
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
                    boolean isNowSpeaking;
                    if (p.uid == uid) {
                        isNowSpeaking = activeSpeakers.contains(0) || activeSpeakers.contains(uid);
                    } else {
                        isNowSpeaking = activeSpeakers.contains(p.uid);
                    }

                    if (p.isSpeaking != isNowSpeaking) {
                        p.isSpeaking = isNowSpeaking;
                        if (adapter != null) adapter.notifyItemChanged(i, "border_update");
                    }
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
        updateMuteButtonUI(false);
        binding.btnMute.setSelected(false);
        binding.btnToggleVideo.setSelected(true); // Video ban đầu tắt nên set selected = true để click lần đầu bật lên

        binding.btnToggleVideo.setOnLongClickListener(v -> {
            showVirtualBgDialog();
            return true;
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
            } else {
                if (!isSharingScreen) {
                    mRtcEngine.stopPreview();
                }
            }
            updateVideoButtonUI(isVideoOff);
            if (!participantList.isEmpty()) {
                participantList.get(0).isVideoOff = isVideoOff;
                sortParticipantList();
                updateGridLayout();
                if (adapter != null) adapter.notifyDataSetChanged();
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
        if (isMinimized) {
            if (mRtcEngine != null) {
                mRtcEngine.removeHandler(mRtcEventHandler);
            }
            super.onDestroy();
            return;
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
        sParticipantList.clear();
        isMinimized = false;
        minimizedCallEvent.setValue(false);

        // Clear user's active call channel in Firestore
        viewModel.clearVoiceChannel();

        super.onDestroy();
    }

    private void showVirtualBgDialog() {
        if (getContext() == null || mRtcEngine == null) return;
        
        String[] options = {"Không dùng (Normal)", "Làm mờ nền (Background Blur)", "Hình nền ảo (Virtual Background)"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Bộ lọc nền Video")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    mRtcEngine.enableVirtualBackground(false, null, null);
                    Toast.makeText(getContext(), "Đã tắt bộ lọc nền", Toast.LENGTH_SHORT).show();
                } else if (which == 1) {
                    io.agora.rtc2.video.VirtualBackgroundSource source = new io.agora.rtc2.video.VirtualBackgroundSource();
                    source.backgroundSourceType = io.agora.rtc2.video.VirtualBackgroundSource.BACKGROUND_BLUR;
                    source.blurDegree = io.agora.rtc2.video.VirtualBackgroundSource.BLUR_DEGREE_MEDIUM;
                    
                    io.agora.rtc2.video.SegmentationProperty segProperty = new io.agora.rtc2.video.SegmentationProperty();
                    segProperty.modelType = io.agora.rtc2.video.SegmentationProperty.SEG_MODEL_AI;
                    
                    int res = mRtcEngine.enableVirtualBackground(true, source, segProperty);
                    if (res == 0) {
                        Toast.makeText(getContext(), "Đã bật làm mờ nền", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Không hỗ trợ trên thiết bị này: " + res, Toast.LENGTH_SHORT).show();
                    }
                } else if (which == 2) {
                    io.agora.rtc2.video.VirtualBackgroundSource source = new io.agora.rtc2.video.VirtualBackgroundSource();
                    source.backgroundSourceType = io.agora.rtc2.video.VirtualBackgroundSource.BACKGROUND_COLOR;
                    source.color = 0x2B2D31; // màu tối Discord
                    
                    io.agora.rtc2.video.SegmentationProperty segProperty = new io.agora.rtc2.video.SegmentationProperty();
                    segProperty.modelType = io.agora.rtc2.video.SegmentationProperty.SEG_MODEL_AI;
                    
                    int res = mRtcEngine.enableVirtualBackground(true, source, segProperty);
                    if (res == 0) {
                        Toast.makeText(getContext(), "Đã đặt màu nền tối Discord", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Không hỗ trợ trên thiết bị này: " + res, Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .show();
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
        if (getActivity() == null || getView() == null) return;
        getActivity().runOnUiThread(() -> {
            try {
                androidx.navigation.Navigation.findNavController(requireView()).popBackStack();
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            }
        });
    }
}
