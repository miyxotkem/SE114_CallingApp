package com.example.se114_callingsystem.Adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se114_callingsystem.Model.ChatChannel;
import com.example.se114_callingsystem.Activity.Page.ChatDetailActivity;
import com.example.se114_callingsystem.R;

import java.util.List;

public class ChatZoneAdapter extends RecyclerView.Adapter<ChatZoneAdapter.ViewHolder> {
    private List<ChatChannel> channels;
    private OnChannelActionListener listener;
    private String serverColor = "#6C63FF";

    public interface OnChannelActionListener {
        void onRename(ChatChannel channel);
        void onRemove(ChatChannel channel);
    }

    public void setServerColor(String color) {
        this.serverColor = color;
        notifyDataSetChanged();
    }

    public ChatZoneAdapter(List<ChatChannel> channels, OnChannelActionListener listener) {
        this.channels = channels;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_list_item_chat_channels, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatChannel channel = channels.get(position);
        holder.name.setText("# " + channel.getChatName());

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
                ChatChannel currentChannel = channels.get(currentPos); // Get the channel object
                Intent intent = new Intent(holder.itemView.getContext(), ChatDetailActivity.class);

                // Pass the name and ID to the next activity
                intent.putExtra("CHAT_NAME", currentChannel.getChatName());
                intent.putExtra("CHAT_ID", currentChannel.getChatId());
                intent.putExtra("SERVER_COLOR", serverColor);

                holder.itemView.getContext().startActivity(intent);
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