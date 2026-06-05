package com.example.se114_callingsystem.features.call;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.CallChannel;
import java.util.List;

public class CallChannelAdapter extends RecyclerView.Adapter<CallChannelAdapter.ViewHolder> {
    private List<CallChannel> channels;
    private OnCallActionListener listener;
    private String serverColor = "#6C63FF";
    private boolean isAdmin = false;

    public interface OnCallActionListener {
        void onRename(CallChannel channel);
        void onRemove(CallChannel channel);
        void onJoinCall(CallChannel channel);
    }

    public void setServerColor(String color) {
        this.serverColor = color;
        notifyDataSetChanged();
    }

    public void setAdmin(boolean admin) {
        this.isAdmin = admin;
        notifyDataSetChanged();
    }

    public CallChannelAdapter(List<CallChannel> channels, OnCallActionListener listener) {
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
        CallChannel channel = channels.get(position);
        holder.name.setText("🔊 " + channel.getCallName());

        if (isAdmin) {
            holder.btnRename.setVisibility(View.VISIBLE);
            holder.btnRemove.setVisibility(View.VISIBLE);
        } else {
            holder.btnRename.setVisibility(View.GONE);
            holder.btnRemove.setVisibility(View.GONE);
        }

        holder.btnRename.setOnClickListener(v -> {
            if (holder.getAdapterPosition() != RecyclerView.NO_POSITION)
                listener.onRename(channels.get(holder.getAdapterPosition()));
        });

        holder.btnRemove.setOnClickListener(v -> {
            if (holder.getAdapterPosition() != RecyclerView.NO_POSITION)
                listener.onRemove(channels.get(holder.getAdapterPosition()));
        });

        holder.itemView.setOnClickListener(v->{
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                listener.onJoinCall(channels.get(currentPos));
            }
        });
    }

    @Override
    public int getItemCount() { return channels.size(); }

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


