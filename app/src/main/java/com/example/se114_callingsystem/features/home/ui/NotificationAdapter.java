package com.example.se114_callingsystem.features.home.ui;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.NotificationItem;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private final List<NotificationItem> items;
    private final OnNotificationClickListener listener;

    public interface OnNotificationClickListener {
        void onNotificationClick(NotificationItem item);
    }

    public NotificationAdapter(List<NotificationItem> items, OnNotificationClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationItem item = items.get(position);
        Context context = holder.itemView.getContext();

        holder.tvTitle.setText(item.getTitle());
        holder.tvContent.setText(item.getContent());

        // Format timestamp to relative "Time Ago"
        CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                item.getTimestamp(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_ALL
            );
        holder.tvTime.setText(timeAgo);

        // Choose icon and color tint based on type
        int iconRes = R.drawable.ic_tab_notifications;
        boolean isMissedCall = "missed_call".equals(item.getType());
        
        if ("dm".equals(item.getType())) {
            iconRes = R.drawable.ic_chat_bubble;
        } else if ("mention".equals(item.getType())) {
            iconRes = R.drawable.ic_chat_bubble;
        } else if ("friend_request".equals(item.getType()) || "friend_accepted".equals(item.getType())) {
            iconRes = android.R.drawable.ic_menu_myplaces;
        } else if (isMissedCall) {
            iconRes = android.R.drawable.sym_call_missed;
        } else if ("reminder_alert".equals(item.getType())) {
            iconRes = android.R.drawable.ic_lock_idle_alarm;
        } else if ("new_post".equals(item.getType()) || "post_reply".equals(item.getType())) {
            iconRes = R.drawable.ic_file_modern;
        }
        
        holder.ivIcon.setImageResource(iconRes);
        if (isMissedCall) {
            holder.ivIcon.setColorFilter(android.graphics.Color.parseColor("#F04747"));
        } else {
            holder.ivIcon.clearColorFilter();
        }

        // Set unread status
        holder.viewUnread.setVisibility(item.isRead() ? View.GONE : View.VISIBLE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNotificationClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public static class NotificationViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle, tvTime, tvContent;
        View viewUnread;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivNotificationIcon);
            tvTitle = itemView.findViewById(R.id.tvNotificationTitle);
            tvTime = itemView.findViewById(R.id.tvNotificationTime);
            tvContent = itemView.findViewById(R.id.tvNotificationContent);
            viewUnread = itemView.findViewById(R.id.viewUnreadIndicator);
        }
    }
}
