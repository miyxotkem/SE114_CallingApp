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
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.databinding.FragmentChatBinding;
import com.example.se114_callingsystem.core.model.Firebase;
import com.example.se114_callingsystem.core.model.Message;
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

public class ChatFragment extends Fragment {

    private static final String TAG = "ChatFragment";
    public static String activeChatId = null;

    private FragmentChatBinding binding;
    private ChatAdapter adapter;
    private List<Message> messageList = new ArrayList<>();
    
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



    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        senderId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "UNKNOWN";
        initCloudinary();

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

        if (groupId != null) {
            groupChatRef = Firebase.getDatabase().getReference("chats").child(groupId);
            listenForMessages(groupId);
        }

        setupRecyclerView();
        setupClickListeners();
        setupMentionSuggestions();
        setupServerMembersListener();
    }

    private void initCloudinary() {
        Map config = new HashMap();
        config.put("cloud_name", "dxoukp0yb");
        config.put("api_key", "359217744855482");
        config.put("api_secret", "eTG0UvW_hdsHm4hl0r2XJCvidR0");
        try {
            MediaManager.init(requireContext(), config);
        } catch (IllegalStateException e) {}
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
            public void onEditReminder(Message message) {}
        });

        binding.chatRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
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
            String[] options = {"ðŸ“· Send Image", "ðŸ“Ž Send File"};
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Upload Media")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) imagePickerLauncher.launch("image/*");
                        else filePickerLauncher.launch("*/*");
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
        if (!msg.isEmpty() && groupChatRef != null) {
            Message messageModel = new Message(senderId, groupId, msg, System.currentTimeMillis());
            if (messageToReply != null) {
                messageModel.setRepliedToContent(messageToReply.getContent());
                messageModel.setRepliedToType(messageToReply.getType());
                messageToReply = null;
                binding.tvReplyingToLayout.setVisibility(View.GONE);
            }
            groupChatRef.push().setValue(messageModel).addOnSuccessListener(aVoid -> {
                if (binding != null) binding.edtMessage.setText("");
            });
        }
    }

    private void uploadToCloudinary(Uri fileUri, String type) {
        if (getContext() == null) return;
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
            messageToReply = null;
            if (binding != null) binding.tvReplyingToLayout.setVisibility(View.GONE);
        }
        groupChatRef.push().setValue(model);
    }

    private void listenForMessages(String chatRoomID) {
        groupChatRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;
                
                messageList.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    Message model = data.getValue(Message.class);
                    if (model != null) {
                        model.setMessageId(data.getKey());
                        messageList.add(model);
                    }
                }
                adapter.notifyDataSetChanged();

                
                if (!messageList.isEmpty()) {
                    Message lastMsg = messageList.get(messageList.size() - 1);
                    String lastMsgId = lastMsg.getMessageId();
                    if (lastMessageId == null || !lastMsgId.equals(lastMessageId)) {
                        binding.chatRecyclerView.scrollToPosition(messageList.size() - 1);
                        lastMessageId = lastMsgId;
                    }
                } else {
                    lastMessageId = null;
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showReplyUI(Message message) {
        if (message.isDeleted() || binding == null) return;
        messageToReply = message;
        binding.tvReplyingToLayout.setVisibility(View.VISIBLE);
        String type = message.getType();
        if ("image".equals(type)) {
            binding.tvReplyingToText.setText("Replying to: ðŸ“· Image");
            binding.cardReplyPreviewImage.setVisibility(View.VISIBLE);
            Glide.with(this).load(message.getContent()).centerCrop().into(binding.ivReplyPreview);
        } else if ("file".equals(type)) {
            String fileName = "Attachment";
            try { fileName = message.getContent().substring(message.getContent().lastIndexOf('/') + 1); } catch (Exception e) {}
            binding.tvReplyingToText.setText("Replying to: ðŸ“Ž " + fileName);
            binding.cardReplyPreviewImage.setVisibility(View.GONE);
        } else {
            String content = message.getContent();
            binding.tvReplyingToText.setText("Replying to: " + (content.length() > 40 ? content.substring(0, 40) + "..." : content));
            binding.cardReplyPreviewImage.setVisibility(View.GONE);
        }
        binding.edtMessage.requestFocus();
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
        
        com.google.firebase.firestore.FirebaseFirestore dbFS = com.google.firebase.firestore.FirebaseFirestore.getInstance();
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
            
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && uid.equals(holder.itemView.getTag()) && getContext() != null) {
                        String profilePic = doc.getString("profilePic");
                        if (profilePic != null && !profilePic.isEmpty()) {
                            Glide.with(ChatFragment.this)
                                .load(profilePic)
                                .placeholder(R.drawable.ic_user)
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        activeChatId = null;
        binding = null;
        if (membersListener != null) {
            membersListener.remove();
        }
    }
}


