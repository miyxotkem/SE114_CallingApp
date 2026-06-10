package com.example.se114_callingsystem.features.chat.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.ChatChannel;
import java.util.List;

public class ChatZoneAdapter extends RecyclerView.Adapter<ChatZoneAdapter.ViewHolder> {
    private List<ChatChannel> channels;
    private OnChannelActionListener listener;
    private String serverColor = "#6C63FF";
    private boolean isAdmin = false;

    public interface OnChannelActionListener {
        void onRename(ChatChannel channel);
        void onRemove(ChatChannel channel);
    }

    public void setServerColor(String color) {
        this.serverColor = color;
        notifyDataSetChanged();
    }

    public void setAdmin(boolean admin) {
        this.isAdmin = admin;
        notifyDataSetChanged();
    }

    public ChatZoneAdapter(List<ChatChannel> channels, OnChannelActionListener listener) {
        this.channels = channels;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_server_chat_channel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatChannel channel = channels.get(position);
        holder.name.setText("# " + channel.getChatName());

        if (isAdmin) {
            holder.btnRename.setVisibility(View.VISIBLE);
            holder.btnRemove.setVisibility(View.VISIBLE);
        } else {
            holder.btnRename.setVisibility(View.GONE);
            holder.btnRemove.setVisibility(View.GONE);
        }

        holder.btnRename.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                listener.onRename(channels.get(currentPos));
            }
        });

        holder.btnRemove.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                listener.onRemove(channels.get(currentPos));
            }
        });

        holder.itemView.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                ChatChannel currentChannel = channels.get(currentPos);
                Bundle args = new Bundle();
                args.putString("CHAT_NAME", currentChannel.getChatName());
                args.putString("CHAT_ID", currentChannel.getChatId());
                args.putString("SERVER_ID", currentChannel.getServerId());
                args.putString("SERVER_COLOR", serverColor);
                
                String transitionName = "chat_transform_" + currentChannel.getChatId();
                holder.itemView.setTransitionName(transitionName);
                args.putString("TRANSITION_NAME", transitionName);
                
                androidx.navigation.fragment.FragmentNavigator.Extras extras = 
                    new androidx.navigation.fragment.FragmentNavigator.Extras.Builder()
                        .addSharedElement(holder.itemView, transitionName)
                        .build();
                
                androidx.navigation.Navigation.findNavController(v).navigate(
                    R.id.action_server_to_server_chat, args, null, extras);
            }
        });
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        ImageView btnRename, btnRemove;

        public ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvChannelName);
            btnRename = itemView.findViewById(R.id.btnRename);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}
