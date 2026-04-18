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

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;

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

    // UI để hiển thị đang Reply (Layout hiện đại)
    private View tvReplyingToLayout;
    private TextView tvReplyingToText;
    private MessageModel messageToReply = null;

    private FirebaseFirestore db;
    private String groupId;
    private DatabaseReference groupChatRef;
    private String senderId = "znNKHjrncFBE39hu8h8V";

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat_detail);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(0, 0, 0, imeInsets.bottom);
            return insets;
        });

        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) uploadFileToFirebase(uri, "image");
        });
        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) uploadFileToFirebase(uri, "file");
        });

        initViews();

        String channelName = getIntent().getStringExtra("CHAT_NAME");
        groupId = getIntent().getStringExtra("CHAT_ID");

        if (groupId != null) {
            groupChatRef = Firebase.getDatabase().getReference("chats").child(groupId);
        }

        if (channelName != null) {
            tvChannelName.setText("# " + channelName);
        }

        // Khởi tạo Adapter với Interface
        adapter = new Chat_adapter(messageList, new Chat_adapter.OnChatInteractListener() {
            @Override
            public void onReply(MessageModel message) {
                showReplyUI(message);
            }

            @Override
            public void onDelete(MessageModel message) {
                if (groupChatRef != null && message.getMessageId() != null) {
                    groupChatRef.child(message.getMessageId()).child("deleted").setValue(true);
                }
            }

            @Override
            public void onReact(MessageModel message, String emoji) {
                if(emoji.equals("CUSTOM")){
                    Toast.makeText(Chat_detail.this, "Mở Custom Emoji Picker", Toast.LENGTH_SHORT).show();
                } else if (groupChatRef != null && message.getMessageId() != null) {
                    groupChatRef.child(message.getMessageId()).child("reactionEmoji").setValue(emoji);
                }
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setOnTouchListener((v,event)->{
            hideKeyboard();
            return false;
        });

        // --- SWIPE TO REPLY (Vuốt mượt & vẽ Icon phía sau) ---
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                MessageModel message = messageList.get(position);
                adapter.notifyItemChanged(position); // Trả item về vị trí cũ ngay lập tức
                showReplyUI(message);
            }

            // Ghi đè để giới hạn khoảng cách và vẽ icon
            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                View itemView = viewHolder.itemView;

                // 1. Vẽ Icon Reply phía sau (Sử dụng icon mặc định của Android hoặc icon của bạn)
                Drawable replyIcon = ContextCompat.getDrawable(Chat_detail.this, android.R.drawable.ic_menu_revert);
                if (replyIcon != null) {
                    int iconMargin = (itemView.getHeight() - replyIcon.getIntrinsicHeight()) / 2;
                    int iconTop = itemView.getTop() + iconMargin;
                    int iconBottom = iconTop + replyIcon.getIntrinsicHeight();

                    if (dX > 0) { // Vuốt sang phải
                        int iconLeft = itemView.getLeft() + iconMargin;
                        int iconRight = iconLeft + replyIcon.getIntrinsicWidth();
                        replyIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                        replyIcon.draw(c);
                    } else if (dX < 0) { // Vuốt sang trái
                        int iconRight = itemView.getRight() - iconMargin;
                        int iconLeft = iconRight - replyIcon.getIntrinsicWidth();
                        replyIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                        replyIcon.draw(c);
                    }
                }

                // 2. Giới hạn khoảng cách vuốt (Khoảng 250 pixels)
                float maxSwipeDistance = 250f;
                float newDx = dX;

                if (dX > maxSwipeDistance) {
                    newDx = maxSwipeDistance;
                } else if (dX < -maxSwipeDistance) {
                    newDx = -maxSwipeDistance;
                }

                // Chỉ dịch chuyển theo newDx thay vì dX gốc
                super.onChildDraw(c, recyclerView, viewHolder, newDx, dY, actionState, isCurrentlyActive);
            }
        };
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView);

        // Click Listeners
        btnAttachImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        btnAttachFile.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendMessage());

        // Hủy bỏ Reply khi nhấn vào khu vực Reply
        tvReplyingToLayout.setOnClickListener(v -> {
            messageToReply = null;
            tvReplyingToLayout.setVisibility(View.GONE);
        });

        if (groupId != null) {
            listenForMessages(groupId);
        }
    }

    private void showReplyUI(MessageModel message) {
        if(message.isDeleted()) return;
        messageToReply = message;

        tvReplyingToLayout.setVisibility(View.VISIBLE);
        // Cắt bớt text nếu quá dài
        String content = message.getContent();
        if(content.length() > 40) content = content.substring(0, 40) + "...";
        tvReplyingToText.setText("Đang trả lời: " + content);

        // Mở bàn phím
        edtMessage.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(edtMessage, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
            view.clearFocus();
        }
    }

    private void listenForMessages(String chatRoomID) {
        Firebase.getDatabase().getReference("chats").child(chatRoomID)
                .addValueEventListener(new ValueEventListener() {
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
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void initViews() {
        recyclerView = findViewById(R.id.chatRecyclerView);
        edtMessage = findViewById(R.id.edtMessage);
        btnSend = findViewById(R.id.btnSend);
        btnBack = findViewById(R.id.btnBack);
        tvChannelName = findViewById(R.id.tvChannelName);
        btnAttachImage = findViewById(R.id.btnAttachImage);
        btnAttachFile = findViewById(R.id.btnAttachFile);

        // ID mới cho phần giao diện Reply
        tvReplyingToLayout = findViewById(R.id.tvReplyingToLayout);
        tvReplyingToText = findViewById(R.id.tvReplyingToText);
    }

    private void sendMessage() {
        String msg = edtMessage.getText().toString().trim();
        if (!msg.isEmpty() && groupChatRef != null) {
            long timestamp = System.currentTimeMillis();

            MessageModel messageModel = new MessageModel(senderId, groupId, msg, timestamp);

            // Xử lý nếu đang Reply
            if (messageToReply != null) {
                messageModel.setRepliedToContent(messageToReply.getContent());
                messageToReply = null;
                tvReplyingToLayout.setVisibility(View.GONE);
            }

            String messageId = groupChatRef.push().getKey();
            if (messageId != null) {
                messageModel.setMessageId(messageId);
                groupChatRef.child(messageId).setValue(messageModel)
                        .addOnSuccessListener(aVoid -> edtMessage.setText(""));
            }
        }
    }

    private void uploadFileToFirebase(Uri fileUri, String type) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Đang tải " + type + " lên...");
        pd.setCancelable(false);
        pd.show();

        if (groupId == null) {
            pd.dismiss();
            return;
        }

        String extension = type.equals("image") ? ".jpg" : ".file";
        String fileName = System.currentTimeMillis() + extension;
        StorageReference storageRef = Firebase.getChatStorageRef(groupId).child(fileName);

        storageRef.putFile(fileUri).addOnSuccessListener(taskSnapshot -> {
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                sendMediaMessage(uri.toString(), type);
                if (!isFinishing()) pd.dismiss();
            });
        }).addOnFailureListener(e -> {
            if (!isFinishing()) pd.dismiss();
            Toast.makeText(this, "Lỗi upload", Toast.LENGTH_SHORT).show();
        });
    }

    private void sendMediaMessage(String fileUrl, String type) {
        if (groupChatRef == null) return;
        long timestamp = System.currentTimeMillis();

        MessageModel messageModel = new MessageModel(senderId, groupId, "["+type+"]", timestamp);
        messageModel.setType(type);
        messageModel.setFileUrl(fileUrl);

        // Xử lý reply cho Media
        if (messageToReply != null) {
            messageModel.setRepliedToContent(messageToReply.getContent());
            messageToReply = null;
            tvReplyingToLayout.setVisibility(View.GONE);
        }

        String messageId = groupChatRef.push().getKey();
        if (messageId != null) {
            messageModel.setMessageId(messageId);
            groupChatRef.child(messageId).setValue(messageModel);
        }
    }
}