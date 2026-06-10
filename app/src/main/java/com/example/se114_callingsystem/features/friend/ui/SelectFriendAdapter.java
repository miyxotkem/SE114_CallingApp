package com.example.se114_callingsystem.features.friend.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.User;
import java.util.ArrayList;
import java.util.List;

public class SelectFriendAdapter extends RecyclerView.Adapter<SelectFriendAdapter.SelectFriendViewHolder> {

    private List<User> userList;
    private List<User> selectedUsers = new ArrayList<>();

    public SelectFriendAdapter(List<User> userList) {
        this.userList = userList;
    }

    public List<User> getSelectedUsers() {
        return selectedUsers;
    }

    @NonNull
    @Override
    public SelectFriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_selectable, parent, false);
        return new SelectFriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SelectFriendViewHolder holder, int position) {
        User user = userList.get(position);
        
        String name = user.getUsername();
        if (name == null || name.isEmpty()) {
            name = user.getEmail();
        }
        holder.tvUserName.setText(name);

        // Reset listener to avoid triggering it when recycling
        holder.cbSelect.setOnCheckedChangeListener(null);
        holder.cbSelect.setChecked(selectedUsers.contains(user));

        holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!selectedUsers.contains(user)) {
                    selectedUsers.add(user);
                }
            } else {
                selectedUsers.remove(user);
            }
        });
        
        holder.itemView.setOnClickListener(v -> {
            holder.cbSelect.setChecked(!holder.cbSelect.isChecked());
        });
    }

    @Override
    public int getItemCount() {
        return userList != null ? userList.size() : 0;
    }

    public static class SelectFriendViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName;
        CheckBox cbSelect;

        public SelectFriendViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            cbSelect = itemView.findViewById(R.id.cbSelect);
        }
    }
}
