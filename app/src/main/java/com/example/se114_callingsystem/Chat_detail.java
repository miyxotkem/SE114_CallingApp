package com.example.se114_callingsystem;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Chat_detail extends AppCompatActivity {
    private RecyclerView recyclerView;
    private Chat_adapter adapter;
    private List<MessageModel> messageList = new ArrayList<>();
    private ImageButton btnAttachImage, btnAttachFile;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String> filePickerLauncher;
    private EditText edtMessage;
    private ImageButton btnSend;
    private ImageView btnBack;
    private TextView tvChannelName;

    private View tvReplyingToLayout;
    private TextView tvReplyingToText;
    private MessageModel messageToReply = null;

    private String groupId;
    private DatabaseReference groupChatRef;
    private String senderId = "znNKHjrncFBE39hu8h8V"; // ID của Nhã

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

        if (groupId != null) {
            groupChatRef = Firebase.getDatabase().getReference("chats").child(groupId);
            listenForMessages(groupId);
        }

        if (channelName != null) {
            tvChannelName.setText("# " + channelName);
        }

        setupRecyclerView();
        setupClickListeners();
    }

    private void initCloudinary() {
        Map config = new HashMap();
        config.put("cloud_name", "tên_cloud_của_nhã");
        config.put("api_key", "359217744855482");
        config.put("api_secret", "api_secret_của_nhã");

        try {
            MediaManager.init(this, config);
        } catch (IllegalStateException e) {
            // Đã init trước đó
        }
    }

    private void setupRecyclerView() {
        adapter = new Chat_adapter(messageList, new Chat_adapter.OnChatInteractListener() {
            @Override
            public void onReply(MessageModel message) { showReplyUI(message); }

            @Override
            public void onDelete(MessageModel message) {
                if (groupChatRef != null && message.getMessageId() != null) {
                    groupChatRef.child(message.getMessageId()).child("deleted").setValue(true);
                }
            }

            @Override
            public void onReact(MessageModel message, String emoji) {
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
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder v, @NonNull RecyclerView.ViewHolder t) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                showReplyUI(messageList.get(position));
                // Quan trọng: Phải notify để item quay về vị trí cũ sau khi swipe
                adapter.notifyItemChanged(position);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {

                View itemView = viewHolder.itemView;

                // --- LOGIC VẼ ICON REPLY KHI KÉO ---
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX > 0) {
                    // Lấy icon từ hệ thống hoặc drawable của bạn
                    Drawable icon = ContextCompat.getDrawable(Chat_detail.this, android.R.drawable.ic_menu_revert);
                    if (icon != null) {
                        // Tính toán vị trí icon nằm chính giữa chiều dọc của item
                        int itemHeight = itemView.getBottom() - itemView.getTop();
                        int iconHeight = icon.getIntrinsicHeight();
                        int iconWidth = icon.getIntrinsicWidth();

                        int iconTop = itemView.getTop() + (itemHeight - iconHeight) / 2;
                        int iconBottom = iconTop + iconHeight;

                        // Hiện icon khi kéo qua một khoảng nhất định (ví dụ 40px)
                        if (dX > 40) {
                            int iconLeft = itemView.getLeft() + 40; // Cách lề trái 40px
                            int iconRight = iconLeft + iconWidth;
                            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);

                            // Độ mờ của icon tăng dần theo độ kéo (DX)
                            int alpha = (int) Math.min(255, dX * 2);
                            icon.setAlpha(alpha);
                            icon.draw(c);
                        }
                    }
                }

                // Giới hạn độ kéo tối đa (ví dụ 150px) để không bị kéo mất item
                float maxSwipe = 150f;
                float limitedDX = Math.min(dX, maxSwipe);

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
    }

    private void sendMessage() {
        String msg = edtMessage.getText().toString().trim();
        if (!msg.isEmpty() && groupChatRef != null) {
            MessageModel messageModel = new MessageModel(senderId, groupId, msg, System.currentTimeMillis());
            if (messageToReply != null) {
                messageModel.setRepliedToContent(messageToReply.getContent());
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
                        Toast.makeText(Chat_detail.this, "Lỗi: " + error.getDescription(), Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
    }

    private void sendMediaMessage(String fileUrl, String type) {
        if (groupChatRef == null) return;
        MessageModel model = new MessageModel(senderId, groupId, fileUrl, System.currentTimeMillis());
        model.setType(type);

        if (messageToReply != null) {
            model.setRepliedToContent(messageToReply.getContent());
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
                    MessageModel model = data.getValue(MessageModel.class);
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

    private void showReplyUI(MessageModel message) {
        if(message.isDeleted()) return;
        messageToReply = message;
        tvReplyingToLayout.setVisibility(View.VISIBLE);
        String content = message.getContent();
        tvReplyingToText.setText("Đang trả lời: " + (content.length() > 40 ? content.substring(0, 40) + "..." : content));
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
    }
}