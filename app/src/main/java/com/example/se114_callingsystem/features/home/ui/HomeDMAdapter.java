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
import com.example.se114_callingsystem.features.home.viewmodel.HomeViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.List;

public class HomeDMAdapter extends RecyclerView.Adapter<HomeDMAdapter.DMViewHolder> {

    private List<User> friendList;
    private OnFriendClickListener listener;
    private HomeViewModel viewModel;
    private java.util.Map<String, Integer> unreadCounts = new java.util.HashMap<>();

    public interface OnFriendClickListener {
        void onFriendClick(User friend, View itemView);
    }

    public HomeDMAdapter(List<User> friendList, HomeViewModel viewModel, OnFriendClickListener listener) {
        this.friendList = friendList;
        this.viewModel = viewModel;
        this.listener = listener;
    }

    public void setUnreadCounts(java.util.Map<String, Integer> unreadCounts) {
        this.unreadCounts = unreadCounts != null ? unreadCounts : new java.util.HashMap<>();
        notifyDataSetChanged();
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

        boolean isPinned = viewModel != null && viewModel.isUserPinned(friend.getUserId());
        holder.ivPinIcon.setVisibility(isPinned ? View.VISIBLE : View.GONE);

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

        String friendUid = friend.getUserId();
        int unreadCount = (friendUid != null && unreadCounts.containsKey(friendUid)) ? unreadCounts.get(friendUid) : 0;
        if (unreadCount > 0) {
            holder.tvUnreadBadge.setText(String.valueOf(unreadCount));
            holder.tvUnreadBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvUnreadBadge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFriendClick(friend, holder.itemView);
            }
        });
        
        holder.ivMoreOptions.setOnClickListener(v -> {
            if (viewModel != null) {
                showDMOptionsBottomSheet(context, friend, isPinned, holder.itemView);
            }
        });
    }
    
    private void showDMOptionsBottomSheet(Context context, User friend, boolean isPinned, View anchorView) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(0, 32, 0, 32);
        layout.setBackgroundResource(R.drawable.bg_bottom_sheet);
        layout.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.discord_dark_alt)));
        
        android.widget.TextView tvTitle = new android.widget.TextView(context);
        tvTitle.setText(friend.getUsername() != null ? friend.getUsername() : "Options");
        tvTitle.setTextColor(ContextCompat.getColor(context, R.color.discord_text_primary));
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setPadding(48, 16, 48, 32);
        layout.addView(tvTitle);
        
        android.util.TypedValue outValue = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);

        // Xem Profile
        android.widget.TextView btnProfile = new android.widget.TextView(context);
        btnProfile.setText("Xem Profile");
        btnProfile.setTextColor(ContextCompat.getColor(context, R.color.discord_text_primary));
        btnProfile.setTextSize(16);
        btnProfile.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        btnProfile.setPadding(48, 48, 48, 48);
        btnProfile.setBackgroundResource(outValue.resourceId);
        btnProfile.setClickable(true);
        btnProfile.setFocusable(true);
        layout.addView(btnProfile);

        btnProfile.setOnClickListener(v -> {
            android.os.Bundle bundle = new android.os.Bundle();
            bundle.putString("USER_ID", friend.getUserId());
            androidx.navigation.Navigation.findNavController(anchorView).navigate(R.id.nav_profile, bundle);
            dialog.dismiss();
        });
        
        // Pin DM
        android.widget.TextView btnPin = new android.widget.TextView(context);
        btnPin.setText(isPinned ? "Unpin DM" : "Pin to Top");
        btnPin.setTextColor(ContextCompat.getColor(context, R.color.discord_text_primary));
        btnPin.setTextSize(16);
        btnPin.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        btnPin.setPadding(48, 48, 48, 48);
        btnPin.setBackgroundResource(outValue.resourceId);
        btnPin.setClickable(true);
        btnPin.setFocusable(true);
        layout.addView(btnPin);
        
        btnPin.setOnClickListener(v -> {
            viewModel.togglePin(friend.getUserId());
            dialog.dismiss();
        });
        
        // Delete DM
        android.widget.TextView btnDelete = new android.widget.TextView(context);
        btnDelete.setText("Xóa tin nhắn");
        btnDelete.setTextColor(ContextCompat.getColor(context, R.color.discord_red));
        btnDelete.setTextSize(16);
        btnDelete.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        btnDelete.setPadding(48, 48, 48, 48);
        btnDelete.setBackgroundResource(outValue.resourceId);
        btnDelete.setClickable(true);
        btnDelete.setFocusable(true);
        layout.addView(btnDelete);
        
        btnDelete.setOnClickListener(v -> {
            dialog.dismiss();
            com.example.se114_callingsystem.core.util.BottomSheetUtils.showConfirmDialog(
                context,
                "Xóa tin nhắn",
                "Bạn có chắc chắn muốn xóa toàn bộ tin nhắn với " + (friend.getUsername() != null ? friend.getUsername() : "người này") + " không?",
                "Xóa",
                "#F23F42",
                () -> {
                    viewModel.deleteDirectMessage(friend.getUserId());
                }
            );
        });
        
        dialog.setContentView(layout);
        dialog.show();
    }



    @Override
    public int getItemCount() {
        return friendList != null ? friendList.size() : 0;
    }

    public static class DMViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        ImageView ivPinIcon;
        ImageView ivMoreOptions;
        View viewStatusIndicator;
        TextView tvUserName;
        TextView tvUnreadBadge;


        public DMViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            ivPinIcon = itemView.findViewById(R.id.ivPinIcon);
            ivMoreOptions = itemView.findViewById(R.id.ivMoreOptions);
            viewStatusIndicator = itemView.findViewById(R.id.viewStatusIndicator);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvUnreadBadge = itemView.findViewById(R.id.tvUnreadBadge);
        }
    }
}
