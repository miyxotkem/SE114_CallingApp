package com.example.se114_callingsystem.server;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.model.PostChannel;
import com.example.se114_callingsystem.post.PostChannelActivity;
import java.util.List;

public class PostChannelAdapter extends RecyclerView.Adapter<PostChannelAdapter.ViewHolder> {
    private List<PostChannel> channels;
    private OnChannelActionListener listener;
    private String serverColor = "#6C63FF";

    public interface OnChannelActionListener {
        void onRename(PostChannel channel);
        void onRemove(PostChannel channel);
    }

    public void setServerColor(String color) {
        this.serverColor = color;
        notifyDataSetChanged();
    }

    public PostChannelAdapter(List<PostChannel> channels, OnChannelActionListener listener) {
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
        PostChannel channel = channels.get(position);
        holder.name.setText("📰 " + channel.getName()); // Use 📰 icon for posts

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
                PostChannel currentChannel = channels.get(currentPos);
                Intent intent = new Intent(holder.itemView.getContext(), PostChannelActivity.class);
                intent.putExtra("CHANNEL_NAME", currentChannel.getName());
                intent.putExtra("CHANNEL_ID", currentChannel.getId());
                intent.putExtra("SERVER_ID", currentChannel.getServerId());
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
