package com.example.se114_callingsystem.Activity.Page;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private List<Message> messageList = new ArrayList<>();
    private ImageButton btnAttachImage, btnAttachFile;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String> filePickerLauncher;
    private EditText edtMessage;
    private ImageButton btnSend;
    private ImageView btnBack;
    private TextView tvChannelName;

    private View tvReplyingToLayout;
    private TextView tvReplyingToText;
    private ImageView ivReplyPreview;
    private Message messageToReply = null;

    private String groupId;
    private DatabaseReference groupChatRef;
    private String senderId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "UNKNOWN";
    private String serverColor = "#6C63FF";

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat_detail);

        // Khởi tạo Cloudinary (Nên điền đủ Key vào đây)
        initCloudinary();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, imeInsets.bottom);
            return insets;
        });

        // Launcher chọn Ảnh & File gửi lên Cloudinary
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
            tvChannelName.setText("# " + channelName);
        }

        applyServerColor();
        setupRecyclerView();
        setupClickListeners();
    }

    private void applyServerColor() {
        try {
            int color = android.graphics.Color.parseColor(serverColor);
            com.google.android.material.card.MaterialCardView header = findViewById(R.id.header);
            if (header != null) {
                header.setStrokeColor(color);
                header.setStrokeWidth(2);
            }
            if (btnSend != null) {
                btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
            }
        } catch (Exception e) {}
    }

    private void initCloudinary() {
        Map config = new HashMap();
        config.put("cloud_name", "dxoukp0yb");
        config.put("api_key", "359217744855482");
        config.put("api_secret", "eTG0UvW_hdsHm4hl0r2XJCvidR0");

        try {
            MediaManager.init(this, config);
        } catch (IllegalStateException e) {
            // Đã init trước đó
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
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Vuốt để reply
        setupSwipeToReply();
    }

    private void setupSwipeToReply() {
        // Cho phép vuốt cả TRÁI và PHẢI
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder v, @NonNull RecyclerView.ViewHolder t) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                showReplyUI(messageList.get(position));
                adapter.notifyItemChanged(position);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {

                View itemView = viewHolder.itemView;
                // Xác định xem đây là tin nhắn gửi hay nhận dựa trên ViewType của Adapter
                int viewType = viewHolder.getItemViewType();
                boolean isSent = (viewType == 1); // 1 là TYPE_SENT Nhã đã đặt trong Adapter

                // Giới hạn hướng vuốt:
                // Nếu là tin mình gửi (bên phải) -> chỉ cho vuốt trái (dX < 0)
                // Nếu là người ta gửi (bên trái) -> chỉ cho vuốt phải (dX > 0)
                float limitedDX = dX;
                if (isSent && dX > 0) limitedDX = 0; // Chặn vuốt phải cho tin gửi
                if (!isSent && dX < 0) limitedDX = 0; // Chặn vuốt trái cho tin nhận

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    Drawable icon = ContextCompat.getDrawable(ChatDetailActivity.this, android.R.drawable.ic_menu_revert);
                    if (icon != null) {
                        int itemHeight = itemView.getBottom() - itemView.getTop();
                        int iconHeight = icon.getIntrinsicHeight();
                        int iconWidth = icon.getIntrinsicWidth();
                        int iconTop = itemView.getTop() + (itemHeight - iconHeight) / 2;
                        int iconBottom = iconTop + iconHeight;

                        // Vẽ icon bên TRÁI khi vuốt PHẢI (Tin nhắn nhận)
                        if (limitedDX > 40) {
                            int iconLeft = itemView.getLeft() + 60;
                            int iconRight = iconLeft + iconWidth;
                            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                            icon.draw(c);
                        }
                        // Vẽ icon bên PHẢI khi vuốt TRÁI (Tin nhắn gửi)
                        else if (limitedDX < -40) {
                            int iconRight = itemView.getRight() - 60;
                            int iconLeft = iconRight - iconWidth;
                            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                            icon.draw(c);
                        }
                    }
                }

                // Giới hạn độ kéo tối đa để không bị trôi quá xa
                float maxSwipe = 180f;
                if (limitedDX > maxSwipe) limitedDX = maxSwipe;
                if (limitedDX < -maxSwipe) limitedDX = -maxSwipe;

                super.onChildDraw(c, recyclerView, viewHolder, limitedDX, dY, actionState, isCurrentlyActive);
            }
        };
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView);
    }

    private void setupClickListeners() {
        btnAttachImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        btnAttachFile.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendMessage());
        tvReplyingToLayout.setOnClickListener(v -> {
            messageToReply = null;
            tvReplyingToLayout.setVisibility(View.GONE);
        });

        // Nhấn vào tên channel → mở trang quản lí đoạn chat
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
        pd.setMessage("Đang tải " + type + "...");
        pd.show();

        MediaManager.get().upload(fileUri)
                .option("resource_type", "auto")
                .callback(new UploadCallback() {
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
            ivReplyPreview.setVisibility(View.VISIBLE);
            com.bumptech.glide.Glide.with(this)
                    .load(message.getContent())
                    .centerCrop()
                    .into(ivReplyPreview);
        } else if ("file".equals(type)) {
            String fileName = "Tài liệu đính kèm";
            try {
                fileName = message.getContent().substring(message.getContent().lastIndexOf('/') + 1);
            } catch (Exception e) {}
            tvReplyingToText.setText("Đang trả lời: 📎 " + fileName);
            ivReplyPreview.setVisibility(View.GONE);
        } else {
            String content = message.getContent();
            tvReplyingToText.setText("Đang trả lời: " + (content.length() > 40 ? content.substring(0, 40) + "..." : content));
            ivReplyPreview.setVisibility(View.GONE);
        }

        edtMessage.requestFocus();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.chatRecyclerView);
        edtMessage = findViewById(R.id.edtMessage);
        btnSend = findViewById(R.id.btnSend);
        btnBack = findViewById(R.id.btnBack);
        tvChannelName = findViewById(R.id.tvChannelName);
        btnAttachImage = findViewById(R.id.btnAttachImage);
        btnAttachFile = findViewById(R.id.btnAttachFile);
        tvReplyingToLayout = findViewById(R.id.tvReplyingToLayout);
        tvReplyingToText = findViewById(R.id.tvReplyingToText);
        ivReplyPreview = findViewById(R.id.ivReplyPreview);
    }
}