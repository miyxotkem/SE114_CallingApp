package com.example.se114_callingsystem.server;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.model.Server;
import com.example.se114_callingsystem.model.ServerMember;
import com.example.se114_callingsystem.model.User;
import java.util.List;

public class ServerMemberAdapter extends RecyclerView.Adapter<ServerMemberAdapter.ViewHolder> {

    private List<ServerMember> list;
    private Context context;
    private OnMemberActionListener listener;

    public interface OnMemberActionListener {
        void onPromote(ServerMember member);
        void onKick(ServerMember member);
        void onSetNickname(ServerMember member);
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
        
        String displayName = member.getUserName() != null ? member.getUserName() : "Unknown User";
        if (member.getNickname() != null && !member.getNickname().trim().isEmpty()) {
            displayName = member.getNickname() + " (" + displayName + ")";
        }
        holder.tvName.setText(displayName);

        // Hiện Role Badge
        if ("owner".equals(member.getRole()) || "admin".equals(member.getRole())) {
            holder.tvRole.setVisibility(View.VISIBLE);
            holder.tvRole.setText(member.getRole().toUpperCase());
        } else {
            holder.tvRole.setVisibility(View.GONE);
        }

        // Nhấp vào dòng thành viên để đặt biệt danh (Messenger-style)
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSetNickname(member);
            }
        });

        // 3-dot option menu
        holder.btnOptions.setVisibility(View.VISIBLE);
        holder.btnOptions.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.btnOptions);
            popup.getMenu().add("Đặt biệt danh");

            if (!"owner".equals(member.getRole())) {
                if (!"admin".equals(member.getRole())) {
                    popup.getMenu().add("Promote to Admin");
                }
                popup.getMenu().add("Kick from Server");
            }

            popup.setOnMenuItemClickListener(item -> {
                if (item.getTitle().equals("Đặt biệt danh")) {
                    if (listener != null) listener.onSetNickname(member);
                } else if (item.getTitle().equals("Promote to Admin")) {
                    if (listener != null) listener.onPromote(member);
                } else if (item.getTitle().equals("Kick from Server")) {
                    if (listener != null) listener.onKick(member);
                }
                return true;
            });
            popup.show();
        });
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
