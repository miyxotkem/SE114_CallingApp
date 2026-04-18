package com.example.se114_callingsystem;

import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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
        this.db = FirebaseFirestore.getInstance();
    }

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

        // 1. Kiểm tra xem có phải tin ĐẦU TIÊN của nhóm không (để hiện Tên)
        boolean isFirstInGroup = true;
        if (position > 0) {
            MessageModel previousMsg = mMessages.get(position - 1);
            if (previousMsg.getSenderId().equals(message.getSenderId())) {
                isFirstInGroup = false;
            }
        }

        // 2. Kiểm tra xem có phải tin CUỐI CÙNG của nhóm không (để hiện Avatar & Giờ)
        boolean isLastInGroup = true;
        if (position < mMessages.size() - 1) {
            MessageModel nextMsg = mMessages.get(position + 1);
            if (nextMsg.getSenderId().equals(message.getSenderId())) {
                isLastInGroup = false;
            }
        }

        if (holder instanceof SentMessageViewHolder) {
            ((SentMessageViewHolder) holder).bind(message, isLastInGroup);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            // Truyền cả 2 trạng thái vào bind
            ((ReceivedMessageViewHolder) holder).bind(message, isFirstInGroup, isLastInGroup);
        }
    }

    @Override
    public int getItemCount() {
        return mMessages != null ? mMessages.size() : 0;
    }

    // --- VIEWHOLDERS ---

    public static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView textTime;

        public SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textMessage);
            textTime= itemView.findViewById(R.id.textTime);
        }

        void bind(MessageModel message,boolean isLastInGroup) {
            messageText.setText(message.getContent());
            if(isLastInGroup){
                textTime.setVisibility(View.VISIBLE);
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                textTime.setText(sdf.format(new Date(message.getTimestamp())));
            }else{
                textTime.setVisibility(View.GONE);
            }
        }
    }

    public static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, senderName, textTime;
        ImageView avatarImg;

        public ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textMessage);
            senderName = itemView.findViewById(R.id.textSenderName);
            textTime = itemView.findViewById(R.id.textTime);
            avatarImg = itemView.findViewById(R.id.imgAvatar);
        }

        void bind(MessageModel message, boolean isFirstInGroup, boolean isLastInGroup) {
            messageText.setText(message.getContent());

            // --- QUY TẮC 1: TÊN HIỆN Ở TIN ĐẦU NHÓM ---
            if (isFirstInGroup) {
                senderName.setVisibility(View.VISIBLE);
                String uid = message.getSenderId();
                senderName.setTag(uid);
                senderName.setTextColor(getConsistentColor(uid));

                db.collection("users").document(uid).get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists() && uid.equals(senderName.getTag())) {
                                senderName.setText(documentSnapshot.getString("username"));
                            }
                        });
            } else {
                senderName.setVisibility(View.GONE);
            }

            // --- QUY TẮC 2: GIỜ & AVATAR HIỆN Ở TIN CUỐI NHÓM ---
            if (isLastInGroup) {
                textTime.setVisibility(View.VISIBLE);
                if (avatarImg != null) avatarImg.setVisibility(View.VISIBLE);

                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                textTime.setText(sdf.format(new Date(message.getTimestamp())));
            } else {
                textTime.setVisibility(View.GONE);
                // Dùng INVISIBLE để giữ khoảng trống cho Avatar, giúp các bong bóng chat thẳng hàng
                if (avatarImg != null) avatarImg.setVisibility(View.INVISIBLE);
            }
        }

        private int getConsistentColor(String uid) {
            int hash = uid.hashCode();
            int[] colors = {Color.RED, Color.BLUE, Color.parseColor("#FF9800"), Color.parseColor("#4CAF50"), Color.MAGENTA};
            return colors[Math.abs(hash) % colors.length];
        }
    }
    }