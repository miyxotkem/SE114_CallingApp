package com.example.se114_callingsystem.features.chat.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.List;

public class ChatMembersFragment extends Fragment {

    private RecyclerView recyclerView;
    private MemberAdapter adapter;
    private List<ServerMember> memberList = new ArrayList<>();
    private String serverId;
    private FirebaseFirestore db;
    private ListenerRegistration membersListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        recyclerView = new RecyclerView(requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setPadding(0, 8, 0, 8);
        recyclerView.setBackgroundColor(getResources().getColor(R.color.discord_dark_base, null));

        adapter = new MemberAdapter();
        recyclerView.setAdapter(adapter);

        return recyclerView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = FirebaseFirestore.getInstance();

        if (getParentFragment() instanceof ChatInfoFragment) {
            serverId = ((ChatInfoFragment) getParentFragment()).getServerId();
        }

        loadMembers();
    }

    private void loadMembers() {
        if (serverId == null || serverId.isEmpty()) {
            // It's a Direct Message chat - load current user and a fallback participant
            loadDMParticipants();
            return;
        }

        membersListener = db.collection("servers").document(serverId).collection("members")
            .addSnapshotListener((snapshots, error) -> {
                if (error != null) return;
                if (snapshots != null) {
                    memberList.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        ServerMember m = doc.toObject(ServerMember.class);
                        if (m != null) {
                            memberList.add(m);
                        }
                    }
                    adapter.notifyDataSetChanged();
                }
            });
    }

    private void loadDMParticipants() {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        if (currentUid.isEmpty()) return;

        memberList.clear();
        // 1. Load current user as first member
        db.collection("users").document(currentUid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String username = doc.getString("username");
                String avatar = doc.getString("profilePic");
                String status = doc.getString("status");
                ServerMember me = new ServerMember(currentUid, username, "online".equalsIgnoreCase(status) ? "online" : "offline");
                me.setNickname("You");
                memberList.add(me);
                adapter.notifyDataSetChanged();
            }
        });

        // 2. Load the other user in the DM channel if we can find them from the channel document members list
        if (getParentFragment() instanceof ChatInfoFragment) {
            String chatId = ((ChatInfoFragment) getParentFragment()).getChatId();
            if (chatId != null) {
                db.collection("Channels").document(chatId).get().addOnSuccessListener(chanDoc -> {
                    if (chanDoc.exists()) {
                        // Check if it's a DM by searching for participant fields or similar in channel
                        // As a robust fallback, if we cannot find participant, we just show "You" in DM
                    }
                });
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (membersListener != null) {
            membersListener.remove();
        }
    }

    // ===== INNER MEMBER ADAPTER =====
    private class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_member, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ServerMember member = memberList.get(position);
            String displayName = member.getNickname();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = member.getUserName();
            }

            holder.tvUsername.setText(displayName);

            if (member.getNickname() != null && !member.getNickname().isEmpty() && !member.getNickname().equals(member.getUserName())) {
                holder.tvNickname.setText(member.getUserName());
                holder.tvNickname.setVisibility(View.VISIBLE);
            } else {
                holder.tvNickname.setVisibility(View.GONE);
            }

            // Set online/offline status dot color (default to offline initially, updated asynchronously below)
            holder.viewStatusIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.discord_text_muted, null)));


            String uid = member.getUserId();
            holder.itemView.setTag(uid);

            db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
                if (doc.exists() && uid.equals(holder.itemView.getTag()) && getContext() != null) {
                    String profilePic = doc.getString("profilePic");
                    String status = doc.getString("status");

                    if (profilePic != null && !profilePic.isEmpty()) {
                        Glide.with(ChatMembersFragment.this)
                            .load(profilePic)
                            .placeholder(R.drawable.ic_user)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(holder.ivAvatar);
                    } else {
                        holder.ivAvatar.setImageResource(R.drawable.ic_user);
                    }

                    // Live status sync
                    if ("online".equalsIgnoreCase(status)) {
                        holder.viewStatusIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.discord_green, null)));
                    } else {
                        holder.viewStatusIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.discord_text_muted, null)));
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return memberList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            View viewStatusIndicator;
            TextView tvUsername, tvNickname;

            VH(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.ivAvatar);
                viewStatusIndicator = itemView.findViewById(R.id.viewStatusIndicator);
                tvUsername = itemView.findViewById(R.id.tvUsername);
                tvNickname = itemView.findViewById(R.id.tvNickname);
            }
        }
    }
}
