package com.example.se114_callingsystem;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class Chat_adapter extends RecyclerView.Adapter<Chat_adapter.ChatViewHolder> {
    private List<MessageModel> mMessages;

    public Chat_adapter(List<MessageModel> messages) {
        this.mMessages = messages;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Layout này nên chứa bong bóng chat
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_item_chat_bubble, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        // Lấy đối tượng MessageModel tại vị trí position
        MessageModel message = mMessages.get(position);

        // CHỖ CẦN SỬA: Lấy content từ model để hiển thị
        if (message != null) {
            holder.messageText.setText(message.getContent());
        }
    }

    @Override
    public int getItemCount() {
        return mMessages != null ? mMessages.size() : 0;
    }

    public static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ánh xạ ID từ file activity_item_chat_bubble.xml
            messageText = itemView.findViewById(R.id.textMessage);
        }
    }
}