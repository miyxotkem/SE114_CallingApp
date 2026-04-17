package com.example.se114_callingsystem;

import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Chat_adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private List<MessageModel> mMessages;
    private static FirebaseFirestore db;
    private String currentUserId = "znNKHjrncFBE39hu8h8V"; // ID của Nhã

    public Chat_adapter(List<MessageModel> messages) {
        this.mMessages = messages;
    }

    // Xác định loại tin nhắn dựa trên senderId
    @Override
    public int getItemViewType(int position) {
        if (mMessages.get(position).getSenderId().equals(currentUserId)) {
            return TYPE_SENT;
        } else {
            return TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SENT) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.activity_item_chat_bubble, parent, false);
            return new SentMessageViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.activity_item_chat_bubble_receive, parent, false);
            return new ReceivedMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageModel message = mMessages.get(position);
        db = FirebaseFirestore.getInstance();
        // Logic xử lý gom nhóm: Nếu người trước đó trùng người hiện tại -> ẩn tên
        boolean showName = true;
        if (position > 0) {
            MessageModel previousMsg = mMessages.get(position - 1);
            if (previousMsg.getSenderId().equals(message.getSenderId())) {
                showName = false;
            }
        }

        if (holder instanceof SentMessageViewHolder) {
            ((SentMessageViewHolder) holder).bind(message);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            ((ReceivedMessageViewHolder) holder).bind(message, showName);
        }
    }

    @Override
    public int getItemCount() {
        return mMessages != null ? mMessages.size() : 0;
    }

    // --- VIEWHOLDERS ---

    // 1. ViewHolder cho tin nhắn Nhã gửi (Bên phải)
    public static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;

        public SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textMessage);
        }

        void bind(MessageModel message) {
            messageText.setText(message.getContent());
        }
    }

    // 2. ViewHolder cho tin nhắn người khác gửi (Bên trái)
    public static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, senderName,textTime;

        public ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textMessage);
            senderName = itemView.findViewById(R.id.textSenderName);
            textTime = itemView.findViewById(R.id.textTime);
        }

        void bind(MessageModel message, boolean showName) {
            // 1. Thiết lập hiển thị cơ bản và gán nội dung tin nhắn
            messageText.setText(message.getContent());
            senderName.setVisibility(showName ? View.VISIBLE : View.GONE);
            Date date = new Date(message.getTimestamp());
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            String formattedTime = sdf.format(date);
            textTime.setText(formattedTime);
            if (showName) {
                String uid = message.getSenderId();
                senderName.setTag(uid); // Dán nhãn để đối chiếu khi dữ liệu về
                senderName.setTextColor(getConsistentColor(uid));

                // 3. Truy vấn trực tiếp Document của người dùng theo ID (Tối ưu hơn Collection)
                db.collection("users").document(uid).get()
                        .addOnSuccessListener(documentSnapshot -> {
                            // 4. Kiểm tra Tag: Chỉ gán tên nếu View này vẫn dành cho UID này
                            if (documentSnapshot.exists() && uid.equals(senderName.getTag())) {
                                String name = documentSnapshot.getString("username");
                                senderName.setText(name != null ? name : "Người dùng");
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e("Firestore", "Lấy tên thất bại: " + e.getMessage());
                            senderName.setText("Lỗi");
                        });
            }
        }

        // Hàm tạo màu cố định dựa trên ID người dùng để mỗi người 1 màu khác nhau
        private int getConsistentColor(String uid) {
            int hash = uid.hashCode();
            int[] colors = {Color.RED, Color.BLUE, Color.parseColor("#FF9800"), Color.parseColor("#4CAF50"), Color.MAGENTA};
            return colors[Math.abs(hash) % colors.length];
        }
    }
}