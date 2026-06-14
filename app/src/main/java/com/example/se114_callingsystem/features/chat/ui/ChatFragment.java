package com.example.se114_callingsystem.features.chat.ui;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.databinding.FragmentChatBinding;
import com.example.se114_callingsystem.core.model.Firebase;
import com.example.se114_callingsystem.core.model.Message;
import com.example.se114_callingsystem.core.di.AppDependencyProvider;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.example.se114_callingsystem.features.chat.viewmodel.ChatViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.widget.TextView;
import android.widget.Button;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.view.MotionEvent;
import android.media.MediaRecorder;
import java.io.File;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ChatFragment extends Fragment {

    private static final String TAG = "ChatFragment";
    public static String activeChatId = null;

    private FragmentChatBinding binding;
    private ChatAdapter adapter;
    private final List<Message> messageList = new ArrayList<>();
    private final List<String> pendingMessageIds = new java.util.ArrayList<>();
    
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String> filePickerLauncher;
    private ActivityResultLauncher<String> videoPickerLauncher;
    private Message messageToReply = null;
    private String lastMessageId = null;

    private String groupId;
    private String senderId;
    private String serverColor = "#5865F2"; // Discord Blurple default

    private String serverId;
    private final List<ServerMember> serverMembers = new ArrayList<>();
    private final List<ServerMember> filteredMembers = new ArrayList<>();
    private MentionAdapter mentionAdapter;
    private com.google.firebase.firestore.ListenerRegistration dmNicknamesListener;

    private final android.os.Handler typingHandler = new android.os.Handler();
    private final Runnable typingStopRunnable = () -> setTypingStatus(false);
    private boolean isTyping = false;
    private ChatViewModel viewModel;
    private DatabaseReference groupChatRef;

    private ActivityResultLauncher<String> requestAudioPermissionLauncher;
    private android.media.MediaRecorder mediaRecorder = null;
    private String audioFilePath = null;
    private long recordStartTime = 0;

    // Recording state tracking (Messenger-style)
    private boolean isPaused = false;
    private boolean isPreviewMode = false;
    private long totalRecordedDuration = 0;
    private long sessionStartTime = 0;
    private final android.os.Handler timerHandler = new android.os.Handler();
    private Runnable timerRunnable = null;
    private String segmentFilePath = null;
    private Runnable previewProgressRunnable = null;
    private final android.os.Handler previewProgressHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        senderId = AppDependencyProvider.getFirebaseAuth().getCurrentUser() != null ? AppDependencyProvider.getFirebaseAuth().getCurrentUser().getUid() : "UNKNOWN";

        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) uploadToCloudinary(uri, "image");
        });
        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) uploadToCloudinary(uri, "file");
        });
        videoPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) uploadToCloudinary(uri, "video");
        });

        requestAudioPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(getContext(), "Quyền ghi âm đã được cấp. Bắt đầu ghi âm.", Toast.LENGTH_SHORT).show();
                    startRecording();
                } else {
                    Toast.makeText(getContext(), "Cần quyền ghi âm để sử dụng tính năng này.", Toast.LENGTH_SHORT).show();
                }
            }
        );

        com.google.android.material.transition.MaterialContainerTransform transform = 
            new com.google.android.material.transition.MaterialContainerTransform();
        transform.setDrawingViewId(R.id.nav_host_fragment);
        transform.setDuration(300L);
        transform.setFadeMode(com.google.android.material.transition.MaterialContainerTransform.FADE_MODE_THROUGH);
        transform.setStartContainerColor(android.graphics.Color.parseColor("#2B2D31"));
        transform.setEndContainerColor(android.graphics.Color.parseColor("#313338"));
        setSharedElementEnterTransition(transform);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Đảm bảo thanh nhắn tin nâng lên khi bàn phím mở
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int imeBottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;
            int navBottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
            int bottomPadding = Math.max(imeBottom, navBottom);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottomPadding);
            return insets;
        });

        viewModel = new androidx.lifecycle.ViewModelProvider(this).get(ChatViewModel.class);

        // Retrieve Arguments passed via navigation bundle
        if (getArguments() != null) {
            String transitionName = getArguments().getString("TRANSITION_NAME");
            if (transitionName != null) {
                binding.chatRoot.setTransitionName(transitionName);
            }
            groupId = getArguments().getString("CHAT_ID");
            String channelName = getArguments().getString("CHAT_NAME");
            serverColor = getArguments().getString("SERVER_COLOR", "#5865F2");
            serverId = getArguments().getString("SERVER_ID");

            if (channelName != null) {
                if (serverId == null) {
                    // Chat DM 1-1 (Private Chat)
                    binding.tvChannelHash.setVisibility(View.GONE);
                    binding.ivOnlineStatus.setVisibility(View.VISIBLE);
                    binding.btnVoiceCall.setVisibility(View.VISIBLE);
                    binding.btnVideoCall.setVisibility(View.VISIBLE);
                    binding.tvChannelName.setText(channelName);
                    binding.edtMessage.setHint(getString(R.string.chat_input_hint_dm, channelName));
                    loadDMParticipants();
                } else {
                    // Chat Server Channel
                    binding.tvChannelHash.setVisibility(View.VISIBLE);
                    binding.ivOnlineStatus.setVisibility(View.GONE);
                    binding.btnVoiceCall.setVisibility(View.GONE);
                    binding.btnVideoCall.setVisibility(View.GONE);
                    binding.tvChannelName.setText(channelName.toLowerCase());
                    binding.edtMessage.setHint(getString(R.string.chat_input_hint_channel, channelName.toLowerCase()));
                }
            }
        }

        activeChatId = groupId;

        // QUAN TRỌNG: setupRecyclerView() phải được gọi TRƯỚC listenForMessages()
        // để adapter không bị null khi Firebase fire onDataChange từ cache
        setupRecyclerView();
        setupClickListeners();
        setupMentionSuggestions();
        setupAudioRecording();
        setupObservers();

        if (groupId != null) {
            groupChatRef = Firebase.getDatabase().getReference("chats").child(groupId);
            viewModel.startChatSession(groupId, serverId, senderId);
            markNotificationsAsRead(groupId);
        }
    }

    private void setupObservers() {
        viewModel.getMessages().observe(getViewLifecycleOwner(), list -> {
            if (binding == null) return;
            
            messageList.clear();
            for (Message model : list) {
                if (pendingMessageIds.contains(model.getMessageId())) {
                    model.setPending(true);
                }
                messageList.add(model);
            }
            adapter.notifyDataSetChanged();
            updatePinnedMessageHeader();

            if (!messageList.isEmpty()) {
                Message lastMsg = messageList.get(messageList.size() - 1);
                String currentLastMsgId = lastMsg.getMessageId();
                if (ChatFragment.this.lastMessageId == null || !ChatFragment.this.lastMessageId.equals(currentLastMsgId)) {
                    binding.chatRecyclerView.post(() -> {
                        if (binding != null) {
                            binding.chatRecyclerView.scrollToPosition(messageList.size() - 1);
                        }
                    });
                    ChatFragment.this.lastMessageId = currentLastMsgId;
                }
            } else {
                ChatFragment.this.lastMessageId = null;
            }
        });

        viewModel.getTypingUsers().observe(getViewLifecycleOwner(), users -> {
            if (users.isEmpty()) {
                hideTypingIndicator();
            } else {
                showTypingIndicator(users);
            }
        });

        viewModel.getServerMembers().observe(getViewLifecycleOwner(), members -> {
            if (binding == null) return;
            serverMembers.clear();
            serverMembers.addAll(members);
            if (adapter != null) {
                adapter.setServerMembers(serverMembers);
            }
            if (mentionAdapter != null) {
                mentionAdapter.setList(new ArrayList<>(serverMembers));
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new ChatAdapter(messageList, serverColor, new ChatAdapter.OnChatInteractListener() {
            @Override
            public void onReply(Message message) { showReplyUI(message); }
            @Override
            public void onDelete(Message message) {
                viewModel.deleteMessage(message.getMessageId());
            }
            @Override
            public void onReact(Message message, String emoji) {
                viewModel.updateReaction(message.getMessageId(), emoji);
            }
            @Override
            public void onPinToggle(Message message) {
                viewModel.togglePin(message.getMessageId(), !message.isPinned());
            }
            @Override
            public void onEditReminder(Message message) {
                if ("reminder".equals(message.getType())) {
                    showReminderDialog(message, null);
                } else {
                    showReminderDialog(null, message.getContent());
                }
            }
            @Override
            public void onRepliedMessageClick(Message message) {
                String targetId = message.getRepliedToMessageId();
                if (targetId == null || targetId.trim().isEmpty()) {
                    for (Message m : messageList) {
                        if (m.getContent() != null && m.getContent().equals(message.getRepliedToContent())) {
                            targetId = m.getMessageId();
                            break;
                        }
                    }
                }
                if (targetId != null) {
                    scrollToMessage(targetId);
                }
            }
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true); // Chat style: stack messages from bottom
        binding.chatRecyclerView.setLayoutManager(layoutManager);
        binding.chatRecyclerView.setAdapter(adapter);
        setupSwipeToReply();
    }

    private void setupSwipeToReply() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder v, @NonNull RecyclerView.ViewHolder t) { return false; }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                showReplyUI(messageList.get(position));
                adapter.notifyItemChanged(position);
            }
            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                View itemView = viewHolder.itemView;
                int viewType = viewHolder.getItemViewType();
                boolean isSent = (viewType == 1);
                float limitedDX = dX;
                if (isSent && dX > 0) limitedDX = 0;
                if (!isSent && dX < 0) limitedDX = 0;
                super.onChildDraw(c, recyclerView, viewHolder, limitedDX, dY, actionState, isCurrentlyActive);
            }
        };
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.chatRecyclerView);
    }

    private void setupClickListeners() {
        binding.btnAttachHome.setOnClickListener(v -> {
            String[] options = {"📷 Send Image", "🎥 Send Video", "📎 Send File", "🎬 Tìm và gửi ảnh GIF", "⏰ Đặt lời nhắc"};
            com.example.se114_callingsystem.core.util.BottomSheetUtils.showListDialog(
                    requireContext(),
                    "Upload Media & Options",
                    options,
                    (index, option) -> {
                        if (index == 0) imagePickerLauncher.launch("image/*");
                        else if (index == 1) videoPickerLauncher.launch("video/*");
                        else if (index == 2) filePickerLauncher.launch("*/*");
                        else if (index == 3) showGifSearchDialog();
                        else showReminderDialog(null, null);
                    }
            );
        });

        binding.btnBack.setOnClickListener(v -> {
            Navigation.findNavController(v).popBackStack();
        });

        binding.btnVoiceCall.setOnClickListener(v -> initiateDirectCall("voice"));
        binding.btnVideoCall.setOnClickListener(v -> initiateDirectCall("video"));

        binding.btnSend.setOnClickListener(v -> sendMessage());
        
        binding.tvReplyingToLayout.setOnClickListener(v -> {
            messageToReply = null;
            binding.tvReplyingToLayout.setVisibility(View.GONE);
        });
        
        View btnCancelReply = binding.tvReplyingToLayout.findViewById(R.id.btnCancelReply);
        if (btnCancelReply != null) {
            btnCancelReply.setOnClickListener(v -> {
                messageToReply = null;
                binding.tvReplyingToLayout.setVisibility(View.GONE);
            });
        }

        androidx.navigation.NavController navController = Navigation.findNavController(requireView());
        if (navController.getCurrentBackStackEntry() != null) {
            navController.getCurrentBackStackEntry().getSavedStateHandle()
                .getLiveData("GOTO_MESSAGE_ID")
                .observe(getViewLifecycleOwner(), targetIdObj -> {
                    if (targetIdObj != null) {
                        String targetId = (String) targetIdObj;
                        scrollToMessage(targetId);
                        navController.getCurrentBackStackEntry().getSavedStateHandle().remove("GOTO_MESSAGE_ID");
                    }
                });
        }

        View.OnClickListener toChatInfo = v -> {
            Bundle args = new Bundle();
            args.putString("CHAT_ID", groupId);
            args.putString("CHAT_NAME", binding.tvChannelName.getText().toString());
            args.putString("SERVER_ID", serverId);
            args.putString("SERVER_COLOR", serverColor);
            Navigation.findNavController(v).navigate(R.id.action_chat_to_chat_info, args);
        };
        binding.tvChannelName.setOnClickListener(toChatInfo);
    }

    private void sendMessage() {
        if (binding == null) return;

        String msg = binding.edtMessage.getText().toString().trim();
        if (!msg.isEmpty() && viewModel != null) {
            Message messageModel = new Message(senderId, groupId, msg, System.currentTimeMillis());
            if (messageToReply != null) {
                messageModel.setRepliedToContent(messageToReply.getContent());
                messageModel.setRepliedToType(messageToReply.getType());
                messageModel.setRepliedToMessageId(messageToReply.getMessageId());
                messageToReply = null;
                binding.tvReplyingToLayout.setVisibility(View.GONE);
            }
            
            // Generate message ID beforehand for tracking pending state
            String messageId = "msg_" + System.currentTimeMillis();
            messageModel.setMessageId(messageId);
            
            if (getContext() != null && !com.example.se114_callingsystem.core.util.NetworkMonitor.isNetworkAvailable(getContext())) {
                pendingMessageIds.add(messageId);
                messageModel.setPending(true);
            }
            
            viewModel.sendMessage(messageModel, task -> {
                if (task.isSuccessful()) {
                    pendingMessageIds.remove(messageId);
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                    checkAndTriggerMentions(messageModel);
                }
            });
        }

        if (binding != null) binding.edtMessage.setText("");
        setTypingStatus(false);
    }

    private void uploadToCloudinary(Uri fileUri, String type) {
        if (getContext() == null) return;
        if (!com.example.se114_callingsystem.core.util.NetworkMonitor.isNetworkAvailable(getContext())) {
            Toast.makeText(getContext(), "Không có kết nối mạng. Không thể tải lên file.", Toast.LENGTH_SHORT).show();
            return;
        }
        ProgressDialog pd = new ProgressDialog(getContext());
        pd.setMessage("Uploading payload...");
        pd.show();
        String resourceType = ("video".equals(type) || "audio".equals(type)) ? "video" : "auto";
        MediaManager.get().upload(fileUri).option("resource_type", resourceType).callback(new UploadCallback() {
            @Override public void onStart(String requestId) {}
            @Override public void onProgress(String requestId, long bytes, long totalBytes) {}
            @Override public void onSuccess(String requestId, Map resultData) {
                pd.dismiss();
                sendMediaMessage((String) resultData.get("secure_url"), type);
            }
            @Override public void onError(String requestId, ErrorInfo error) {
                pd.dismiss();
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Error: " + error.getDescription(), Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onReschedule(String requestId, ErrorInfo error) {}
        }).dispatch();
    }

    private void sendMediaMessage(String fileUrl, String type) {
        if (viewModel == null) return;
        Message model = new Message(senderId, groupId, fileUrl, System.currentTimeMillis());
        model.setType(type);
        if (messageToReply != null) {
            model.setRepliedToContent(messageToReply.getContent());
            model.setRepliedToType(messageToReply.getType());
            model.setRepliedToMessageId(messageToReply.getMessageId());
            messageToReply = null;
            if (binding != null) binding.tvReplyingToLayout.setVisibility(View.GONE);
        }
        String messageId = "msg_" + System.currentTimeMillis();
        model.setMessageId(messageId);
        viewModel.sendMessage(model, task -> {
            if (task.isSuccessful()) {
                checkAndTriggerMentions(model);
            }
        });
    }



    private void showReplyUI(Message message) {
        if (message.isDeleted() || binding == null) return;
        messageToReply = message;
        binding.tvReplyingToLayout.setVisibility(View.VISIBLE);
        String type = message.getType();
        if ("image".equals(type)) {
            binding.tvReplyingToText.setText("📷 Hình ảnh");
            binding.cardReplyPreviewImage.setVisibility(View.VISIBLE);
            Glide.with(this).load(message.getContent()).diskCacheStrategy(DiskCacheStrategy.ALL).centerCrop().into(binding.ivReplyPreview);
        } else if ("file".equals(type)) {
            String fileName = "Tài liệu đính kèm";
            try { fileName = message.getContent().substring(message.getContent().lastIndexOf('/') + 1); } catch (Exception e) {}
            binding.tvReplyingToText.setText("📎 " + fileName);
            binding.cardReplyPreviewImage.setVisibility(View.GONE);
        } else {
            String content = message.getContent();
            binding.tvReplyingToText.setText(content.length() > 40 ? content.substring(0, 40) + "..." : content);
            binding.cardReplyPreviewImage.setVisibility(View.GONE);
        }
        binding.edtMessage.requestFocus();
    }

    private void updatePinnedMessageHeader() {
        if (binding == null) return;
        View layoutPinnedMessage = binding.getRoot().findViewById(R.id.layoutPinnedMessage);
        TextView tvPinnedContent = binding.getRoot().findViewById(R.id.tvPinnedMessageContent);
        if (layoutPinnedMessage == null || tvPinnedContent == null) return;

        Message latestPinned = null;
        // Search backwards to find the latest pinned message
        for (int i = messageList.size() - 1; i >= 0; i--) {
            Message m = messageList.get(i);
            if (m.isPinned() && !m.isDeleted()) {
                latestPinned = m;
                break;
            }
        }

        if (latestPinned != null) {
            layoutPinnedMessage.setVisibility(View.VISIBLE);
            String type = latestPinned.getType();
            if ("image".equals(type)) {
                tvPinnedContent.setText("📷 Hình ảnh");
            } else if ("file".equals(type)) {
                String fileName = "Tài liệu đính kèm";
                try { fileName = latestPinned.getContent().substring(latestPinned.getContent().lastIndexOf('/') + 1); } catch (Exception e) {}
                tvPinnedContent.setText("📎 " + fileName);
            } else if ("reminder".equals(type)) {
                tvPinnedContent.setText("⏰ Lời nhắc: " + latestPinned.getContent());
            } else {
                tvPinnedContent.setText(latestPinned.getContent());
            }

            layoutPinnedMessage.setOnClickListener(v -> {
                showPinnedMessagesBottomSheet();
            });

            android.widget.ImageView btnViewPinnedMessages = binding.getRoot().findViewById(R.id.btnViewPinnedMessages);
            if (btnViewPinnedMessages != null) {
                btnViewPinnedMessages.setOnClickListener(v -> showPinnedMessagesBottomSheet());
            }
        } else {
            layoutPinnedMessage.setVisibility(View.GONE);
        }
    }

    private void showPinnedMessagesBottomSheet() {
        if (getContext() == null) return;
        java.util.List<Message> pinnedMessages = new java.util.ArrayList<>();
        for (Message m : messageList) {
            if (m.isPinned() && !m.isDeleted()) {
                pinnedMessages.add(m);
            }
        }
        
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(getContext());
        View view = getLayoutInflater().inflate(R.layout.layout_chat_bottom_sheet_pinned_messages, null);
        dialog.setContentView(view);
        
        try {
            ((View) view.getParent()).setBackgroundColor(android.graphics.Color.TRANSPARENT);
        } catch (Exception e) {}
        
        androidx.recyclerview.widget.RecyclerView rvPinnedMessages = view.findViewById(R.id.rvPinnedMessages);
        View tvNoPinnedMessages = view.findViewById(R.id.tvNoPinnedMessages);
        
        if (pinnedMessages.isEmpty()) {
            rvPinnedMessages.setVisibility(View.GONE);
            tvNoPinnedMessages.setVisibility(View.VISIBLE);
        } else {
            rvPinnedMessages.setVisibility(View.VISIBLE);
            tvNoPinnedMessages.setVisibility(View.GONE);
            
            rvPinnedMessages.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
            PinnedMessagesAdapter pinnedAdapter = new PinnedMessagesAdapter(pinnedMessages, serverColor, new PinnedMessagesAdapter.OnPinnedMessageInteractListener() {
                @Override
                public void onGoTo(Message message) {
                    dialog.dismiss();
                    scrollToMessage(message.getMessageId());
                }
                @Override
                public void onUnpin(Message message) {
                    viewModel.togglePin(message.getMessageId(), false);
                    dialog.dismiss();
                }
            });
            pinnedAdapter.setServerMembers(serverMembers);
            rvPinnedMessages.setAdapter(pinnedAdapter);
        }
        
        dialog.show();
    }

    private void setupMentionSuggestions() {
        mentionAdapter = new MentionAdapter(filteredMembers);
        binding.rvMentionSuggestions.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvMentionSuggestions.setAdapter(mentionAdapter);

        binding.edtMessage.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (binding == null) return;
                
                // Typing Indicator logic
                if (s.length() > 0) {
                    if (!isTyping) setTypingStatus(true);
                    typingHandler.removeCallbacks(typingStopRunnable);
                    typingHandler.postDelayed(typingStopRunnable, 2000); // Stop after 2s of no typing
                } else {
                    setTypingStatus(false);
                    typingHandler.removeCallbacks(typingStopRunnable);
                }

                if (s.toString().trim().isEmpty()) {
                    binding.btnSend.setVisibility(View.GONE);
                    binding.btnRecordAudio.setVisibility(View.VISIBLE);
                } else {
                    binding.btnSend.setVisibility(View.VISIBLE);
                    binding.btnRecordAudio.setVisibility(View.GONE);
                }

                int cursor = binding.edtMessage.getSelectionStart();
                if (cursor < 0) {
                    hideMentionSuggestions();
                    return;
                }
                String text = s.toString();
                int atIndex = -1;
                for (int i = cursor - 1; i >= 0; i--) {
                    char c = text.charAt(i);
                    if (c == '@') {
                        if (i == 0 || Character.isWhitespace(text.charAt(i - 1))) {
                            atIndex = i;
                            break;
                        }
                    }
                }

                if (atIndex != -1) {
                    String query = text.substring(atIndex + 1, cursor);
                    if (query.contains("\n")) {
                        hideMentionSuggestions();
                    } else {
                        showMentionSuggestions(query);
                    }
                } else {
                    hideMentionSuggestions();
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void showMentionSuggestions(String query) {
        if (binding == null) return;
        filteredMembers.clear();
        String lowercaseQuery = query.toLowerCase();
        for (ServerMember member : serverMembers) {
            boolean matchesUsername = member.getUserName() != null && member.getUserName().toLowerCase().contains(lowercaseQuery);
            boolean matchesNickname = member.getNickname() != null && member.getNickname().toLowerCase().contains(lowercaseQuery);
            if (matchesUsername || matchesNickname) {
                filteredMembers.add(member);
            }
        }
        
        if (filteredMembers.isEmpty()) {
            hideMentionSuggestions();
        } else {
            mentionAdapter.setList(filteredMembers);
            binding.cardMentionSuggestions.setVisibility(View.VISIBLE);
        }
    }

    private void hideMentionSuggestions() {
        if (binding != null) {
            binding.cardMentionSuggestions.setVisibility(View.GONE);
        }
    }

    private void setTypingStatus(boolean typing) {
        isTyping = typing;
        if (viewModel != null) {
            viewModel.setTypingStatus(typing);
        }
    }

    private void showTypingIndicator(List<String> typingUsers) {
        android.widget.LinearLayout typingLayout = binding.getRoot().findViewById(R.id.typingIndicatorLayout);
        android.widget.TextView tvTypingStatus = binding.getRoot().findViewById(R.id.tvTypingStatus);
        if (typingLayout.getVisibility() == View.GONE) {
            typingLayout.setVisibility(View.VISIBLE);
            typingLayout.setTranslationY(50f);
            typingLayout.setAlpha(0f);
            typingLayout.animate().translationY(0f).alpha(1f).setDuration(300).start();
        }
        
        if (typingUsers.size() == 1) {
            String uid = typingUsers.get(0);
            String name = "Someone";
            for (ServerMember m : serverMembers) {
                if (uid.equals(m.getUserId())) {
                    name = m.getNickname() != null && !m.getNickname().isEmpty() ? m.getNickname() : m.getUserName();
                    break;
                }
            }
            tvTypingStatus.setText(name + " " + getString(R.string.typing_indicator));
        } else {
            tvTypingStatus.setText(getString(R.string.several_people_typing));
        }
        
        // Auto scroll to bottom if we are already at bottom
        if (lastMessageId != null) {
            binding.chatRecyclerView.post(() -> binding.chatRecyclerView.scrollToPosition(messageList.size() - 1));
        }
    }

    private void hideTypingIndicator() {
        android.widget.LinearLayout typingLayout = binding.getRoot().findViewById(R.id.typingIndicatorLayout);
        if (typingLayout.getVisibility() == View.VISIBLE) {
            typingLayout.animate().translationY(50f).alpha(0f).setDuration(200).withEndAction(() -> {
                typingLayout.setVisibility(View.GONE);
            }).start();
        }
    }



    private void insertMention(ServerMember member) {
        if (binding == null) return;
        int cursor = binding.edtMessage.getSelectionStart();
        if (cursor < 0) return;
        String text = binding.edtMessage.getText().toString();
        
        int atIndex = -1;
        for (int i = cursor - 1; i >= 0; i--) {
            if (text.charAt(i) == '@') {
                if (i == 0 || Character.isWhitespace(text.charAt(i - 1))) {
                    atIndex = i;
                    break;
                }
            }
        }

        if (atIndex != -1) {
            String before = text.substring(0, atIndex);
            String after = text.substring(cursor);
            String nameToInsert = member.getNickname();
            if (nameToInsert == null || nameToInsert.trim().isEmpty()) {
                nameToInsert = member.getUserName();
            }
            if (nameToInsert == null) {
                nameToInsert = "";
            }
            String mention = "@" + nameToInsert + " ";
            
            binding.edtMessage.setText(before + mention + after);
            binding.edtMessage.setSelection(atIndex + mention.length());
        }
        hideMentionSuggestions();
    }

    private void scrollToMessage(String messageId) {
        if (messageId == null || messageList == null || binding == null) return;
        for (int i = 0; i < messageList.size(); i++) {
            Message m = messageList.get(i);
            if (m != null && messageId.equals(m.getMessageId())) {
                final int pos = i;
                binding.chatRecyclerView.post(() -> {
                    binding.chatRecyclerView.scrollToPosition(pos);
                    if (adapter != null) {
                        adapter.setHighlightMessageId(messageId);
                        adapter.notifyDataSetChanged();
                        binding.chatRecyclerView.postDelayed(() -> {
                            if (adapter != null && messageId.equals(adapter.getHighlightMessageId())) {
                                adapter.setHighlightMessageId(null);
                                adapter.notifyDataSetChanged();
                            }
                        }, 1500);
                    }
                });
                break;
            }
        }
    }

    private class MentionAdapter extends RecyclerView.Adapter<MentionAdapter.ViewHolder> {
        private List<ServerMember> list;

        public MentionAdapter(List<ServerMember> list) { this.list = list; }
        public void setList(List<ServerMember> list) {
            this.list = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_mention_suggestion, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ServerMember member = list.get(position);
            String displayName = member.getNickname();
            if (displayName != null && !displayName.trim().isEmpty()) {
                holder.tvUsername.setText(displayName + " (" + member.getUserName() + ")");
            } else {
                holder.tvUsername.setText(member.getUserName());
            }

            holder.ivAvatar.setColorFilter(null);
            String uid = member.getUserId();
            holder.itemView.setTag(uid);
            
            AppDependencyProvider.getFirestore().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && uid.equals(holder.itemView.getTag()) && getContext() != null) {
                        String profilePic = doc.getString("profilePic");
                        if (profilePic != null && !profilePic.isEmpty()) {
                            Glide.with(ChatFragment.this)
                                .load(profilePic)
                                .placeholder(R.drawable.ic_user)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(holder.ivAvatar);
                        } else {
                            holder.ivAvatar.setImageResource(R.drawable.ic_user);
                        }
                    }
                });

            holder.itemView.setOnClickListener(v -> insertMention(member));
        }

        @Override public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            android.widget.ImageView ivAvatar;
            android.widget.TextView tvUsername;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.ivAvatar);
                tvUsername = itemView.findViewById(R.id.tvUsername);
            }
        }
    }

    private void showReminderDialog(@Nullable Message messageToEdit, @Nullable String defaultContent) {
        if (getContext() == null) return;
        
        android.app.Dialog dialog = new android.app.Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_chat_add_reminder);
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        
        TextView tvDialogTitle = dialog.findViewById(R.id.tvDialogTitle);
        com.google.android.material.textfield.TextInputEditText etReminderContent = dialog.findViewById(R.id.etReminderContent);
        TextView tvReminderDateTime = dialog.findViewById(R.id.tvReminderDateTime);
        TextView btnPickDate = dialog.findViewById(R.id.btnPickDate);
        TextView btnSave = dialog.findViewById(R.id.btnSave);
        TextView btnCancel = dialog.findViewById(R.id.btnCancel);
        
        final java.util.Calendar calendar = java.util.Calendar.getInstance();
        
        if (messageToEdit != null) {
            tvDialogTitle.setText("Sửa lời nhắc");
            btnSave.setText("Lưu");
            etReminderContent.setText(messageToEdit.getContent());
            calendar.setTimeInMillis(messageToEdit.getReminderTime());
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            tvReminderDateTime.setText("Thời gian: " + sdf.format(new Date(messageToEdit.getReminderTime())));
        } else {
            tvDialogTitle.setText("Tạo lời nhắc");
            btnSave.setText("Tạo");
            if (defaultContent != null) {
                etReminderContent.setText(defaultContent);
            }
        }
        
        final boolean[] isTimeSelected = {messageToEdit != null};
        
        btnPickDate.setOnClickListener(v -> {
            int year = calendar.get(java.util.Calendar.YEAR);
            int month = calendar.get(java.util.Calendar.MONTH);
            int day = calendar.get(java.util.Calendar.DAY_OF_MONTH);
            
            new android.app.DatePickerDialog(requireContext(), (view1, selectedYear, selectedMonth, selectedDay) -> {
                calendar.set(java.util.Calendar.YEAR, selectedYear);
                calendar.set(java.util.Calendar.MONTH, selectedMonth);
                calendar.set(java.util.Calendar.DAY_OF_MONTH, selectedDay);
                
                int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
                int minute = calendar.get(java.util.Calendar.MINUTE);
                
                new android.app.TimePickerDialog(requireContext(), (view2, selectedHour, selectedMinute) -> {
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, selectedHour);
                    calendar.set(java.util.Calendar.MINUTE, selectedMinute);
                    calendar.set(java.util.Calendar.SECOND, 0);
                    calendar.set(java.util.Calendar.MILLISECOND, 0);
                    
                    if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                        Toast.makeText(getContext(), "Thời gian nhắc nhở phải ở tương lai!", Toast.LENGTH_SHORT).show();
                    } else {
                        isTimeSelected[0] = true;
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                        tvReminderDateTime.setText("Thời gian: " + sdf.format(calendar.getTime()));
                    }
                }, hour, minute, true).show();
                
            }, year, month, day).show();
        });
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnSave.setOnClickListener(v -> {
            String content = etReminderContent.getText().toString().trim();
            if (content.isEmpty()) {
                Toast.makeText(getContext(), "Nội dung lời nhắc không được để trống!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isTimeSelected[0]) {
                Toast.makeText(getContext(), "Vui lòng chọn thời gian nhắc nhở!", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (messageToEdit == null) {
                if (viewModel != null) {
                    Message reminder = new Message(senderId, groupId, content, System.currentTimeMillis());
                    reminder.setType("reminder");
                    reminder.setReminderTime(calendar.getTimeInMillis());
                    viewModel.sendMessage(reminder, null);
                }
            } else {
                if (viewModel != null && messageToEdit.getMessageId() != null) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("content", content);
                    updates.put("reminderTime", calendar.getTimeInMillis());
                    viewModel.updateMessage(messageToEdit.getMessageId(), updates);
                }
            }
            dialog.dismiss();
        });
        
        dialog.show();
    }

    private void checkAndTriggerMentions(Message message) {
        if (message == null || message.getContent() == null || message.getContent().trim().isEmpty()) return;
        
        // Mentions are only applicable in server channels.
        if (serverId == null) return;
        
        String content = message.getContent();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("@(\\w+)");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        
        java.util.Set<String> mentionedUsernames = new java.util.HashSet<>();
        while (matcher.find()) {
            String username = matcher.group(1);
            if (username != null) {
                mentionedUsernames.add(username.toLowerCase());
            }
        }
        
        if (mentionedUsernames.isEmpty()) return;
        
        // Query users collection in Firestore to match usernames
        for (String username : mentionedUsernames) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            String targetUid = doc.getId();
                            
                            // Don't notify yourself
                            String myUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null 
                                    ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
                            if (targetUid.equals(myUid)) continue;
                            
                            // Write notification document to target user's notifications subcollection
                            writeMentionNotificationToUser(targetUid, message);
                        }
                    }
                });
        }
    }

    private void writeMentionNotificationToUser(String targetUid, Message message) {
        String myUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null 
                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        
        // Get my username or display name
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(myUid).get()
            .addOnSuccessListener(doc -> {
                String myName = "Ai đó";
                if (doc.exists()) {
                    String name = doc.getString("username");
                    if (name != null && !name.isEmpty()) {
                        myName = name;
                    }
                }
                
                String channelName = binding.tvChannelName.getText().toString();
                String title = "Nhắc tới bạn ở #" + channelName;
                
                Map<String, Object> notif = new HashMap<>();
                String notifId = message.getMessageId() != null ? message.getMessageId() : String.valueOf(System.currentTimeMillis());
                notif.put("notificationId", notifId);
                notif.put("title", title);
                notif.put("content", message.getContent());
                notif.put("type", "mention");
                notif.put("senderId", myUid);
                notif.put("senderName", myName);
                notif.put("targetId", groupId);
                notif.put("timestamp", message.getTimestamp());
                notif.put("isRead", false);
                
                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users")
                    .document(targetUid)
                    .collection("notifications")
                    .document(notifId)
                    .set(notif);
            });
    }

    private void markNotificationsAsRead(String chatId) {
        String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null 
                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId == null || chatId == null) return;

        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users")
                .document(currentUserId)
                .collection("notifications")
                .whereEqualTo("targetId", chatId)
                .whereEqualTo("isRead", false)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        com.google.firebase.firestore.WriteBatch batch = com.google.firebase.firestore.FirebaseFirestore.getInstance().batch();
                        for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            batch.update(doc.getReference(), "isRead", true);
                        }
                        batch.commit().addOnFailureListener(e -> {
                            android.util.Log.e("ChatFragment", "Failed to mark notifications as read", e);
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ChatFragment", "Failed to fetch notifications to mark as read", e);
                });
    }

    private void setupAudioRecording() {
        binding.btnRecordAudio.setOnClickListener(v -> {
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.RECORD_AUDIO) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO);
            } else {
                startRecording();
            }
        });

        // Cancel recording (Trash button)
        binding.btnRecordCancel.setOnClickListener(v -> cancelRecordingSession());

        // Reset/Re-record (Reload button)
        binding.btnRecordReset.setOnClickListener(v -> resetRecordingSession());

        // Pause/Resume recording
        binding.btnRecordPause.setOnClickListener(v -> togglePauseResumeRecording());

        // Play/Pause preview playback
        binding.btnRecordPlay.setOnClickListener(v -> togglePreviewPlayback());

        // Send recorded audio
        binding.btnRecordSend.setOnClickListener(v -> sendRecordingSession());

        // Seek/scrub on preview waveform
        binding.layoutWaveform.setOnTouchListener((v, event) -> {
            if (com.example.se114_callingsystem.core.util.AudioPlayerManager.isPlaying(audioFilePath)) {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                    float width = v.getWidth();
                    float x = event.getX();
                    float percent = Math.max(0f, Math.min(1f, x / width));
                    int duration = com.example.se114_callingsystem.core.util.AudioPlayerManager.getDuration();
                    if (duration > 0) {
                        com.example.se114_callingsystem.core.util.AudioPlayerManager.seekTo((int) (percent * duration));
                    }
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.performClick();
                }
                return true;
            }
            return false;
        });
    }

    private void appendFile(String sourcePath, String destPath) {
        if (sourcePath == null || destPath == null) return;
        java.io.File source = new java.io.File(sourcePath);
        if (!source.exists() || source.length() == 0) return;
        
        java.io.File dest = new java.io.File(destPath);
        try (java.io.FileInputStream in = new java.io.FileInputStream(source);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dest, true)) {
            
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startRecording() {
        try {
            audioFilePath = requireContext().getCacheDir().getAbsolutePath() + "/temp_accumulated.aac";
            segmentFilePath = requireContext().getCacheDir().getAbsolutePath() + "/temp_segment.aac";
            
            try {
                new java.io.File(audioFilePath).delete();
            } catch (Exception ignored) {}
            try {
                new java.io.File(segmentFilePath).delete();
            } catch (Exception ignored) {}
            
            // Stop any playing audio
            com.example.se114_callingsystem.core.util.AudioPlayerManager.stop();
            
            mediaRecorder = new android.media.MediaRecorder(requireContext());
            mediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.AAC_ADTS);
            mediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(segmentFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            
            // State initialization
            isPaused = false;
            isPreviewMode = false;
            totalRecordedDuration = 0;
            sessionStartTime = System.currentTimeMillis();
            
            // UI setup
            binding.inputAreaPanel.setVisibility(View.GONE);
            binding.recordingPanel.setVisibility(View.VISIBLE);
            
            binding.tvRecordTimer.setText("00:00");
            
            binding.cardMicResume.setVisibility(View.VISIBLE);
            binding.btnRecordPause.setImageResource(R.drawable.ic_pause);
            binding.btnRecordPlay.setVisibility(View.GONE);
            binding.btnRecordPlay.setImageResource(R.drawable.ic_play);
            
            // Start timer
            startTimerRunnable();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Không thể khởi động ghi âm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void resumeRecordingSegment() {
        try {
            try {
                new java.io.File(segmentFilePath).delete();
            } catch (Exception ignored) {}
            
            com.example.se114_callingsystem.core.util.AudioPlayerManager.stop();
            
            mediaRecorder = new android.media.MediaRecorder(requireContext());
            mediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.AAC_ADTS);
            mediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(segmentFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            
            isPaused = false;
            isPreviewMode = false;
            sessionStartTime = System.currentTimeMillis();
            
            // UI updates
            binding.btnRecordPause.setImageResource(R.drawable.ic_pause);
            binding.btnRecordPlay.setVisibility(View.GONE);
            
            startTimerRunnable();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Không thể tiếp tục ghi âm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startTimerRunnable() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaRecorder == null && !isPreviewMode) return;
                
                long currentDuration = totalRecordedDuration;
                if (!isPaused && !isPreviewMode) {
                    currentDuration += (System.currentTimeMillis() - sessionStartTime);
                }
                
                int seconds = (int) (currentDuration / 1000);
                int minutes = seconds / 60;
                seconds = seconds % 60;
                
                if (binding != null) {
                    binding.tvRecordTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
                }
                
                timerHandler.postDelayed(this, 500);
            }
        };
        timerHandler.post(timerRunnable);
    }
    
    private void stopTimerRunnable() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerRunnable = null;
        }
    }

    private void initWaveformPreview() {
        if (binding == null) return;
        int barCount = binding.layoutWaveform.getChildCount();
        final int inactiveColor = Color.parseColor("#40FFFFFF");
        for (int i = 0; i < barCount; i++) {
            View bar = binding.layoutWaveform.getChildAt(i);
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setCornerRadius(100);
            gd.setColor(inactiveColor);
            bar.setBackground(gd);
        }
    }

    private void resetWaveformColors() {
        if (binding == null) return;
        int barCount = binding.layoutWaveform.getChildCount();
        final int inactiveColor = Color.parseColor("#40FFFFFF");
        for (int i = 0; i < barCount; i++) {
            View bar = binding.layoutWaveform.getChildAt(i);
            android.graphics.drawable.Drawable bg = bar.getBackground();
            if (bg instanceof android.graphics.drawable.GradientDrawable) {
                ((android.graphics.drawable.GradientDrawable) bg).setColor(inactiveColor);
            } else {
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setCornerRadius(100);
                gd.setColor(inactiveColor);
                bar.setBackground(gd);
            }
        }
    }

    private void startPreviewProgressUpdater() {
        if (previewProgressRunnable != null) {
            previewProgressHandler.removeCallbacks(previewProgressRunnable);
        }
        
        final int activeColor = Color.WHITE;
        final int inactiveColor = Color.parseColor("#40FFFFFF");
        
        previewProgressRunnable = new Runnable() {
            @Override
            public void run() {
                if (binding == null) return;
                
                if (com.example.se114_callingsystem.core.util.AudioPlayerManager.isPlaying(audioFilePath)) {
                    int current = com.example.se114_callingsystem.core.util.AudioPlayerManager.getCurrentPosition();
                    int duration = com.example.se114_callingsystem.core.util.AudioPlayerManager.getDuration();
                    
                    float percent = 0f;
                    if (duration > 0) {
                        percent = (float) current / duration;
                        int seconds = current / 1000;
                        int minutes = seconds / 60;
                        seconds = seconds % 60;
                        binding.tvRecordTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
                    }
                    
                    int barCount = binding.layoutWaveform.getChildCount();
                    int activeCount = (int) (percent * barCount);
                    for (int i = 0; i < barCount; i++) {
                        View bar = binding.layoutWaveform.getChildAt(i);
                        android.graphics.drawable.Drawable bg = bar.getBackground();
                        if (bg instanceof android.graphics.drawable.GradientDrawable) {
                            ((android.graphics.drawable.GradientDrawable) bg).setColor(
                                i < activeCount ? activeColor : inactiveColor
                            );
                        } else {
                            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                            gd.setCornerRadius(100);
                            gd.setColor(i < activeCount ? activeColor : inactiveColor);
                            bar.setBackground(gd);
                        }
                    }
                    previewProgressHandler.postDelayed(this, 100);
                } else {
                    binding.btnRecordPlay.setImageResource(R.drawable.ic_play);
                    resetWaveformColors();
                    int seconds = (int) (totalRecordedDuration / 1000);
                    int minutes = seconds / 60;
                    seconds = seconds % 60;
                    binding.tvRecordTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
                }
            }
        };
        previewProgressHandler.post(previewProgressRunnable);
    }

    private void stopPreviewProgressUpdater() {
        if (previewProgressRunnable != null) {
            previewProgressHandler.removeCallbacks(previewProgressRunnable);
        }
        resetWaveformColors();
        if (binding != null) {
            int seconds = (int) (totalRecordedDuration / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            binding.tvRecordTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
        }
    }

    private void togglePauseResumeRecording() {
        if (isPreviewMode) {
            resumeRecordingSegment();
            return;
        }
        
        if (mediaRecorder == null) return;
        
        try {
            if (!isPaused) {
                // Pause recording: stop current segment and append it to accumulated file
                stopTimerRunnable();
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
                
                totalRecordedDuration += (System.currentTimeMillis() - sessionStartTime);
                appendFile(segmentFilePath, audioFilePath);
                try {
                    new java.io.File(segmentFilePath).delete();
                } catch (Exception ignored) {}
                
                isPaused = true;
                
                // UI updates
                binding.btnRecordPause.setImageResource(R.drawable.ic_mic_on);
                binding.btnRecordPlay.setVisibility(View.VISIBLE);
                binding.btnRecordPlay.setImageResource(R.drawable.ic_play);
                initWaveformPreview();
            } else {
                // Resume recording segment
                resumeRecordingSegment();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Không thể tạm dừng/tiếp tục ghi âm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecorderForPreview() {
        if (mediaRecorder != null) {
            try {
                if (!isPaused) {
                    totalRecordedDuration += (System.currentTimeMillis() - sessionStartTime);
                }
                mediaRecorder.stop();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    mediaRecorder.release();
                } catch (Exception ignored) {}
                mediaRecorder = null;
            }
        }
        
        appendFile(segmentFilePath, audioFilePath);
        try {
            new java.io.File(segmentFilePath).delete();
        } catch (Exception ignored) {}
        
        isPreviewMode = true;
        
        // UI updates for preview mode
        binding.cardMicResume.setVisibility(View.VISIBLE);
        binding.btnRecordPause.setImageResource(R.drawable.ic_mic_on);
        binding.btnRecordPlay.setVisibility(View.VISIBLE);
        binding.btnRecordPlay.setImageResource(R.drawable.ic_play);
        initWaveformPreview();
        
        // Format the timer to show total duration
        int seconds = (int) (totalRecordedDuration / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        binding.tvRecordTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }

    private void togglePreviewPlayback() {
        if (!isPreviewMode) {
            stopRecorderForPreview();
        }
        
        if (com.example.se114_callingsystem.core.util.AudioPlayerManager.isPlaying(audioFilePath)) {
            com.example.se114_callingsystem.core.util.AudioPlayerManager.stop();
        } else {
            binding.btnRecordPlay.setImageResource(R.drawable.ic_pause);
            com.example.se114_callingsystem.core.util.AudioPlayerManager.play(audioFilePath, new com.example.se114_callingsystem.core.util.AudioPlayerManager.AudioPlayerListener() {
                @Override
                public void onStart() {
                    if (binding != null) {
                        binding.btnRecordPlay.setImageResource(R.drawable.ic_pause);
                        startPreviewProgressUpdater();
                    }
                }

                @Override
                public void onStop() {
                    if (binding != null) {
                        binding.btnRecordPlay.setImageResource(R.drawable.ic_play);
                        stopPreviewProgressUpdater();
                    }
                }

                @Override
                public void onComplete() {
                    if (binding != null) {
                        binding.btnRecordPlay.setImageResource(R.drawable.ic_play);
                        stopPreviewProgressUpdater();
                    }
                }

                @Override
                public void onError(String error) {
                    if (binding != null) {
                        binding.btnRecordPlay.setImageResource(R.drawable.ic_play);
                        stopPreviewProgressUpdater();
                        Toast.makeText(getContext(), "Lỗi phát thử âm thanh: " + error, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void resetRecordingSession() {
        stopTimerRunnable();
        stopPreviewProgressUpdater();
        com.example.se114_callingsystem.core.util.AudioPlayerManager.stop();
        
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception ignored) {}
            try {
                mediaRecorder.release();
            } catch (Exception ignored) {}
            mediaRecorder = null;
        }
        
        if (audioFilePath != null) {
            try {
                new java.io.File(audioFilePath).delete();
            } catch (Exception ignored) {}
            audioFilePath = null;
        }
        if (segmentFilePath != null) {
            try {
                new java.io.File(segmentFilePath).delete();
            } catch (Exception ignored) {}
            segmentFilePath = null;
        }
        
        isPaused = false;
        isPreviewMode = false;
        totalRecordedDuration = 0;
        
        startRecording();
    }

    private void cancelRecordingSession() {
        stopTimerRunnable();
        stopPreviewProgressUpdater();
        com.example.se114_callingsystem.core.util.AudioPlayerManager.stop();
        
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception ignored) {}
            try {
                mediaRecorder.release();
            } catch (Exception ignored) {}
            mediaRecorder = null;
        }
        
        if (audioFilePath != null) {
            try {
                new java.io.File(audioFilePath).delete();
            } catch (Exception ignored) {}
            audioFilePath = null;
        }
        if (segmentFilePath != null) {
            try {
                new java.io.File(segmentFilePath).delete();
            } catch (Exception ignored) {}
            segmentFilePath = null;
        }
        
        isPaused = false;
        isPreviewMode = false;
        totalRecordedDuration = 0;
        
        binding.recordingPanel.setVisibility(View.GONE);
        binding.inputAreaPanel.setVisibility(View.VISIBLE);
    }

    private void sendRecordingSession() {
        stopTimerRunnable();
        stopPreviewProgressUpdater();
        com.example.se114_callingsystem.core.util.AudioPlayerManager.stop();
        
        if (mediaRecorder != null) {
            try {
                if (!isPaused) {
                    totalRecordedDuration += (System.currentTimeMillis() - sessionStartTime);
                }
                mediaRecorder.stop();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    mediaRecorder.release();
                } catch (Exception ignored) {}
                mediaRecorder = null;
            }
            appendFile(segmentFilePath, audioFilePath);
            try {
                new java.io.File(segmentFilePath).delete();
            } catch (Exception ignored) {}
        }
        
        if (totalRecordedDuration < 1000) {
            Toast.makeText(getContext(), "Tin nhắn quá ngắn", Toast.LENGTH_SHORT).show();
            cancelRecordingSession();
            return;
        }
        
        if (audioFilePath != null) {
            uploadAudioToCloudinary(Uri.fromFile(new java.io.File(audioFilePath)));
        }
        
        isPaused = false;
        isPreviewMode = false;
        totalRecordedDuration = 0;
        
        binding.recordingPanel.setVisibility(View.GONE);
        binding.inputAreaPanel.setVisibility(View.VISIBLE);
    }

    private void uploadAudioToCloudinary(Uri fileUri) {
        if (getContext() == null) return;
        if (!com.example.se114_callingsystem.core.util.NetworkMonitor.isNetworkAvailable(getContext())) {
            Toast.makeText(getContext(), "Không có kết nối mạng. Không thể gửi tin nhắn thoại.", Toast.LENGTH_SHORT).show();
            return;
        }
        ProgressDialog pd = new ProgressDialog(getContext());
        pd.setMessage("Đang gửi tin nhắn thoại...");
        pd.show();
        MediaManager.get().upload(fileUri).option("resource_type", "video").callback(new UploadCallback() {
            @Override public void onStart(String requestId) {}
            @Override public void onProgress(String requestId, long bytes, long totalBytes) {}
            @Override public void onSuccess(String requestId, Map resultData) {
                pd.dismiss();
                sendMediaMessage((String) resultData.get("secure_url"), "audio");
                try {
                    new java.io.File(audioFilePath).delete();
                } catch (Exception ignored) {}
            }
            @Override public void onError(String requestId, ErrorInfo error) {
                pd.dismiss();
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Lỗi tải âm thanh: " + error.getDescription(), Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onReschedule(String requestId, ErrorInfo error) {}
        }).dispatch();
    }

    private void showGifSearchDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#313338"));
        layout.setPadding(32, 32, 32, 32);

        android.widget.EditText etSearch = new android.widget.EditText(requireContext());
        etSearch.setHint("Tìm kiếm GIF trên Giphy...");
        etSearch.setHintTextColor(Color.parseColor("#949BA4"));
        etSearch.setTextColor(Color.WHITE);
        etSearch.setBackgroundResource(R.drawable.bg_chat_input);
        etSearch.setPadding(32, 24, 32, 24);
        etSearch.setSingleLine(true);
        etSearch.setTextSize(14f);

        RecyclerView rvGifs = new RecyclerView(requireContext());
        rvGifs.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2));
        rvGifs.setPadding(0, 16, 0, 0);

        layout.addView(etSearch);
        layout.addView(rvGifs);

        dialog.setContentView(layout);
        
        List<String> gifUrls = new ArrayList<>();
        
        class GifViewHolder extends RecyclerView.ViewHolder {
            android.widget.ImageView ivGif;
            public GifViewHolder(@NonNull View itemView) {
                super(itemView);
                ivGif = (android.widget.ImageView) itemView;
                ivGif.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 300));
                ivGif.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                ivGif.setPadding(4, 4, 4, 4);
            }
        }

        RecyclerView.Adapter<GifViewHolder> gifAdapter = new RecyclerView.Adapter<GifViewHolder>() {
            @NonNull
            @Override
            public GifViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                android.widget.ImageView imageView = new android.widget.ImageView(parent.getContext());
                return new GifViewHolder(imageView);
            }

            @Override
            public void onBindViewHolder(@NonNull GifViewHolder holder, int position) {
                String url = gifUrls.get(position);
                Glide.with(ChatFragment.this)
                        .asGif()
                        .load(url)
                        .placeholder(R.drawable.bg_circle_transparent)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(holder.ivGif);
                holder.itemView.setOnClickListener(v -> {
                    dialog.dismiss();
                    sendMediaMessage(url, "image");
                });
            }

            @Override
            public int getItemCount() {
                return gifUrls.size();
            }
        };
        rvGifs.setAdapter(gifAdapter);

        Runnable searchGif = () -> {
            String query = etSearch.getText().toString().trim();
            String urlStr = "https://api.giphy.com/v1/gifs/trending?api_key=T7ziYgAkCNMJhQXUhjjc4siv7Tamvcb7&limit=25";
            if (!query.isEmpty()) {
                try {
                    urlStr = "https://api.giphy.com/v1/gifs/search?api_key=T7ziYgAkCNMJhQXUhjjc4siv7Tamvcb7&limit=25&q=" + java.net.URLEncoder.encode(query, "UTF-8");
                } catch (Exception ignored) {}
            }
            final String requestUrl = urlStr;
            new Thread(() -> {
                try {
                    java.net.URL url = new java.net.URL(requestUrl);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    org.json.JSONObject json = new org.json.JSONObject(response.toString());
                    org.json.JSONArray data = json.getJSONArray("data");
                    List<String> newUrls = new ArrayList<>();
                    for (int i = 0; i < data.length(); i++) {
                        org.json.JSONObject gif = data.getJSONObject(i);
                        org.json.JSONObject images = gif.getJSONObject("images");
                        org.json.JSONObject fixedWidth = images.getJSONObject("fixed_width");
                        String gifUrl = fixedWidth.getString("url");
                        newUrls.add(gifUrl);
                    }

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            gifUrls.clear();
                            gifUrls.addAll(newUrls);
                            gifAdapter.notifyDataSetChanged();
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        };

        final Runnable[] searchRunnable = {null};
        final android.os.Handler searchHandler = new android.os.Handler();
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable[0] != null) {
                    searchHandler.removeCallbacks(searchRunnable[0]);
                }
                searchRunnable[0] = searchGif;
                searchHandler.postDelayed(searchRunnable[0], 500);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        searchGif.run();

        dialog.show();
    }

    @Override
    public void onStop() {
        super.onStop();
        com.example.se114_callingsystem.core.util.AudioPlayerManager.stop();
        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (Exception ignored) {}
            mediaRecorder = null;
        }
        stopTimerRunnable();
        stopPreviewProgressUpdater();
        if (segmentFilePath != null) {
            try {
                new java.io.File(segmentFilePath).delete();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        setTypingStatus(false);
        stopPreviewProgressUpdater();
        if (viewModel != null) {
            viewModel.stopChatSession();
        }
        if (dmNicknamesListener != null) {
            dmNicknamesListener.remove();
            dmNicknamesListener = null;
        }
        activeChatId = null;
        binding = null;
    }

    private void loadDMParticipants() {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null ? 
            FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        if (currentUid.isEmpty()) return;

        serverMembers.clear();
        
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        
        db.collection("users").document(currentUid).get()
            .addOnSuccessListener(docMe -> {
                if (docMe.exists() && binding != null) {
                    String username = docMe.getString("username");
                    ServerMember me = new ServerMember(currentUid, username, "online");
                    serverMembers.add(me);
                    
                    String otherUid = null;
                    if (groupId != null && groupId.startsWith("dm_")) {
                        String[] parts = groupId.split("_");
                        if (parts.length == 3) {
                            otherUid = parts[1].equals(currentUid) ? parts[2] : parts[1];
                        }
                    }
                    
                    if (otherUid != null) {
                        String finalOtherUid = otherUid;
                        db.collection("users").document(finalOtherUid).get()
                            .addOnSuccessListener(docOther -> {
                                if (docOther.exists() && binding != null) {
                                    String otherUsername = docOther.getString("username");
                                    ServerMember other = new ServerMember(finalOtherUid, otherUsername, "online");
                                    serverMembers.add(other);
                                    
                                    listenToDMNicknames();
                                }
                            });
                    } else {
                        listenToDMNicknames();
                    }
                }
            });
    }

    private void listenToDMNicknames() {
        if (groupId == null) return;
        if (dmNicknamesListener != null) {
            dmNicknamesListener.remove();
        }
        
        if (adapter != null) {
            adapter.setServerMembers(serverMembers);
            adapter.notifyDataSetChanged();
        }
        if (mentionAdapter != null) {
            mentionAdapter.setList(new ArrayList<>(serverMembers));
        }

        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        dmNicknamesListener = db.collection("Channels").document(groupId)
            .addSnapshotListener((snapshot, error) -> {
                if (error != null || snapshot == null || !snapshot.exists()) return;
                
                java.util.Map<String, Object> nicknames = (java.util.Map<String, Object>) snapshot.get("nicknames");
                if (nicknames != null) {
                    for (ServerMember m : serverMembers) {
                        String uid = m.getUserId();
                        if (nicknames.containsKey(uid)) {
                            m.setNickname((String) nicknames.get(uid));
                        }
                    }
                    if (adapter != null) {
                        adapter.setServerMembers(serverMembers);
                        adapter.notifyDataSetChanged();
                    }
                    if (mentionAdapter != null) {
                        mentionAdapter.setList(new ArrayList<>(serverMembers));
                    }
                    
                    updateChatHeaderTitle(nicknames);
                }
            });
    }

    private void updateChatHeaderTitle(java.util.Map<String, Object> nicknames) {
        if (binding == null) return;
        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null ? 
            FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        String otherUid = null;
        if (groupId != null && groupId.startsWith("dm_")) {
            String[] parts = groupId.split("_");
            if (parts.length == 3) {
                otherUid = parts[1].equals(currentUid) ? parts[2] : parts[1];
            }
        }
        if (otherUid != null && nicknames.containsKey(otherUid)) {
            String nickname = (String) nicknames.get(otherUid);
            if (nickname != null && !nickname.trim().isEmpty()) {
                binding.tvChannelName.setText(nickname);
                binding.edtMessage.setHint("Message " + nickname);
            }
        }
    }

    private void initiateDirectCall(String type) {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null ? 
            FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        String otherUid = null;
        if (groupId != null && groupId.startsWith("dm_")) {
            String[] parts = groupId.split("_");
            if (parts.length == 3) {
                otherUid = parts[1].equals(currentUid) ? parts[2] : parts[1];
            }
        }
        if (otherUid == null || currentUid.isEmpty()) return;

        String callerName = "Friend";
        for (ServerMember member : serverMembers) {
            if (currentUid.equals(member.getUserId())) {
                callerName = member.getNickname() != null && !member.getNickname().isEmpty() ? 
                    member.getNickname() : member.getUserName();
                break;
            }
        }

        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        java.util.Map<String, Object> callMap = new java.util.HashMap<>();
        callMap.put("callerId", currentUid);
        callMap.put("callerName", callerName);
        callMap.put("channelName", groupId);
        callMap.put("callType", type);
        callMap.put("status", "ringing");
        callMap.put("timestamp", System.currentTimeMillis());

        String finalOtherUid = otherUid;
        String finalCallerName = callerName;
        db.collection("users").document(otherUid).collection("incomingCall").document("activeCall")
            .set(callMap)
            .addOnSuccessListener(aVoid -> {
                if (getContext() != null) {
                    Intent intent = new Intent(requireContext(), com.example.se114_callingsystem.features.call.ui.CallActivity.class);
                    intent.putExtra("CALL_CHANNEL_NAME", groupId);
                    intent.putExtra("SERVER_ID", (String) null);
                    intent.putExtra("SERVER_COLOR", serverColor);
                    intent.putExtra("IS_CALLER", true);
                    intent.putExtra("CALL_TYPE", type);
                    startActivity(intent);
                }
            })
            .addOnFailureListener(e -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Không thể khởi tạo cuộc gọi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        com.example.se114_callingsystem.core.util.AudioPlayerManager.stop();
    }
}
