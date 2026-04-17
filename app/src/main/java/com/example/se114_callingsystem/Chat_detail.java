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
                    if (uri != null) uploadFileToFirebase(uri, "image");
                }
        );

        // --- Khởi tạo Launcher chọn File ---
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) uploadFileToFirebase(uri, "file");
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
    private void uploadFileToFirebase(Uri fileUri, String type) {
        // Sử dụng ProgressDialog (Lưu ý: ProgressDialog đã bị deprecated,
        // nhưng nếu bạn muốn dùng thì vẫn ổn)
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Đang tải " + type + " lên...");
        pd.setCancelable(false);
        pd.show();

        String groupId = getIntent().getStringExtra("CHAT_ID");
        if (groupId == null) {
            pd.dismiss();
            return;
        }

        // 1. Tạo tên file duy nhất
        String extension = type.equals("image") ? ".jpg" : ".file";
        String fileName = System.currentTimeMillis() + extension;

        // 2. Sửa lỗi gọi hàm: Sử dụng hàm helper từ class Firebase của bạn
        // Cách này sạch sẽ và đúng chuẩn Singleton bạn vừa viết
        StorageReference storageRef = Firebase.getChatStorageRef(groupId).child(fileName);

        // 3. Thực hiện Upload
        storageRef.putFile(fileUri)
                .addOnSuccessListener(taskSnapshot -> {
                    // 4. Lấy URL tải về sau khi upload thành công
                    storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String downloadUrl = uri.toString();

                        // Gửi tin nhắn chứa link file vào Realtime Database
                        sendMediaMessage(downloadUrl, type);

                        if (!isFinishing()) pd.dismiss();
                        Toast.makeText(this, "Gửi thành công!", Toast.LENGTH_SHORT).show();
                    });
                })
                .addOnFailureListener(e -> {
                    if (!isFinishing()) pd.dismiss();
                    Toast.makeText(this, "Lỗi upload: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                })
                .addOnProgressListener(snapshot -> {
                    // (Tùy chọn) Bạn có thể cập nhật % tiến trình tại đây
                    double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                    pd.setMessage("Đang tải lên: " + (int) progress + "%");
                });
    }

    // --- Hàm gửi tin nhắn Media vào Database ---
    private void sendMediaMessage(String fileUrl, String type) {
        String senderId = "znNKHjrncFBE39hu8h8V";
        String groupId = getIntent().getStringExtra("CHAT_ID");
        long timestamp = System.currentTimeMillis();

        // Giả sử Model của bạn có constructor: (senderId, groupId, content, timestamp, type, fileUrl)
        // Nếu chưa có, bạn hãy cập nhật MessageModel của mình
        MessageModel messageModel = new MessageModel(senderId, groupId, "["+type+"]", timestamp);
        messageModel.setType(type);
        messageModel.setFileUrl(fileUrl);

        Firebase.getDatabase().getReference("chats").child(groupId).push().setValue(messageModel);
    }
}