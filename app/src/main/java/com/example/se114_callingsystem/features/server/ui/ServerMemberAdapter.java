package com.example.se114_callingsystem.features.server.ui;

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
import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.core.model.Server;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.example.se114_callingsystem.core.model.User;
import com.example.se114_callingsystem.core.util.ThemeHelper;
import java.util.List;

public class ServerMemberAdapter extends RecyclerView.Adapter<ServerMemberAdapter.ViewHolder> {

    private List<ServerMember> list;
    private Context context;
    private OnMemberActionListener listener;

    public interface OnMemberActionListener {
        void onPromote(ServerMember member);
        void onDemote(ServerMember member);
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
        if ("owner".equals(member.getRole())) {
            holder.tvRole.setVisibility(View.VISIBLE);
            holder.tvRole.setText("OWNER");
            holder.tvRole.setTextColor(android.graphics.Color.parseColor("#F5A623"));
            holder.tvRole.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1AF5A623")));
        } else if ("admin".equals(member.getRole())) {
            holder.tvRole.setVisibility(View.VISIBLE);
            holder.tvRole.setText("ADMIN");
            holder.tvRole.setTextColor(android.graphics.Color.parseColor("#5865F2"));
            holder.tvRole.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1A5865F2")));
        } else {
            holder.tvRole.setVisibility(View.GONE);
        }

        // Lắng nghe trạng thái Online/Offline (Từ Realtime Database)
        if (holder.currentListener != null && holder.currentRef != null) {
            holder.currentRef.removeEventListener(holder.currentListener);
        }
        holder.currentRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users/" + member.getUserId() + "/status");
        holder.currentListener = new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot doc) {
                String status = doc.getValue(String.class);
                if (status == null) status = "offline";
                
                int colorRes = R.color.discord_green;
                if ("offline".equalsIgnoreCase(status) || "invisible".equalsIgnoreCase(status)) colorRes = R.color.discord_text_muted;
                else if ("dnd".equalsIgnoreCase(status)) colorRes = R.color.discord_red;
                else if ("idle".equalsIgnoreCase(status)) colorRes = R.color.discord_yellow;
                
                if (holder.vStatusIndicator instanceof com.google.android.material.card.MaterialCardView) {
                    ((com.google.android.material.card.MaterialCardView) holder.vStatusIndicator).setCardBackgroundColor(context.getResources().getColor(colorRes));
                } else {
                    holder.vStatusIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(context.getResources().getColor(colorRes)));
                }
            }

            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
        };
        holder.currentRef.addValueEventListener(holder.currentListener);
        
        final String finalDisplayName = displayName;
        // Lắng nghe Voice Channel & Avatar (Từ Firestore)
        if (holder.firestoreListener != null) {
            holder.firestoreListener.remove();
        }
        holder.firestoreListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users").document(member.getUserId())
            .addSnapshotListener((doc, error) -> {
                if (doc != null && doc.exists()) {
                    String voiceChannel = doc.getString("currentVoiceChannelName");
                    if (voiceChannel != null && !voiceChannel.isEmpty()) {
                        if (holder.layoutVoiceStatus != null) {
                            holder.layoutVoiceStatus.setVisibility(View.VISIBLE);
                        } else {
                            holder.tvMemberStatus.setVisibility(View.VISIBLE);
                        }
                        holder.tvMemberStatus.setText("Đang ở trong phòng thoại " + voiceChannel);
                    } else {
                        if (holder.layoutVoiceStatus != null) {
                            holder.layoutVoiceStatus.setVisibility(View.GONE);
                        } else {
                            holder.tvMemberStatus.setVisibility(View.GONE);
                        }
                    }

                    // Tải profilePic
                    String profilePic = doc.getString("profilePic");
                    if (profilePic != null && !profilePic.isEmpty()) {
                        if (holder.ivAvatar != null) holder.ivAvatar.setVisibility(View.VISIBLE);
                        if (holder.tvInitials != null) holder.tvInitials.setVisibility(View.GONE);
                        if (holder.ivPlaceholder != null) holder.ivPlaceholder.setVisibility(View.GONE);
                        Glide.with(context).load(profilePic).into(holder.ivAvatar);
                    } else {
                        if (holder.ivAvatar != null) holder.ivAvatar.setVisibility(View.GONE);
                        if (holder.ivPlaceholder != null) holder.ivPlaceholder.setVisibility(View.GONE);
                        if (holder.tvInitials != null) {
                            holder.tvInitials.setVisibility(View.VISIBLE);
                            String initials = finalDisplayName.substring(0, 1).toUpperCase();
                            holder.tvInitials.setText(initials);
                            
                            // Set dynamic background color based on name hash
                            try {
                                com.google.android.material.card.MaterialCardView cardAvatar = holder.itemView.findViewById(R.id.cardMemberAvatar);
                                if (cardAvatar != null) {
                                    int hash = finalDisplayName.hashCode();
                                    int[] presetColors = {0xFF5865F2, 0xFF23A559, 0xFFF5A623, 0xFFEB459E, 0xFF00A8FC};
                                    int chosenColor = presetColors[Math.abs(hash) % presetColors.length];
                                    cardAvatar.setCardBackgroundColor(chosenColor);
                                }
                            } catch (Exception e) {}
                        }
                    }
                } else {
                    if (holder.ivAvatar != null) holder.ivAvatar.setVisibility(View.GONE);
                    if (holder.tvInitials != null) holder.tvInitials.setVisibility(View.GONE);
                    if (holder.ivPlaceholder != null) holder.ivPlaceholder.setVisibility(View.VISIBLE);
                    if (holder.layoutVoiceStatus != null) {
                        holder.layoutVoiceStatus.setVisibility(View.GONE);
                    } else {
                        holder.tvMemberStatus.setVisibility(View.GONE);
                    }
                }
            });

        // Nhấp vào dòng thành viên để đặt biệt danh (Messenger-style)
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSetNickname(member);
            }
        });

        // Nhấp vào avatar để xem Profile
        com.google.android.material.card.MaterialCardView cardAvatar = holder.itemView.findViewById(R.id.cardMemberAvatar);
        if (cardAvatar != null) {
            cardAvatar.setOnClickListener(v -> {
                android.os.Bundle bundle = new android.os.Bundle();
                bundle.putString("USER_ID", member.getUserId());
                androidx.navigation.Navigation.findNavController(v).navigate(R.id.nav_profile, bundle);
            });
        }

        // 3-dot option menu
        holder.btnOptions.setVisibility(View.VISIBLE);
        holder.btnOptions.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.btnOptions);
            popup.getMenu().add("Đặt biệt danh");

            if (!"owner".equals(member.getRole())) {
                if (!"admin".equals(member.getRole())) {
                    popup.getMenu().add("Promote to Admin");
                } else {
                    popup.getMenu().add("Demote to Member");
                }
                popup.getMenu().add("Kick from Server");
            }

            popup.setOnMenuItemClickListener(item -> {
                if (item.getTitle().equals("Đặt biệt danh")) {
                    if (listener != null) listener.onSetNickname(member);
                } else if (item.getTitle().equals("Promote to Admin")) {
                    if (listener != null) listener.onPromote(member);
                } else if (item.getTitle().equals("Demote to Member")) {
                    if (listener != null) listener.onDemote(member);
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

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder.currentListener != null && holder.currentRef != null) {
            holder.currentRef.removeEventListener(holder.currentListener);
            holder.currentListener = null;
        }
        if (holder.firestoreListener != null) {
            holder.firestoreListener.remove();
            holder.firestoreListener = null;
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvRole, tvMemberStatus, tvInitials;
        ImageView btnOptions, ivAvatar, ivPlaceholder;
        View vStatusIndicator, layoutVoiceStatus;
        com.google.firebase.database.ValueEventListener currentListener;
        com.google.firebase.database.DatabaseReference currentRef;
        com.google.firebase.firestore.ListenerRegistration firestoreListener;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvMemberName);
            tvRole = itemView.findViewById(R.id.tvMemberRole);
            tvMemberStatus = itemView.findViewById(R.id.tvMemberStatus);
            btnOptions = itemView.findViewById(R.id.btnMemberOptions);
            vStatusIndicator = itemView.findViewById(R.id.vStatusIndicator);
            tvInitials = itemView.findViewById(R.id.tvMemberInitials);
            ivAvatar = itemView.findViewById(R.id.ivMemberAvatar);
            ivPlaceholder = itemView.findViewById(R.id.ivMemberPlaceholder);
            layoutVoiceStatus = itemView.findViewById(R.id.layoutVoiceStatus);
        }
    }
}
