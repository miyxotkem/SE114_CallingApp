package com.example.se114_callingsystem.Adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se114_callingsystem.Model.ServerMember;
import com.example.se114_callingsystem.R;

import java.util.List;

public class ServerMemberAdapter extends RecyclerView.Adapter<ServerMemberAdapter.ViewHolder> {

    private List<ServerMember> list;
    private Context context;
    private OnMemberActionListener listener;

    public interface OnMemberActionListener {
        void onPromote(ServerMember member);
        void onKick(ServerMember member);
    }

    public ServerMemberAdapter(List<ServerMember> list, Context context, OnMemberActionListener listener) {
        this.list = list;
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_server_member, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ServerMember member = list.get(position);
        holder.tvName.setText(member.getUserName() != null ? member.getUserName() : "Unknown User");

        // Hiện Role Badge
        if ("owner".equals(member.getRole()) || "admin".equals(member.getRole())) {
            holder.tvRole.setVisibility(View.VISIBLE);
            holder.tvRole.setText(member.getRole().toUpperCase());
        } else {
            holder.tvRole.setVisibility(View.GONE);
        }

        // Logic ẩn nút 3 chấm nếu là Owner (không thể tự kick)
        if ("owner".equals(member.getRole())) {
            holder.btnOptions.setVisibility(View.GONE);
        } else {
            holder.btnOptions.setVisibility(View.VISIBLE);
            holder.btnOptions.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(context, holder.btnOptions);
                if (!"admin".equals(member.getRole())) {
                    popup.getMenu().add("Promote to Admin");
                }
                popup.getMenu().add("Kick from Server");

                popup.setOnMenuItemClickListener(item -> {
                    if (item.getTitle().equals("Promote to Admin")) listener.onPromote(member);
                    if (item.getTitle().equals("Kick from Server")) listener.onKick(member);
                    return true;
                });
                popup.show();
            });
        }
    }

    @Override
    public int getItemCount() { return list.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvRole;
        ImageView btnOptions;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvMemberName);
            tvRole = itemView.findViewById(R.id.tvMemberRole);
            btnOptions = itemView.findViewById(R.id.btnMemberOptions);
        }
    }
}