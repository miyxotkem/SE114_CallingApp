package com.example.se114_callingsystem.features.server;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.Server;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.ViewHolder> {
    private List<Server> serverList;
    private OnServerClickListener clickListener;
    private String activeServerId;

    public interface OnServerClickListener {
        void onServerClick(Server server);
    }

    public ServerAdapter(List<Server> serverList) {
        this.serverList = serverList;
    }

    public ServerAdapter(List<Server> serverList, String activeServerId, OnServerClickListener clickListener) {
        this.serverList = serverList;
        this.activeServerId = activeServerId;
        this.clickListener = clickListener;
    }

    public void setActiveServerId(String activeServerId) {
        this.activeServerId = activeServerId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_server_sidebar, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Server server = serverList.get(position);
        holder.nameText.setText(server.getServerName());

        // Extract initials (e.g. "My Server" -> "MS")
        String initials = getInitials(server.getServerName());
        holder.tvServerInitials.setText(initials);

        // Active state and indicator handling
        boolean isActive = server.getServerId() != null && server.getServerId().equals(activeServerId);
        if (holder.viewActiveIndicator != null) {
            holder.viewActiveIndicator.setVisibility(isActive ? View.VISIBLE : View.GONE);
        }

        // Morph corner radius based on active state (16dp rounded square vs 24dp circle)
        float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
        if (isActive) {
            holder.cardServerContainer.setRadius(16 * density);
        } else {
            holder.cardServerContainer.setRadius(24 * density);
        }

        // Customize card background color based on accentColor or fallback to Blurple (#5865F2)
        String accentColor = server.getAccentColor();
        if (accentColor != null && !accentColor.isEmpty()) {
            try {
                holder.cardServerContainer.setCardBackgroundColor(Color.parseColor(accentColor));
            } catch (Exception e) {
                holder.cardServerContainer.setCardBackgroundColor(Color.parseColor("#5865F2"));
            }
        } else {
            holder.cardServerContainer.setCardBackgroundColor(Color.parseColor("#5865F2"));
        }

        // Load iconUrl if available
        String iconUrl = server.getIconUrl();
        if (iconUrl != null && !iconUrl.trim().isEmpty() && !iconUrl.equals("default_icon_url")) {
            holder.ivServerIcon.setVisibility(View.VISIBLE);
            holder.tvServerInitials.setVisibility(View.GONE);
            Glide.with(holder.itemView.getContext())
                 .load(iconUrl)
                 .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                 .into(holder.ivServerIcon);
        } else {
            holder.ivServerIcon.setVisibility(View.GONE);
            holder.tvServerInitials.setVisibility(View.VISIBLE);
        }

        // Clear old listener
        if (holder.unreadListener != null) {
            holder.unreadListener.remove();
            holder.unreadListener = null;
        }

        String myUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null 
                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (myUid != null) {
            com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
            db.collection("Channels")
              .whereEqualTo("serverId", server.getServerId())
              .get()
              .addOnSuccessListener(queryDocumentSnapshots -> {
                  if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                      java.util.List<String> channelIds = new java.util.ArrayList<>();
                      for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                          channelIds.add(doc.getId());
                      }
                      
                      // Listen for unread notifications in these channels
                      if (holder.unreadListener == null) {
                          holder.unreadListener = db.collection("users").document(myUid)
                              .collection("notifications")
                              .whereEqualTo("isRead", false)
                              .whereIn("targetId", channelIds)
                              .addSnapshotListener((value, error) -> {
                                  if (error != null) return;
                                  if (value != null && !value.isEmpty()) {
                                      holder.tvUnreadBadge.setText(String.valueOf(value.size()));
                                      holder.tvUnreadBadge.setVisibility(View.VISIBLE);
                                  } else {
                                      holder.tvUnreadBadge.setVisibility(View.GONE);
                                  }
                              });
                      }
                  } else {
                      holder.tvUnreadBadge.setVisibility(View.GONE);
                  }
              });
        } else {
            holder.tvUnreadBadge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onServerClick(server);
            } else {
                Bundle args = new Bundle();
                args.putString("SERVER_ID", server.getServerId());
                args.putString("SERVER_NAME", server.getServerName());
                androidx.navigation.Navigation.findNavController(v).navigate(R.id.action_home_to_server, args);
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder.unreadListener != null) {
            holder.unreadListener.remove();
            holder.unreadListener = null;
        }
    }

    private String getInitials(String serverName) {
        if (serverName == null || serverName.trim().isEmpty()) return "?";
        return String.valueOf(serverName.trim().charAt(0)).toUpperCase();
    }

    @Override
    public int getItemCount() { return serverList.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView tvServerInitials;
        ImageView ivServerIcon;
        MaterialCardView cardServerContainer;
        TextView tvUnreadBadge;
        View viewActiveIndicator;
        com.google.firebase.firestore.ListenerRegistration unreadListener;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.textServerName);
            tvServerInitials = itemView.findViewById(R.id.tvServerInitials);
            ivServerIcon = itemView.findViewById(R.id.ivServerIcon);
            cardServerContainer = itemView.findViewById(R.id.cardServerContainer);
            tvUnreadBadge = itemView.findViewById(R.id.tvUnreadBadge);
            viewActiveIndicator = itemView.findViewById(R.id.viewActiveIndicator);
        }
    }
}

