package com.example.se114_callingsystem.server;

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
import com.example.se114_callingsystem.model.Server;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.ViewHolder> {
    private List<Server> serverList;

    public ServerAdapter(List<Server> serverList) {
        this.serverList = serverList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_list_item_servers, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Server server = serverList.get(position);
        holder.nameText.setText(server.getServerName());

        // Extract initials (e.g. "My Server" -> "MS")
        String initials = getInitials(server.getServerName());
        holder.tvServerInitials.setText(initials);

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
        if (iconUrl != null && !iconUrl.trim().isEmpty()) {
            holder.ivServerIcon.setVisibility(View.VISIBLE);
            holder.tvServerInitials.setVisibility(View.GONE);
            Glide.with(holder.itemView.getContext())
                 .load(iconUrl)
                 .placeholder(R.drawable.icon_user)
                 .into(holder.ivServerIcon);
        } else {
            holder.ivServerIcon.setVisibility(View.GONE);
            holder.tvServerInitials.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("SERVER_ID", server.getServerId());
            args.putString("SERVER_NAME", server.getServerName());
            androidx.navigation.Navigation.findNavController(v).navigate(R.id.action_home_to_server, args);
        });
    }

    private String getInitials(String serverName) {
        if (serverName == null || serverName.trim().isEmpty()) return "?";
        String[] words = serverName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(words.length, 2); i++) {
            if (!words[i].isEmpty()) {
                sb.append(words[i].toUpperCase().charAt(0));
            }
        }
        return sb.toString();
    }

    @Override
    public int getItemCount() { return serverList.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView tvServerInitials;
        ImageView ivServerIcon;
        MaterialCardView cardServerContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.textServerName);
            tvServerInitials = itemView.findViewById(R.id.tvServerInitials);
            ivServerIcon = itemView.findViewById(R.id.ivServerIcon);
            cardServerContainer = itemView.findViewById(R.id.cardServerContainer);
        }
    }
}
