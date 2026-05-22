package com.example.se114_callingsystem.chat;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.model.Firebase;
import com.example.se114_callingsystem.model.Message;
import com.example.se114_callingsystem.model.Server;
import com.example.se114_callingsystem.model.ServerMember;
import com.example.se114_callingsystem.util.ThemeHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
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

public class ChatDetailActivity extends AppCompatActivity {
    public static String activeChatId = null;

    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private List<Message> messageList = new ArrayList<>();
    private ActivityResultLauncher<android.content.Intent> chatInfoLauncher;

    // Nút đính kèm Gen Z (gom 2 nút cũ thành 1)
    private ImageButton btnAttachHome;
    private MaterialCardView messageCard; // Thẻ bao quanh ô nhập liệu

    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String> filePickerLauncher;
    private EditText edtMessage;
    private ImageButton btnSend;
    private ImageView btnBack;
    private TextView tvChannelName;
    private TextView tvChannelHash; // Dấu # cách điệu

    private View tvReplyingToLayout;
    private TextView tvReplyingToText;
    private ImageView ivReplyPreview;
    private MaterialCardView cardReplyPreviewImage;
    private Message messageToReply = null;
    private int mentionStartIndex = -1;
    private String lastMessageId = null;

    private String groupId;
    private DatabaseReference groupChatRef;
    private String senderId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "UNKNOWN";
    private String serverColor = "#FF007F"; // Mặc định là màu Cyber Magenta

    // Trình gợi ý nhắc tên (Mention Suggestions)
    private String serverId;
    private List<com.example.se114_callingsystem.model.ServerMember> serverMembers = new ArrayList<>();
    private List<com.example.se114_callingsystem.model.ServerMember> filteredMembers = new ArrayList<>();
    private com.google.firebase.firestore.ListenerRegistration membersListener;
    private MaterialCardView cardMentionSuggestions;
    private RecyclerView rvMentionSuggestions;
    private MentionAdapter mentionAdapter;

    // Tính năng ghim tin nhắn
    private ImageView btnPinnedMessages;
    private PinnedMessagesAdapter pinnedAdapter;
    private List<Message> currentPinnedMessages = new ArrayList<>();
    private TextView tvNoPinnedMessages;
    private RecyclerView rvPinnedMessages;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat_detail);

        initCloudinary();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, imeInsets.bottom);
            return insets;
        });

        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) uploadToCloudinary(uri, "image");
        });
        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) uploadToCloudinary(uri, "file");
        });
        chatInfoLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String scrollToMessageId = result.getData().getStringExtra("SCROLL_TO_MESSAGE_ID");
                    if (scrollToMessageId != null) {
                        scrollToMessage(scrollToMessageId);
                    }
                }
            }
        );

        initViews();

        String channelName = getIntent().getStringExtra("CHAT_NAME");
        groupId = getIntent().getStringExtra("CHAT_ID");
        if (getIntent().hasExtra("SERVER_COLOR")) {
            serverColor = getIntent().getStringExtra("SERVER_COLOR");
        }

        if (groupId != null) {
            groupChatRef = Firebase.getDatabase().getReference("chats").child(groupId);
            listenForMessages(groupId);
        }

        if (channelName != null) {
            tvChannelName.setText(channelName.toUpperCase()); // Ép in hoa cho đúng vibe HUD
        }

        applyServerColor();
        setupRecyclerView();
        setupClickListeners();
        setupMentionSuggestions();

        String serverId = getIntent().getStringExtra("SERVER_ID");
        if (serverId == null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("Channels").document(groupId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String sId = documentSnapshot.getString("serverId");
                        if (sId != null) {
                            this.serverId = sId;
                            setupServerMembersListener();
                        }
                    }
                });
        } else {
            this.serverId = serverId;
            setupServerMembersListener();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activeChatId = groupId;
    }

    @Override
    protected void onPause() {
        super.onPause();
        activeChatId = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (membersListener != null) {
            membersListener.remove();
        }
    }

    private void applyServerColor() {
        try {
            int color = Color.parseColor(serverColor);

            // 1. Viền Neon cho Header
            MaterialCardView header = findViewById(R.id.header);
            if (header != null) {
                header.setStrokeColor(color);
            }

            // 2. Chữ "#" cách điệu
            if (tvChannelHash != null) {
                tvChannelHash.setTextColor(color);
            }

            // 3. Chấm Online
            ImageView ivOnlineStatus = findViewById(R.id.ivOnlineStatus);
            if (ivOnlineStatus != null) {
                ivOnlineStatus.setColorFilter(color);
            }

            // 4. Viền ô nhập liệu Console
            if (messageCard != null) {
                messageCard.setStrokeColor(color);
            }

            // 5. Nút Gửi (Tô màu nền rực rỡ)
            if (btnSend != null) {
                btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
            }

            // 6. Trạng thái thanh Status Bar (Chuyển sang nền tối để app nhìn sâu hơn)
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#121212"));

            // 7. Viền cho danh sách gợi ý nhắc tên (Mention Suggestions)
            if (cardMentionSuggestions != null) {
                cardMentionSuggestions.setStrokeColor(color);
            }

            // 8. Nút xem tin nhắn đã ghim
            if (btnPinnedMessages != null) {
                btnPinnedMessages.setColorFilter(color);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initCloudinary() {
        Map config = new HashMap();
        config.put("cloud_name", "dxoukp0yb");
        config.put("api_key", "359217744855482");
        config.put("api_secret", "eTG0UvW_hdsHm4hl0r2XJCvidR0");
        try {
            MediaManager.init(this, config);
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
            public void onEditReminder(Message message) {
                showReminderDialog(message);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
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
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView);
    }

    private void setupClickListeners() {
        if (btnAttachHome != null) {
            btnAttachHome.setOnClickListener(v -> {
                String[] options = {"📷 Gửi Hình ảnh", "📎 Gửi Tập tin", "⏰ Lời nhắc"};
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Tải lên hệ thống")
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) imagePickerLauncher.launch("image/*");
                            else if (which == 1) filePickerLauncher.launch("*/*");
                            else if (which == 2) showReminderDialog(null);
                        })
                        .show();
            });
        }

        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendMessage());
        tvReplyingToLayout.setOnClickListener(v -> {
            messageToReply = null;
            tvReplyingToLayout.setVisibility(View.GONE);
        });

        tvChannelName.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(ChatDetailActivity.this, ChatInfoActivity.class);
            intent.putExtra("CHAT_ID", groupId);
            String name = getIntent().getStringExtra("CHAT_NAME");
            intent.putExtra("CHAT_NAME", name);
            intent.putExtra("SERVER_ID", serverId);
            intent.putExtra("SERVER_COLOR", serverColor);
            chatInfoLauncher.launch(intent);
        });

        if (btnPinnedMessages != null) {
            btnPinnedMessages.setOnClickListener(v -> showPinnedMessages());
        }
    }

    private void sendMessage() {
        String msg = edtMessage.getText().toString().trim();
        if (!msg.isEmpty() && groupChatRef != null) {
            Message messageModel = new Message(senderId, groupId, msg, System.currentTimeMillis());
            if (messageToReply != null) {
                messageModel.setRepliedToContent(messageToReply.getContent());
                messageModel.setRepliedToType(messageToReply.getType());
                messageToReply = null;
                tvReplyingToLayout.setVisibility(View.GONE);
            }
            groupChatRef.push().setValue(messageModel).addOnSuccessListener(aVoid -> edtMessage.setText(""));
        }
    }

    private void uploadToCloudinary(Uri fileUri, String type) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Đang truyền dữ liệu...");
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
                Toast.makeText(ChatDetailActivity.this, "Lỗi: " + error.getDescription(), Toast.LENGTH_SHORT).show();
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
            tvReplyingToLayout.setVisibility(View.GONE);
        }
        groupChatRef.push().setValue(model);
    }

    private void listenForMessages(String chatRoomID) {
        groupChatRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                messageList.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    Message model = data.getValue(Message.class);
                    if(model != null) {
                        model.setMessageId(data.getKey());
                        messageList.add(model);
                    }
                }
                adapter.notifyDataSetChanged();

                if (pinnedAdapter != null) {
                    currentPinnedMessages.clear();
                    for (Message m : messageList) {
                        if (m.isPinned() && !m.isDeleted()) {
                            currentPinnedMessages.add(m);
                        }
                    }
                    pinnedAdapter.notifyDataSetChanged();
                    if (currentPinnedMessages.isEmpty()) {
                        if (tvNoPinnedMessages != null) tvNoPinnedMessages.setVisibility(View.VISIBLE);
                        if (rvPinnedMessages != null) rvPinnedMessages.setVisibility(View.GONE);
                    } else {
                        if (tvNoPinnedMessages != null) tvNoPinnedMessages.setVisibility(View.GONE);
                        if (rvPinnedMessages != null) rvPinnedMessages.setVisibility(View.VISIBLE);
                    }
                }
                
                if (!messageList.isEmpty()) {
                    Message lastMsg = messageList.get(messageList.size() - 1);
                    String lastMsgId = lastMsg.getMessageId();
                    if (lastMessageId == null || !lastMsgId.equals(lastMessageId)) {
                        recyclerView.scrollToPosition(messageList.size() - 1);
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
        if(message.isDeleted()) return;
        messageToReply = message;
        tvReplyingToLayout.setVisibility(View.VISIBLE);
        String type = message.getType();
        if ("image".equals(type)) {
            tvReplyingToText.setText("Đang trả lời: 📷 Hình ảnh");
            if (cardReplyPreviewImage != null) cardReplyPreviewImage.setVisibility(View.VISIBLE);
            com.bumptech.glide.Glide.with(this).load(message.getContent()).centerCrop().into(ivReplyPreview);
        } else if ("file".equals(type)) {
            String fileName = "Tài liệu đính kèm";
            try { fileName = message.getContent().substring(message.getContent().lastIndexOf('/') + 1); } catch (Exception e) {}
            tvReplyingToText.setText("Đang trả lời: 📎 " + fileName);
            if (cardReplyPreviewImage != null) cardReplyPreviewImage.setVisibility(View.GONE);
        } else {
            String content = message.getContent();
            tvReplyingToText.setText("Đang trả lời: " + (content.length() > 40 ? content.substring(0, 40) + "..." : content));
            if (cardReplyPreviewImage != null) cardReplyPreviewImage.setVisibility(View.GONE);
        }
        edtMessage.requestFocus();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.chatRecyclerView);
        edtMessage = findViewById(R.id.edtMessage);
        btnSend = findViewById(R.id.btnSend);
        btnBack = findViewById(R.id.btnBack);
        tvChannelName = findViewById(R.id.tvChannelName);
        tvChannelHash = findViewById(R.id.tvChannelHash); // Ánh xạ chữ #
        btnPinnedMessages = findViewById(R.id.btnPinnedMessages);

        // Gán nút đính kèm (+)
        btnAttachHome = findViewById(R.id.btnAttachHome);
        messageCard = findViewById(R.id.messageCard);

        tvReplyingToLayout = findViewById(R.id.tvReplyingToLayout);
        tvReplyingToText = findViewById(R.id.tvReplyingToText);
        ivReplyPreview = findViewById(R.id.ivReplyPreview);
        cardReplyPreviewImage = findViewById(R.id.cardReplyPreviewImage);
    }

    private void setupMentionSuggestions() {
        cardMentionSuggestions = findViewById(R.id.cardMentionSuggestions);
        rvMentionSuggestions = findViewById(R.id.rvMentionSuggestions);
        
        mentionAdapter = new MentionAdapter(filteredMembers);
        rvMentionSuggestions.setLayoutManager(new LinearLayoutManager(this));
        rvMentionSuggestions.setAdapter(mentionAdapter);

        // Đăng ký TextWatcher cho edtMessage
        edtMessage.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int cursor = edtMessage.getSelectionStart();
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
                    mentionStartIndex = atIndex;
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

        // Áp dụng màu viền đồng bộ với màu chủ đạo của Server
        if (cardMentionSuggestions != null && serverColor != null) {
            try {
                cardMentionSuggestions.setStrokeColor(Color.parseColor(serverColor));
            } catch (Exception e) {}
        }
    }

    private void setupServerMembersListener() {
        if (serverId == null || serverId.isEmpty()) return;
        
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        membersListener = db.collection("servers").document(serverId).collection("members")
            .addSnapshotListener((snapshots, error) -> {
                if (error != null) {
                    return;
                }
                if (snapshots != null) {
                    serverMembers.clear();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots.getDocuments()) {
                        com.example.se114_callingsystem.model.ServerMember m = doc.toObject(com.example.se114_callingsystem.model.ServerMember.class);
                        if (m != null) {
                            serverMembers.add(m);
                        }
                    }
                    if (adapter != null) {
                        adapter.setServerMembers(serverMembers);
                    }
                    if (pinnedAdapter != null) {
                        pinnedAdapter.setServerMembers(serverMembers);
                    }
                }
            });
    }

    private void showMentionSuggestions(String query) {
        filteredMembers.clear();
        String lowercaseQuery = query.toLowerCase();
        for (com.example.se114_callingsystem.model.ServerMember member : serverMembers) {
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
            if (cardMentionSuggestions != null) {
                cardMentionSuggestions.setVisibility(View.VISIBLE);
            }
        }
    }

    private void hideMentionSuggestions() {
        mentionStartIndex = -1;
        if (cardMentionSuggestions != null) {
            cardMentionSuggestions.setVisibility(View.GONE);
        }
    }

    private void insertMention(com.example.se114_callingsystem.model.ServerMember member) {
        String nickname = member.getNickname() != null ? member.getNickname() : member.getUserName();
        String mention = "@" + nickname + " ";
        
        String text = edtMessage.getText().toString();
        String newText = text.substring(0, mentionStartIndex) + mention;
        
        edtMessage.setText(newText);
        edtMessage.setSelection(newText.length());
        
        mentionStartIndex = -1;
        cardMentionSuggestions.setVisibility(View.GONE);
    }
    
    private void showReminderDialog(Message existingMessage) {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_reminder, null);
        com.google.android.material.textfield.TextInputEditText etContent = dialogView.findViewById(R.id.etReminderContent);
        android.widget.TextView tvDateTime = dialogView.findViewById(R.id.tvReminderDateTime);
        android.widget.Button btnPickDate = dialogView.findViewById(R.id.btnPickDate);

        final java.util.Calendar calendar = java.util.Calendar.getInstance();
        if (existingMessage != null) {
            etContent.setText(existingMessage.getContent());
            calendar.setTimeInMillis(existingMessage.getReminderTime());
        }
        
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault());
        tvDateTime.setText("Thời gian: " + sdf.format(calendar.getTime()));

        btnPickDate.setOnClickListener(v -> {
            new android.app.DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                calendar.set(java.util.Calendar.YEAR, year);
                calendar.set(java.util.Calendar.MONTH, month);
                calendar.set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth);
                new android.app.TimePickerDialog(this, (tView, hourOfDay, minute) -> {
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(java.util.Calendar.MINUTE, minute);
                    calendar.set(java.util.Calendar.SECOND, 0);
                    calendar.set(java.util.Calendar.MILLISECOND, 0);
                    tvDateTime.setText("Thời gian: " + sdf.format(calendar.getTime()));
                }, calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE), true).show();
            }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });

        String title = existingMessage != null ? "Sửa lời nhắc" : "Tạo lời nhắc";
        String btnText = existingMessage != null ? "Lưu" : "Tạo";

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton(btnText, null) // Set null to override later
                .setNegativeButton("Hủy", null)
                .create();
                
        dialog.show();
        
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
                android.widget.Toast.makeText(ChatDetailActivity.this, "Thời gian hẹn phải ở trong tương lai!", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            
            String content = etContent.getText() != null ? etContent.getText().toString().trim() : "";
            if (content.isEmpty()) {
                android.widget.Toast.makeText(ChatDetailActivity.this, "Vui lòng nhập nội dung!", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (existingMessage != null) {
                if (groupChatRef != null && existingMessage.getMessageId() != null) {
                    groupChatRef.child(existingMessage.getMessageId()).child("content").setValue(content);
                    groupChatRef.child(existingMessage.getMessageId()).child("reminderTime").setValue(calendar.getTimeInMillis());
                }
            } else {
                sendReminderMessage(content, calendar.getTimeInMillis());
            }
            dialog.dismiss();
        });
    }

    private void sendReminderMessage(String content, long targetTime) {
        if (senderId == null || groupChatRef == null) return;
        String messageId = groupChatRef.push().getKey();
        if (messageId != null) {
            Message message = new Message(senderId, groupId, content, System.currentTimeMillis());
            message.setMessageId(messageId);
            message.setType("reminder");
            message.setReminderTime(targetTime);
            groupChatRef.child(messageId).setValue(message);
        }
    }

    private class MentionAdapter extends RecyclerView.Adapter<MentionAdapter.ViewHolder> {
        private List<com.example.se114_callingsystem.model.ServerMember> list;

        public MentionAdapter(List<com.example.se114_callingsystem.model.ServerMember> list) {
            this.list = list;
        }

        public void setList(List<com.example.se114_callingsystem.model.ServerMember> list) {
            this.list = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mention_suggestion, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            com.example.se114_callingsystem.model.ServerMember member = list.get(position);
            
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
                    if (doc.exists() && uid.equals(holder.itemView.getTag())) {
                        String profilePic = doc.getString("profilePic");
                        if (profilePic != null && !profilePic.isEmpty()) {
                            com.bumptech.glide.Glide.with(ChatDetailActivity.this)
                                .load(profilePic)
                                .placeholder(R.drawable.icon_user)
                                .into(holder.ivAvatar);
                        } else {
                            holder.ivAvatar.setImageResource(R.drawable.icon_user);
                            try {
                                holder.ivAvatar.setColorFilter(Color.parseColor(serverColor));
                            } catch (Exception e) {
                                holder.ivAvatar.setColorFilter(Color.parseColor("#FF007F"));
                            }
                        }
                    }
                });

            holder.itemView.setOnClickListener(v -> {
                insertMention(member);
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView tvUsername;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.ivAvatar);
                tvUsername = itemView.findViewById(R.id.tvUsername);
            }
        }
    }

    private void showPinnedMessages() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.layout_bottom_sheet_pinned_messages, null);
        bottomSheetDialog.setContentView(sheetView);

        // Make background transparent for rounded corners
        try {
            ((View) sheetView.getParent()).setBackgroundColor(Color.TRANSPARENT);
        } catch (Exception e) {}

        TextView tvPinnedTitle = sheetView.findViewById(R.id.tvPinnedTitle);
        rvPinnedMessages = sheetView.findViewById(R.id.rvPinnedMessages);
        tvNoPinnedMessages = sheetView.findViewById(R.id.tvNoPinnedMessages);

        // Style the title with the server color
        if (tvPinnedTitle != null) {
            try {
                tvPinnedTitle.setTextColor(Color.parseColor(serverColor));
            } catch (Exception e) {}
        }

        // Filter messages that are pinned and not deleted
        currentPinnedMessages.clear();
        for (Message m : messageList) {
            if (m.isPinned() && !m.isDeleted()) {
                currentPinnedMessages.add(m);
            }
        }

        if (currentPinnedMessages.isEmpty()) {
            if (tvNoPinnedMessages != null) tvNoPinnedMessages.setVisibility(View.VISIBLE);
            if (rvPinnedMessages != null) rvPinnedMessages.setVisibility(View.GONE);
        } else {
            if (tvNoPinnedMessages != null) tvNoPinnedMessages.setVisibility(View.GONE);
            if (rvPinnedMessages != null) rvPinnedMessages.setVisibility(View.VISIBLE);
        }

        pinnedAdapter = new PinnedMessagesAdapter(currentPinnedMessages, serverColor, new PinnedMessagesAdapter.OnPinnedMessageInteractListener() {
            @Override
            public void onGoTo(Message message) {
                int index = -1;
                for (int i = 0; i < messageList.size(); i++) {
                    if (messageList.get(i).getMessageId() != null && messageList.get(i).getMessageId().equals(message.getMessageId())) {
                        index = i;
                        break;
                    }
                }
                if (index != -1) {
                    recyclerView.scrollToPosition(index);
                    Toast.makeText(ChatDetailActivity.this, "Đã di chuyển tới tin nhắn", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ChatDetailActivity.this, "Không tìm thấy tin nhắn trong cuộc trò chuyện", Toast.LENGTH_SHORT).show();
                }
                bottomSheetDialog.dismiss();
            }

            @Override
            public void onUnpin(Message message) {
                if (groupChatRef != null && message.getMessageId() != null) {
                    groupChatRef.child(message.getMessageId()).child("pinned").setValue(false)
                        .addOnSuccessListener(aVoid -> {
                            currentPinnedMessages.remove(message);
                            if (currentPinnedMessages.isEmpty()) {
                                if (tvNoPinnedMessages != null) tvNoPinnedMessages.setVisibility(View.VISIBLE);
                                if (rvPinnedMessages != null) rvPinnedMessages.setVisibility(View.GONE);
                            } else {
                                if (pinnedAdapter != null) {
                                    pinnedAdapter.notifyDataSetChanged();
                                }
                            }
                            Toast.makeText(ChatDetailActivity.this, "Đã bỏ ghim", Toast.LENGTH_SHORT).show();
                        });
                }
            }
        });
        pinnedAdapter.setServerMembers(serverMembers);

        if (rvPinnedMessages != null) {
            rvPinnedMessages.setLayoutManager(new LinearLayoutManager(this));
            rvPinnedMessages.setAdapter(pinnedAdapter);
        }

        bottomSheetDialog.setOnDismissListener(dialog -> {
            pinnedAdapter = null;
            tvNoPinnedMessages = null;
            rvPinnedMessages = null;
            currentPinnedMessages.clear();
        });

        bottomSheetDialog.show();
    }

    private void scrollToMessage(String messageId) {
        if (messageId == null || messageList == null) return;
        int index = -1;
        for (int i = 0; i < messageList.size(); i++) {
            Message msg = messageList.get(i);
            if (msg.getMessageId() != null && msg.getMessageId().equals(messageId)) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            final int targetIndex = index;
            recyclerView.scrollToPosition(targetIndex);
            if (adapter != null) {
                adapter.setHighlightMessageId(messageId);
                adapter.notifyItemChanged(targetIndex);
                
                // Clear highlight after 1.5 seconds
                recyclerView.postDelayed(() -> {
                    if (adapter.getHighlightMessageId() != null && adapter.getHighlightMessageId().equals(messageId)) {
                        adapter.setHighlightMessageId(null);
                        adapter.notifyItemChanged(targetIndex);
                    }
                }, 1500);
            }
        } else {
            Toast.makeText(this, "Không tìm thấy tin nhắn trong cuộc trò chuyện", Toast.LENGTH_SHORT).show();
        }
    }
}
