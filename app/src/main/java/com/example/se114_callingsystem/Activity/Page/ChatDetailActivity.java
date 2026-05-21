package com.example.se114_callingsystem.Activity.Page;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
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
import com.example.se114_callingsystem.Adapter.ChatAdapter;
import com.example.se114_callingsystem.Model.Firebase;
import com.example.se114_callingsystem.Model.Message;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.Util.ThemeHelper;
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

    private String groupId;
    private DatabaseReference groupChatRef;
    private String senderId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "UNKNOWN";
    private String serverColor = "#FF007F"; // Mặc định là màu Cyber Magenta

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
        // Mở popup menu khi bấm nút Attach (Gen Z Style)
        if (btnAttachHome != null) {
            btnAttachHome.setOnClickListener(v -> {
                String[] options = {"📷 Gửi Hình ảnh", "📎 Gửi Tập tin"};
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Tải tệp lên hệ thống")
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) imagePickerLauncher.launch("image/*");
                            else filePickerLauncher.launch("*/*");
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
            startActivity(intent);
        });
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
                recyclerView.scrollToPosition(messageList.size() - 1);
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

        // Gán nút đính kèm (+)
        btnAttachHome = findViewById(R.id.btnAttachHome);
        messageCard = findViewById(R.id.messageCard);

        tvReplyingToLayout = findViewById(R.id.tvReplyingToLayout);
        tvReplyingToText = findViewById(R.id.tvReplyingToText);
        ivReplyPreview = findViewById(R.id.ivReplyPreview);
        cardReplyPreviewImage = findViewById(R.id.cardReplyPreviewImage);
    }
}