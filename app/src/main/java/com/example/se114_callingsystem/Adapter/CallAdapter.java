package com.example.se114_callingsystem.Adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se114_callingsystem.Activity.Page.CallDetailActivity;
import com.example.se114_callingsystem.Model.CallChannel;
import com.example.se114_callingsystem.R;

import java.util.List;

public class CallAdapter extends RecyclerView.Adapter<CallAdapter.ViewHolder> {
    private List<CallChannel> channels;
    private OnCallActionListener listener;
    private String serverColor = "#6C63FF";

    public interface OnCallActionListener {
        void onRename(CallChannel channel);
        void onRemove(CallChannel channel);
    }

    public void setServerColor(String color) {
        this.serverColor = color;
        notifyDataSetChanged();
    }

    public CallAdapter(List<CallChannel> channels, OnCallActionListener listener) {
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
        CallChannel channel = channels.get(position);
        holder.name.setText("🔊 " + channel.getCallName());

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
                Intent intent = new Intent(holder.itemView.getContext(), CallDetailActivity.class);
                intent.putExtra("CALL_CHANNEL_NAME", channels.get(currentPos).getCallName());
                intent.putExtra("SERVER_COLOR", serverColor);

                holder.itemView.getContext().startActivity(intent);
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