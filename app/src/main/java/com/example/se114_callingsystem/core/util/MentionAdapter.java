package com.example.se114_callingsystem.util;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.model.ServerMember;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.List;

public class MentionAdapter extends RecyclerView.Adapter<MentionAdapter.ViewHolder> {
    private List<ServerMember> list;
    private OnMentionClickListener listener;

    public interface OnMentionClickListener {
        void onMentionClick(ServerMember member);
    }

    public MentionAdapter(List<ServerMember> list, OnMentionClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    public void setList(List<ServerMember> list) {
        this.list = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mention_suggestion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ServerMember member = list.get(position);
        holder.tvName.setText(member.getNickname() != null && !member.getNickname().isEmpty() ? member.getNickname() : member.getUserName());

        FirebaseFirestore.getInstance().collection("users").document(member.getUserId()).get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String profilePic = documentSnapshot.getString("profilePic");
                    if (profilePic != null && !profilePic.isEmpty()) {
                        Glide.with(holder.itemView.getContext()).load(profilePic).into(holder.ivAvatar);
                    } else {
                        holder.ivAvatar.setImageResource(R.drawable.icon_user);
                    }
                }
            });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onMentionClick(member);
        });
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvName = itemView.findViewById(R.id.tvUsername);
        }
    }
}
