package com.example.se114_callingsystem.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se114_callingsystem.Model.User;
import com.example.se114_callingsystem.R;

import java.util.List;

public class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.FriendViewHolder> {

    private List<User> userList;
    private boolean isRequestList;
    private OnFriendActionListener listener;

    public interface OnFriendActionListener {
        void onAccept(User user);
        void onReject(User user);
        void onRemove(User user);
    }

    public FriendAdapter(List<User> userList, boolean isRequestList, OnFriendActionListener listener) {
        this.userList = userList;
        this.isRequestList = isRequestList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend, parent, false);
        return new FriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        User user = userList.get(position);
        
        String name = user.getUsername();
        if (name == null || name.isEmpty()) {
            name = user.getEmail();
        }
        holder.tvUserName.setText(name);

        if (isRequestList) {
            holder.llRequestActions.setVisibility(View.VISIBLE);
            holder.btnRemoveFriend.setVisibility(View.GONE);
            
            holder.btnAccept.setOnClickListener(v -> {
                if (listener != null) listener.onAccept(user);
            });
            holder.btnReject.setOnClickListener(v -> {
                if (listener != null) listener.onReject(user);
            });
        } else {
            holder.llRequestActions.setVisibility(View.GONE);
            holder.btnRemoveFriend.setVisibility(View.VISIBLE);
            
            holder.btnRemoveFriend.setOnClickListener(v -> {
                if (listener != null) listener.onRemove(user);
            });
        }
    }

    @Override
    public int getItemCount() {
        return userList != null ? userList.size() : 0;
    }

    public static class FriendViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName;
        LinearLayout llRequestActions;
        ImageView btnAccept, btnReject, btnRemoveFriend;

        public FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            llRequestActions = itemView.findViewById(R.id.llRequestActions);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnReject = itemView.findViewById(R.id.btnReject);
            btnRemoveFriend = itemView.findViewById(R.id.btnRemoveFriend);
        }
    }
}
