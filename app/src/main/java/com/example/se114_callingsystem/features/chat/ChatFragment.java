package com.example.se114_callingsystem.features.chat;

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

public class ChatFragment extends Fragment {

    private static final String TAG = "ChatFragment";
    public static String activeChatId = null;

    private FragmentChatBinding binding;
    private ChatAdapter adapter;
    private List<Message> messageList = new ArrayList<>();
    private List<String> pendingMessageIds = new java.util.ArrayList<>();
    
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String> filePickerLauncher;
    private Message messageToReply = null;
    private String lastMessageId = null;

    private String groupId;
    private DatabaseReference groupChatRef;
    private String senderId;
    private String serverColor = "#5865F2"; // Discord Blurple default

    private String serverId;
    private List<ServerMember> serverMembers = new ArrayList<>();
    private List<ServerMember> filteredMembers = new ArrayList<>();
    private com.google.firebase.firestore.ListenerRegistration membersListener;
    private MentionAdapter mentionAdapter;

    private android.os.Handler typingHandler = new android.os.Handler();
    private Runnable typingStopRunnable = () -> setTypingStatus(false);
    private boolean isTyping = false;
    private ValueEventListener typingListener;
    private List<android.animation.ObjectAnimator> dotAnimators = new ArrayList<>();



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

        // Retrieve Arguments passed via navigation bundle
        if (getArguments() != null) {
            groupId = getArguments().getString("CHAT_ID");
            String channelName = getArguments().getString("CHAT_NAME");
            serverColor = getArguments().getString("SERVER_COLOR", "#5865F2");
            serverId = getArguments().getString("SERVER_ID");

            if (channelName != null) {
                if (serverId == null) {
                    // Chat DM 1-1 (Private Chat)
                    binding.tvChannelHash.setVisibility(View.GONE);
                    binding.ivOnlineStatus.setVisibility(View.VISIBLE);
                    binding.tvChannelName.setText(channelName);
                    binding.edtMessage.setHint("Message " + channelName);
                } else {
                    // Chat Server Channel
                    binding.tvChannelHash.setVisibility(View.VISIBLE);
                    binding.ivOnlineStatus.setVisibility(View.GONE);
                    binding.tvChannelName.setText(channelName.toLowerCase());
                    binding.edtMessage.setHint("Message #" + channelName.toLowerCase());
                }
            }
        }

        activeChatId = groupId;

        // QUAN TRỌNG: setupRecyclerView() phải được gọi TRƯỚC listenForMessages()
        // để adapter không bị null khi Firebase fire onDataChange từ cache
        setupRecyclerView();
        setupClickListeners();
        setupMentionSuggestions();
        setupServerMembersListener();
        setupTypingIndicator();

        if (groupId != null) {
            groupChatRef = Firebase.getDatabase().getReference("chats").child(groupId);
            listenForMessages(groupId);
            markNotificationsAsRead(groupId);
        }
    }



    private void setupRecyclerView() {
        adapter = new ChatAdapter(messageList, serverColor, new ChatAdapter.OnChatInteractListener() {
            @Override
            public void onReply(Message message) { showReplyUI(message); }
            @Override
            public void onDelete(Message message) {
                if (groupChatRef != null && message.getMessageId() != null) {
                    groupChatRef.child(message.getMessageId()).child("deleted").setValue(true);
                }
            }
            @Override
            public void onReact(Message message, String emoji) {
                if (groupChatRef != null && message.getMessageId() != null) {
                    groupChatRef.child(message.getMessageId()).child("reactionEmoji").setValue(emoji);
                }
            }
            @Override
            public void onPinToggle(Message message) {
                if (groupChatRef != null && message.getMessageId() != null) {
                    groupChatRef.child(message.getMessageId()).child("pinned").setValue(!message.isPinned());
                }
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
            String[] options = {"📷 Send Image", "📎 Send File", "⏰ Đặt lời nhắc"};
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Upload Media & Options")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) imagePickerLauncher.launch("image/*");
                        else if (which == 1) filePickerLauncher.launch("*/*");
                        else showReminderDialog(null, null);
                    })
                    .show();
        });

        binding.btnBack.setOnClickListener(v -> {
            Navigation.findNavController(v).popBackStack();
        });

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

        // DEBUG: log các giá trị quan trọng
        android.util.Log.d(TAG, "sendMessage: msg='" + msg + "', groupId=" + groupId
                + ", groupChatRef=" + groupChatRef + ", senderId=" + senderId);

        if (msg.isEmpty()) {
            android.util.Log.w(TAG, "sendMessage: message is empty, skip");
            return;
        }
        if (groupChatRef == null) {
            android.util.Log.e(TAG, "sendMessage: groupChatRef is NULL! groupId=" + groupId);
            if (getContext() != null)
                Toast.makeText(getContext(), "Lỗi: Không tìm thấy phòng chat (groupChatRef null)", Toast.LENGTH_LONG).show();
            return;
        }

        Message messageModel = new Message(senderId, groupId, msg, System.currentTimeMillis());
        if (messageToReply != null) {
            messageModel.setRepliedToContent(messageToReply.getContent());
            messageModel.setRepliedToType(messageToReply.getType());
            messageModel.setRepliedToMessageId(messageToReply.getMessageId());
            messageToReply = null;
            binding.tvReplyingToLayout.setVisibility(View.GONE);
        }

        DatabaseReference newMsgRef = groupChatRef.push();
        final String messageId = newMsgRef.getKey();
        messageModel.setMessageId(messageId);

        if (getContext() != null && !com.example.se114_callingsystem.core.util.NetworkMonitor.isNetworkAvailable(getContext())) {
            pendingMessageIds.add(messageId);
            messageModel.setPending(true);
        }

        newMsgRef.setValue(messageModel)
            .addOnSuccessListener(aVoid -> {
                android.util.Log.d(TAG, "sendMessage: SUCCESS, messageId=" + messageId);
                pendingMessageIds.remove(messageId);
                if (adapter != null) adapter.notifyDataSetChanged();
                checkAndTriggerMentions(messageModel);
            })
            .addOnFailureListener(e -> {
                android.util.Log.e(TAG, "sendMessage: FAILED - " + e.getMessage(), e);
                if (getContext() != null)
                    Toast.makeText(getContext(), "Gửi thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });

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
        MediaManager.get().upload(fileUri).option("resource_type", "auto").callback(new UploadCallback() {
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
        if (groupChatRef == null) return;
        Message model = new Message(senderId, groupId, fileUrl, System.currentTimeMillis());
        model.setType(type);
        if (messageToReply != null) {
            model.setRepliedToContent(messageToReply.getContent());
            model.setRepliedToType(messageToReply.getType());
            model.setRepliedToMessageId(messageToReply.getMessageId());
            messageToReply = null;
            if (binding != null) binding.tvReplyingToLayout.setVisibility(View.GONE);
        }
        DatabaseReference newMsgRef = groupChatRef.push();
        String messageId = newMsgRef.getKey();
        model.setMessageId(messageId);
        newMsgRef.setValue(model).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                checkAndTriggerMentions(model);
            }
        });
    }

    private ValueEventListener messagesListener;

    private void listenForMessages(String chatRoomID) {
        messagesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null || adapter == null) {
                    android.util.Log.w(TAG, "onDataChange: binding or adapter is null, skipping");
                    return;
                }

                messageList.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    Message model = data.getValue(Message.class);
                    if (model != null) {
                        model.setMessageId(data.getKey());
                        if (pendingMessageIds.contains(data.getKey())) {
                            model.setPending(true);
                        }
                        messageList.add(model);
                    }
                }
                android.util.Log.d(TAG, "onDataChange: loaded " + messageList.size() + " messages");
                adapter.notifyDataSetChanged();

                updatePinnedMessageHeader();

                if (!messageList.isEmpty()) {
                    Message lastMsg = messageList.get(messageList.size() - 1);
                    String lastMsgId = lastMsg.getMessageId();
                    if (lastMessageId == null || !lastMsgId.equals(lastMessageId)) {
                        lastMessageId = lastMsgId;
                        // Dùng post() để scroll SAU khi RecyclerView layout xong
                        binding.chatRecyclerView.post(() -> {
                            if (binding != null) {
                                binding.chatRecyclerView.scrollToPosition(messageList.size() - 1);
                            }
                        });
                    }
                } else {
                    lastMessageId = null;
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.util.Log.e(TAG, "listenForMessages cancelled: " + error.getMessage());
            }
        };
        groupChatRef.addValueEventListener(messagesListener);
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

            final String pinnedMessageId = latestPinned.getMessageId();
            layoutPinnedMessage.setOnClickListener(v -> {
                scrollToMessage(pinnedMessageId);
            });
        } else {
            layoutPinnedMessage.setVisibility(View.GONE);
        }
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

    private void setupServerMembersListener() {
        if (serverId == null || serverId.isEmpty()) return;
        
        com.google.firebase.firestore.FirebaseFirestore dbFS = AppDependencyProvider.getFirestore();
        membersListener = dbFS.collection("servers").document(serverId).collection("members")
            .addSnapshotListener((snapshots, error) -> {
                if (error != null) return;
                if (snapshots != null && binding != null) {
                    serverMembers.clear();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots.getDocuments()) {
                        ServerMember m = doc.toObject(ServerMember.class);
                        if (m != null) {
                            serverMembers.add(m);
                        }
                    }
                    if (adapter != null) {
                        adapter.setServerMembers(serverMembers);
                    }
                    if (mentionAdapter != null) {
                        mentionAdapter.setList(new ArrayList<>(serverMembers));
                    }
                }
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
        if (groupId == null || senderId == null) return;
        isTyping = typing;
        DatabaseReference ref = AppDependencyProvider.getRealtimeDatabase().getReference("chat_typing").child(groupId).child(senderId);
        if (typing) {
            ref.setValue(true);
            ref.onDisconnect().removeValue();
        } else {
            ref.removeValue();
        }
    }

    private void setupTypingIndicator() {
        if (groupId == null) return;
        DatabaseReference typingRef = AppDependencyProvider.getRealtimeDatabase().getReference("chat_typing").child(groupId);
        typingListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;
                List<String> typingUsers = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    if (child.getValue(Boolean.class) != null && child.getValue(Boolean.class)) {
                        String uid = child.getKey();
                        if (uid != null && !uid.equals(senderId)) {
                            typingUsers.add(uid);
                        }
                    }
                }
                
                if (typingUsers.isEmpty()) {
                    hideTypingIndicator();
                } else {
                    showTypingIndicator(typingUsers);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        typingRef.addValueEventListener(typingListener);
    }

    private void showTypingIndicator(List<String> typingUsers) {
        android.widget.LinearLayout typingLayout = binding.getRoot().findViewById(R.id.typingIndicatorLayout);
        android.widget.TextView tvTypingStatus = binding.getRoot().findViewById(R.id.tvTypingStatus);
        if (typingLayout.getVisibility() == View.GONE) {
            typingLayout.setVisibility(View.VISIBLE);
            typingLayout.setTranslationY(50f);
            typingLayout.setAlpha(0f);
            typingLayout.animate().translationY(0f).alpha(1f).setDuration(300).start();
            startDotsAnimation();
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
            tvTypingStatus.setText(name + " is typing...");
        } else {
            tvTypingStatus.setText("Several people are typing...");
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
                stopDotsAnimation();
            }).start();
        }
    }

    private void startDotsAnimation() {
        if (!dotAnimators.isEmpty()) return;
        View dot1 = binding.getRoot().findViewById(R.id.dot1);
        View dot2 = binding.getRoot().findViewById(R.id.dot2);
        View dot3 = binding.getRoot().findViewById(R.id.dot3);
        
        dotAnimators.add(animateDot(dot1, 0));
        dotAnimators.add(animateDot(dot2, 150));
        dotAnimators.add(animateDot(dot3, 300));
    }

    private android.animation.ObjectAnimator animateDot(View dot, int delay) {
        android.animation.ObjectAnimator animator = android.animation.ObjectAnimator.ofFloat(dot, "translationY", 0f, -8f, 0f);
        animator.setDuration(600);
        animator.setStartDelay(delay);
        animator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        animator.start();
        return animator;
    }

    private void stopDotsAnimation() {
        for (android.animation.ObjectAnimator anim : dotAnimators) {
            anim.cancel();
        }
        dotAnimators.clear();
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
        
        View view = getLayoutInflater().inflate(R.layout.dialog_chat_add_reminder, null);
        com.google.android.material.textfield.TextInputEditText etReminderContent = view.findViewById(R.id.etReminderContent);
        TextView tvReminderDateTime = view.findViewById(R.id.tvReminderDateTime);
        Button btnPickDate = view.findViewById(R.id.btnPickDate);
        
        final java.util.Calendar calendar = java.util.Calendar.getInstance();
        
        if (messageToEdit != null) {
            etReminderContent.setText(messageToEdit.getContent());
            calendar.setTimeInMillis(messageToEdit.getReminderTime());
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            tvReminderDateTime.setText("Thời gian: " + sdf.format(new Date(messageToEdit.getReminderTime())));
        } else if (defaultContent != null) {
            etReminderContent.setText(defaultContent);
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
        
        String title = (messageToEdit == null) ? "Tạo lời nhắc" : "Sửa lời nhắc";
        String positiveText = (messageToEdit == null) ? "Tạo" : "Lưu";
        
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(view)
                .setPositiveButton(positiveText, (dialog, which) -> {
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
                        if (groupChatRef != null) {
                            Message reminder = new Message(senderId, groupId, content, System.currentTimeMillis());
                            reminder.setType("reminder");
                            reminder.setReminderTime(calendar.getTimeInMillis());
                            groupChatRef.push().setValue(reminder);
                        }
                    } else {
                        if (groupChatRef != null && messageToEdit.getMessageId() != null) {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("content", content);
                            updates.put("reminderTime", calendar.getTimeInMillis());
                            groupChatRef.child(messageToEdit.getMessageId()).updateChildren(updates);
                        }
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        setTypingStatus(false);
        if (typingListener != null && groupId != null) {
            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("chat_typing").child(groupId).removeEventListener(typingListener);
        }
        // Remove messages listener để tránh memory leak
        if (messagesListener != null && groupChatRef != null) {
            groupChatRef.removeEventListener(messagesListener);
        }
        stopDotsAnimation();
        activeChatId = null;
        binding = null;
        if (membersListener != null) {
            membersListener.remove();
        }
    }
}


