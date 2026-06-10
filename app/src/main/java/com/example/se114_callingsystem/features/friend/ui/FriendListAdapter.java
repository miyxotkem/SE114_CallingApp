package com.example.se114_callingsystem.features.friend.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.User;
import java.util.List;

public class FriendListAdapter extends RecyclerView.Adapter<FriendListAdapter.FriendViewHolder> {

    private List<User> userList;
    private boolean isRequestList;
    private OnFriendActionListener listener;

    public interface OnFriendActionListener {
        void onAccept(User user);
        void onReject(User user);
        void onRemove(User user);
        void onMessage(User user);
    }

    public FriendListAdapter(List<User> userList, boolean isRequestList, OnFriendActionListener listener) {
        this.userList = userList;
        this.isRequestList = isRequestList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend, parent, false);
        return new FriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        User user = userList.get(position);
        
        // Bind username
        String name = user.getUsername();
        if (name == null || name.isEmpty()) {
            name = user.getEmail();
        }
        holder.tvUserName.setText(name);

        // Bind email subtitle
        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            holder.tvUserEmail.setText(user.getEmail());
            holder.tvUserEmail.setVisibility(View.VISIBLE);
        } else {
            holder.tvUserEmail.setVisibility(View.GONE);
        }

        // Bind profile picture using Glide
        if (user.getProfilePic() != null && !user.getProfilePic().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(user.getProfilePic())
                    .placeholder(R.drawable.ic_user)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_user);
        }

        // Bind status dot indicator
        String status = user.getStatus();
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
        holder.viewStatusIndicator.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(holder.itemView.getContext(), colorRes))
        );

        // Bind actions depending on list type
        if (isRequestList) {
            holder.llRequestActions.setVisibility(View.VISIBLE);
            holder.llFriendActions.setVisibility(View.GONE);
            
            holder.btnAccept.setOnClickListener(v -> {
                if (listener != null) listener.onAccept(user);
            });
            holder.btnReject.setOnClickListener(v -> {
                if (listener != null) listener.onReject(user);
            });
        } else {
            holder.llRequestActions.setVisibility(View.GONE);
            holder.llFriendActions.setVisibility(View.VISIBLE);
            
            holder.btnRemoveFriend.setOnClickListener(v -> {
                if (listener != null) listener.onRemove(user);
            });

            holder.btnMessageFriend.setOnClickListener(v -> {
                if (listener != null) listener.onMessage(user);
            });
        }
        
        // Open Profile on click
        holder.itemView.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("USER_ID", user.getUserId());
            Navigation.findNavController(v).navigate(R.id.nav_profile, bundle);
        });
    }

    @Override
    public int getItemCount() {
        return userList != null ? userList.size() : 0;
    }

    public static class FriendViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName;
        TextView tvUserEmail;
        ImageView ivAvatar;
        View viewStatusIndicator;
        LinearLayout llRequestActions;
        LinearLayout llFriendActions;
        View btnAccept, btnReject, btnRemoveFriend, btnMessageFriend;

        public FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvUserEmail = itemView.findViewById(R.id.tvUserEmail);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            viewStatusIndicator = itemView.findViewById(R.id.viewStatusIndicator);
            llRequestActions = itemView.findViewById(R.id.llRequestActions);
            llFriendActions = itemView.findViewById(R.id.llFriendActions);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnReject = itemView.findViewById(R.id.btnReject);
            btnRemoveFriend = itemView.findViewById(R.id.btnRemoveFriend);
            btnMessageFriend = itemView.findViewById(R.id.btnMessageFriend);
        }
    }
}
