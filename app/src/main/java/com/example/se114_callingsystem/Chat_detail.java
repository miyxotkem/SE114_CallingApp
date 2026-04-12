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
        String senderId = "L2j7rDA0Y0cmsO0XNcaW";

        if (channelName != null) {
            tvChannelName.setText("# " + channelName);
        }

        // --- PHẦN QUAN TRỌNG NHẤT ---
        if (receiverId != null) {
            // Tạo lại chatRoomID giống hệt lúc gửi
            String chatRoomID = (senderId.compareTo(receiverId) < 0)
                    ? senderId + "_" + receiverId
                    : receiverId + "_" + senderId;

            // Gọi lắng nghe với cái ID phòng dài này
            listenForMessages(chatRoomID);
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
            // LẤY ID CỦA BẠN:
            // Nếu đã login, dùng: FirebaseAuth.getInstance().getUid();
            // Ở đây tôi tạm dùng ID bạn đã ghi trong code nhưng ở dạng String chuẩn:
            String senderId = "L2j7rDA0Y0cmsO0XNcaW";

            String receiverId = getIntent().getStringExtra("CHAT_ID");

            if (receiverId == null) {
                Toast.makeText(this, "Không tìm thấy ID người nhận", Toast.LENGTH_SHORT).show();
                return;
            }

            long timestamp = System.currentTimeMillis();

            // Tạo ID phòng chat duy nhất
            String chatRoomID = (senderId.compareTo(receiverId) < 0)
                    ? senderId + "_" + receiverId
                    : receiverId + "_" + senderId;

            MessageModel messageModel = new MessageModel(senderId, receiverId, msg, timestamp);

            DatabaseReference chatRef = Firebase.getDatabase()
                    .getReference("chats")
                    .child(chatRoomID);

            String messageId = chatRef.push().getKey();

            if (messageId != null) {
                chatRef.child(messageId).setValue(messageModel)
                        .addOnSuccessListener(aVoid -> {
                            edtMessage.setText("");
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            }
        }
    }
}