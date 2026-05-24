package com.example.se114_callingsystem.features.chat;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.Message;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.example.se114_callingsystem.core.model.User;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.List;

public class PinnedMessagesAdapter extends RecyclerView.Adapter<PinnedMessagesAdapter.ViewHolder> {

    private List<Message> mPinnedList;
    private OnPinnedMessageInteractListener mListener;
    private String mServerColor;
    private List<ServerMember> mServerMembers;

    public interface OnPinnedMessageInteractListener {
        void onGoTo(Message message);
        void onUnpin(Message message);
    }

    public PinnedMessagesAdapter(List<Message> pinnedList, String serverColor, OnPinnedMessageInteractListener listener) {
        this.mPinnedList = pinnedList;
        this.mServerColor = serverColor;
        this.mListener = listener;
    }

    public void setList(List<Message> list) {
        this.mPinnedList = list;
        notifyDataSetChanged();
    }

    public void setServerMembers(List<ServerMember> members) {
        this.mServerMembers = members;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_pinned_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message message = mPinnedList.get(position);
        Context context = holder.itemView.getContext();

        // 1. Set ná»™i dung tin nháº¯n dá»±a trÃªn type
        if ("image".equals(message.getType())) {
            holder.tvMessageContent.setText("ðŸ“· HÃ¬nh áº£nh");
        } else if ("file".equals(message.getType())) {
            String fileName = "TÃ i liá»‡u Ä‘Ã­nh kÃ¨m";
            try {
                fileName = message.getContent().substring(message.getContent().lastIndexOf('/') + 1);
            } catch (Exception e) {}
            holder.tvMessageContent.setText("ðŸ“Ž " + fileName);
        } else {
            holder.tvMessageContent.setText(message.getContent());
        }

        // 2. Fetch avatar vÃ  tÃªn ngÆ°á»i gá»­i tá»« Firestore
        String uid = message.getSenderId();
        holder.itemView.setTag(uid);
        holder.ivAvatar.setColorFilter(null);

        // Try to resolve name from cached serverMembers
        ServerMember foundMember = null;
        if (mServerMembers != null) {
            for (ServerMember m : mServerMembers) {
                if (m.getUserId() != null && m.getUserId().equals(uid)) {
                    foundMember = m;
                    break;
                }
            }
        }

        final boolean hasCachedName = foundMember != null;
        if (hasCachedName) {
            String displayName = foundMember.getNickname();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = foundMember.getUserName();
            }
            holder.tvSenderName.setText(displayName);
        } else {
            holder.tvSenderName.setText("Äang táº£i...");
        }

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && uid.equals(holder.itemView.getTag())) {
                        if (!hasCachedName) {
                            String name = documentSnapshot.getString("username");
                            if (name != null && !name.isEmpty()) {
                                holder.tvSenderName.setText(name);
                            } else {
                                holder.tvSenderName.setText("User");
                            }
                        }

                        String profilePic = documentSnapshot.getString("profilePic");
                        if (profilePic != null && !profilePic.isEmpty()) {
                            Glide.with(context)
                                    .load(profilePic)
                                    .placeholder(R.drawable.ic_user)
                                    .into(holder.ivAvatar);
                        } else {
                            holder.ivAvatar.setImageResource(R.drawable.ic_user);
                            try {
                                holder.ivAvatar.setColorFilter(Color.parseColor(mServerColor));
                            } catch (Exception e) {
                                holder.ivAvatar.setColorFilter(Color.parseColor("#FF007F"));
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (uid.equals(holder.itemView.getTag())) {
                        if (!hasCachedName) {
                            holder.tvSenderName.setText("User");
                        }
                        holder.ivAvatar.setImageResource(R.drawable.ic_user);
                    }
                });

        // 3. Xá»­ lÃ½ sá»± kiá»‡n click
        holder.btnGoTo.setOnClickListener(v -> mListener.onGoTo(message));
        holder.btnUnpin.setOnClickListener(v -> mListener.onUnpin(message));
        holder.itemView.setOnClickListener(v -> mListener.onGoTo(message));
    }

    @Override
    public int getItemCount() {
        return mPinnedList != null ? mPinnedList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvSenderName, tvMessageContent;
        ImageButton btnGoTo, btnUnpin;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvMessageContent = itemView.findViewById(R.id.tvMessageContent);
            btnGoTo = itemView.findViewById(R.id.btnGoTo);
            btnUnpin = itemView.findViewById(R.id.btnUnpin);
        }
    }
}

