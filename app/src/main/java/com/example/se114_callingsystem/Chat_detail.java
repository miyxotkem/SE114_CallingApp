package com.example.se114_callingsystem;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.StorageReference;

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
    private TextView tvChannelName; // For displaying the passed name
    private FirebaseFirestore db;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat_detail);

        // Handle System Bar Insets (Status bar/Navigation bar padding)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(0, 0, 0, imeInsets.bottom);
            return insets;
        });
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) uploadToCloudinary(uri, "image");
                }
        );

        // --- Khởi tạo Launcher chọn File ---
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) uploadToCloudinary(uri, "file");
                }
        );


        // 1. Initialize Views
        initViews();

        // 2. Get Data from Intent (Passed from ChatAdapter)
        String channelName = getIntent().getStringExtra("CHAT_NAME");
        String receiverId = getIntent().getStringExtra("CHAT_ID"); // You'll need this later for Firebase messages

        if (channelName != null) {
            tvChannelName.setText("# " + channelName);
        }

        // 3. Setup RecyclerView
        adapter = new Chat_adapter(messageList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setOnTouchListener((v,event)->{
            hideKeyboard();
            return false;
        });
        // 4. Click Listeners
        btnAttachImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        btnAttachFile.setOnClickListener(v -> filePickerLauncher.launch("*/*"));
        btnBack.setOnClickListener(v -> finish());

        btnSend.setOnClickListener(v -> {
            sendMessage();
        }); // ID người nhận

        // 3. ID của Nhã (phải khớp với ID lúc gửi)
        String senderId = "znNKHjrncFBE39hu8h8V";

        if (channelName != null) {
            tvChannelName.setText("# " + channelName);
        }

        // --- PHẦN QUAN TRỌNG NHẤT ---
        if (receiverId != null) {
            // Gọi lắng nghe với cái ID phòng dài này
            String groupId = getIntent().getStringExtra("CHAT_ID");
            listenForMessages(groupId);
        }
        // Cloudinary
        Map config = new HashMap();
        config.put("cloud_name", "tên_cloud_của_nhã"); // Lấy trên Dashboard Cloudinary
        config.put("api_key", "359217744855482");     // Lấy trên Dashboard
        config.put("api_secret", "api_secret_của_nhã"); // Lấy trên Dashboard

        try {
            MediaManager.init(this, config);
        } catch (IllegalStateException e) {
            // Đã khởi tạo rồi thì không cần init lại
        }

    }
    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
            // Để chuyên nghiệp hơn, hãy xóa focus khỏi EditText
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
                            messageList.add(model);
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
    }
    private void sendMessage() {
        String msg = edtMessage.getText().toString().trim();
        if (!msg.isEmpty()) {
            String senderId = "znNKHjrncFBE39hu8h8V"; // ID của Nhã

            // Lấy ID Nhóm (truyền từ màn hình danh sách nhóm sang)
            String groupId = getIntent().getStringExtra("CHAT_ID");

            if (groupId == null) return;

            long timestamp = System.currentTimeMillis();

            // Tạo model tin nhắn (Nhớ thêm senderName để mọi người biết ai nhắn)
            MessageModel messageModel = new MessageModel(senderId, groupId, msg, timestamp);

            // Đẩy vào đúng địa chỉ của nhóm
            DatabaseReference groupChatRef = Firebase.getDatabase()
                    .getReference("chats")
                    .child(groupId); // Dùng ID nhóm cố định ở đây

            String messageId = groupChatRef.push().getKey();
            if (messageId != null) {
                groupChatRef.child(messageId).setValue(messageModel)
                        .addOnSuccessListener(aVoid -> edtMessage.setText(""));
            }
        }
    }
    private void uploadToCloudinary(Uri fileUri, String type) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Đang tải " + type + " lên Cloudinary...");
        pd.setCancelable(false);
        pd.show();

        // MediaManager đã được khởi tạo ở onCreate (như mình hướng dẫn ở trên)
        MediaManager.get().upload(fileUri)
                .option("resource_type", "auto") // Tự động nhận diện ảnh/video/file
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        // Có thể để trống hoặc log
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        double progress = (100.0 * bytes) / totalBytes;
                        pd.setMessage("Đang tải lên: " + (int) progress + "%");
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        if (!isFinishing()) pd.dismiss();
                        String cloudinaryUrl = (String) resultData.get("secure_url");
                        sendMediaMessage(cloudinaryUrl, type);
                    }

                    // SỬA LỖI Ở ĐÂY: Cloudinary bản mới dùng ErrorInfo error
                    // và không có phương thức getDescription()
                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        if (!isFinishing()) pd.dismiss();
                        // Dùng error.getMsg() thay vì getDescription()
                        String errorMessage = (error != null) ? error.getDescription() : "Lỗi không xác định";
                        Toast.makeText(Chat_detail.this, "Lỗi Cloudinary: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        // Bắt buộc phải override phương thức này
                    }
                }).dispatch();
    }

    // --- Hàm gửi tin nhắn Media vào Database ---
    private void sendMediaMessage(String fileUrl, String type) {
        String senderId = "znNKHjrncFBE39hu8h8V"; // ID của Nhã
        String groupId = getIntent().getStringExtra("CHAT_ID");
        if (groupId == null) return;

        long timestamp = System.currentTimeMillis();

        // Đối với ảnh/file, ta lưu URL vào content luôn cho đồng nhất
        MessageModel messageModel = new MessageModel(senderId, groupId, fileUrl, timestamp);
        messageModel.setType(type); // "image" hoặc "file"

        Firebase.getDatabase().getReference("chats")
                .child(groupId)
                .push()
                .setValue(messageModel)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Đã gửi " + type, Toast.LENGTH_SHORT).show();
                });
    }
}