package com.example.se114_callingsystem.features.server;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.PostChannel;
import com.example.se114_callingsystem.features.post.PostChannelActivity;
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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_server_chat_channel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PostChannel channel = channels.get(position);
        holder.name.setText("ðŸ“° " + channel.getName()); // Use ðŸ“° icon for posts

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
                android.os.Bundle bundle = new android.os.Bundle();
                bundle.putString("CHANNEL_NAME", currentChannel.getName());
                bundle.putString("CHANNEL_ID", currentChannel.getId());
                bundle.putString("SERVER_ID", currentChannel.getServerId());
                bundle.putString("SERVER_COLOR", serverColor);
                try {
                    androidx.navigation.Navigation.findNavController(v).navigate(R.id.action_server_to_post_channel, bundle);
                } catch (Exception e) {
                    Intent intent = new Intent(holder.itemView.getContext(), PostChannelActivity.class);
                    intent.putExtra("CHANNEL_NAME", currentChannel.getName());
                    intent.putExtra("CHANNEL_ID", currentChannel.getId());
                    intent.putExtra("SERVER_ID", currentChannel.getServerId());
                    intent.putExtra("SERVER_COLOR", serverColor);
                    holder.itemView.getContext().startActivity(intent);
                }
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

