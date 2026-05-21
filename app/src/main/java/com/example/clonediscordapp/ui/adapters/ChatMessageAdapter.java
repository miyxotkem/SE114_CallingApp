package com.example.clonediscordapp.ui.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.clonediscordapp.data.model.ChatMessage;
import com.example.clonediscordapp.databinding.ItemChatMessageBinding;

import java.util.ArrayList;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ViewHolder> {

    private List<ChatMessage> messages = new ArrayList<>();
    private final OnUserClickListener listener;

    public interface OnUserClickListener {
        void onUserClick(ChatMessage message);
    }

    public ChatMessageAdapter(OnUserClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<ChatMessage> list) {
        this.messages = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemChatMessageBinding binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(messages.get(position));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatMessageBinding binding;

        public ViewHolder(ItemChatMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(ChatMessage msg) {
            Glide.with(itemView.getContext())
                    .load(msg.getSender().getAvatarUrl())
                    .circleCrop()
                    .into(binding.ivAvatar);

            binding.tvAuthor.setText(msg.getSender().getName());
            binding.tvContent.setText(msg.getContent());
            binding.tvTimestamp.setText(msg.getFormattedTimestamp());

            binding.ivAvatar.setOnClickListener(v -> {
                if (listener != null) listener.onUserClick(msg);
            });
            binding.tvAuthor.setOnClickListener(v -> {
                if (listener != null) listener.onUserClick(msg);
            });
        }
    }
}
