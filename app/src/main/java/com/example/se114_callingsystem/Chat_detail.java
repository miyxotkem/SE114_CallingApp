package com.example.se114_callingsystem;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
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

import java.util.ArrayList;
import java.util.List;

public class Chat_detail extends AppCompatActivity {
    private RecyclerView recyclerView;
    private Chat_adapter adapter;
    private List<MessageModel> messageList = new ArrayList<>();

    private EditText edtMessage;
    private ImageButton btnSend;
    private ImageView btnBack;
    private TextView tvChannelName; // For displaying the passed name
    private FirebaseFirestore db;

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

        // 4. Click Listeners
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
        tvChannelName = findViewById(R.id.tvChannelName); // Ensure this ID is in your XML
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
}