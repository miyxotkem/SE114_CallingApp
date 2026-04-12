package com.example.se114_callingsystem;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class Chat_adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // Định nghĩa 2 loại View
    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private List<MessageModel> mMessages;
    // ID của Nhã để so sánh (Sau này nên truyền từ Activity qua)
    private String currentUserId = "L2j7rDA0Y0cmsO0XNcaW";

    public Chat_adapter(List<MessageModel> messages) {
        this.mMessages = messages;
    }

    // Bước 1: Xác định tin nhắn này là Gửi hay Nhận
    @Override
    public int getItemViewType(int position) {
        MessageModel message = mMessages.get(position);
        if (message.getSenderId().equals(currentUserId)) {
            return TYPE_SENT;
        } else {
            return TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SENT) {
            // Nạp layout bên phải (Người gửi)
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.activity_item_chat_bubble, parent, false);
            return new SentMessageViewHolder(view);
        } else {
            // Nạp layout bên trái (Người nhận)
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.activity_item_chat_bubble_receive, parent, false);
            return new ReceivedMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageModel message = mMessages.get(position);

        if (getItemViewType(position) == TYPE_SENT) {
            ((SentMessageViewHolder) holder).bind(message);
        } else {
            ((ReceivedMessageViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return mMessages != null ? mMessages.size() : 0;
    }

    // --- CÁC VIEWHOLDER RIÊNG BIỆT ---

    // ViewHolder cho tin nhắn gửi đi
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

    // ViewHolder cho tin nhắn nhận về
    public static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        public ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textMessage);
        }
        void bind(MessageModel message) {
            messageText.setText(message.getContent());
        }
    }
}