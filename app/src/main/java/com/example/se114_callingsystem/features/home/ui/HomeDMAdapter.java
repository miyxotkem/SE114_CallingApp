package com.example.se114_callingsystem.features.home.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.User;
import java.util.List;

public class HomeDMAdapter extends RecyclerView.Adapter<HomeDMAdapter.DMViewHolder> {

    private List<User> friendList;
    private OnFriendClickListener listener;

    public interface OnFriendClickListener {
        void onFriendClick(User friend, View itemView);
    }

    public HomeDMAdapter(List<User> friendList, OnFriendClickListener listener) {
        this.friendList = friendList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DMViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_dm, parent, false);
        return new DMViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DMViewHolder holder, int position) {
        User friend = friendList.get(position);
        Context context = holder.itemView.getContext();

        String displayName = friend.getUsername();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = friend.getEmail();
        }
        holder.tvUserName.setText(displayName);

        // Load profile picture
        if (friend.getProfilePic() != null && !friend.getProfilePic().isEmpty()) {
            Glide.with(context)
                    .load(friend.getProfilePic())
                    .placeholder(R.drawable.ic_user)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_user);
        }

        // Set status dot color
        String status = friend.getStatus();
        if (status == null) status = "offline";
        int colorRes = R.color.discord_text_muted;

        switch (status.toLowerCase()) {
            case "online":
                colorRes = R.color.discord_green;
                break;
            case "idle":
            case "idling":
                colorRes = R.color.discord_yellow;
                break;
            case "dnd":
            case "do not disturb":
                colorRes = R.color.discord_red;
                break;
            case "offline":
            case "invisible":
                colorRes = R.color.discord_text_muted;
                break;
            default:
                if (status.length() > 0 && !status.equalsIgnoreCase("offline")) {
                    colorRes = R.color.discord_green;
                }
                break;
        }

        holder.viewStatusIndicator.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, colorRes)));

        // Listen for unread notification count from this specific friend
        if (holder.unreadListener != null) {
            holder.unreadListener.remove();
            holder.unreadListener = null;
        }

        String myUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null 
                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        String friendUid = friend.getUserId();

        if (myUid != null && friendUid != null) {
            holder.unreadListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(myUid)
                    .collection("notifications")
                    .whereEqualTo("isRead", false)
                    .whereEqualTo("senderId", friendUid)
                    .addSnapshotListener((value, error) -> {
                        if (error != null) return;
                        if (value != null && !value.isEmpty()) {
                            holder.tvUnreadBadge.setText(String.valueOf(value.size()));
                            holder.tvUnreadBadge.setVisibility(View.VISIBLE);
                        } else {
                            holder.tvUnreadBadge.setVisibility(View.GONE);
                        }
                    });
        } else {
            holder.tvUnreadBadge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFriendClick(friend, holder.itemView);
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull DMViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder.unreadListener != null) {
            holder.unreadListener.remove();
            holder.unreadListener = null;
        }
    }

    @Override
    public int getItemCount() {
        return friendList != null ? friendList.size() : 0;
    }

    public static class DMViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        View viewStatusIndicator;
        TextView tvUserName;
        TextView tvUnreadBadge;
        com.google.firebase.firestore.ListenerRegistration unreadListener;

        public DMViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            viewStatusIndicator = itemView.findViewById(R.id.viewStatusIndicator);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvUnreadBadge = itemView.findViewById(R.id.tvUnreadBadge);
        }
    }
}
