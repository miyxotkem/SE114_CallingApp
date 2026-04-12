package com.example.se114_callingsystem;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class Chat_adapter extends RecyclerView.Adapter<Chat_adapter.ChatViewHolder> {
    private List<String> mMessages;

    public Chat_adapter(List<String> messages) {
        this.mMessages = messages;
    }

    @Override
    public ChatViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_item_chat_bubble, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ChatViewHolder holder, int position) {
        // Đưa nội dung tin nhắn vào TextView
        holder.messageText.setText(mMessages.get(position));
    }

    @Override
    public int getItemCount() { return mMessages.size(); }

    public static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        public ChatViewHolder(View itemView) {
            super(itemView);
            // Đảm bảo bạn đã đặt ID cho TextView trong file item_chat_bubble là textMessage
            messageText = itemView.findViewById(R.id.textMessage);
        }
    }
}
