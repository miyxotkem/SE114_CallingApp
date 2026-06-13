package com.example.se114_callingsystem.features.chat.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.databinding.FragmentChatInfoBinding;
import com.example.se114_callingsystem.core.model.Firebase;
import com.example.se114_callingsystem.core.model.Message;

import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Color;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.example.se114_callingsystem.core.model.ServerMember;

public class ChatInfoFragment extends Fragment {

    private static final String TAG = "ChatInfoFragment";

    private FragmentChatInfoBinding binding;
    private String chatId, chatName, serverId;
    private String serverColor = "#5865F2";
    
    private DatabaseReference chatRef;
    private ValueEventListener messagesListener;

    private List<String> mediaUrls = new ArrayList<>();
    private List<Message> fileMessages = new ArrayList<>();
    private List<String[]> linkItems = new ArrayList<>(); // [url, contextText]
    private List<Message> currentPinnedMessages = new ArrayList<>();
    private List<ServerMember> serverMembers = new ArrayList<>();
    private PinnedMessagesAdapter pinnedAdapter;

    private ChatInfoPagerAdapter pagerAdapter;
    private com.google.firebase.firestore.ListenerRegistration dmNicknamesListener;

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)",
            Pattern.CASE_INSENSITIVE
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatInfoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            chatId = getArguments().getString("CHAT_ID");
            chatName = getArguments().getString("CHAT_NAME");
            serverId = getArguments().getString("SERVER_ID");
            serverColor = getArguments().getString("SERVER_COLOR", "#5865F2");
        }

        if (chatId == null) {
            Toast.makeText(getContext(), "Error: Chat ID not found", Toast.LENGTH_SHORT).show();
            Navigation.findNavController(view).popBackStack();
            return;
        }

        initViews();
        setupTabs();
        loadMessages();
        setupNicknames();
        setupMuteNotifications();
        setupSearchInChat();
        setupPinnedMessages();
    }

    private void initViews() {
        if (binding == null) return;

        binding.btnBack.setOnClickListener(v -> {
            Navigation.findNavController(v).popBackStack();
        });

        if (chatName != null) {
            binding.tvChannelInfoName.setText("# " + chatName.toLowerCase());
        }
    }

    private void setupTabs() {
        if (binding == null) return;
        pagerAdapter = new ChatInfoPagerAdapter(this, mediaUrls, fileMessages, linkItems);
        binding.viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("📷 Media"); break;
                case 1: tab.setText("📎 Files"); break;
                case 2: tab.setText("🔗 Links"); break;
            }
        }).attach();
    }

    private void loadMessages() {
        if (chatId == null) return;
        chatRef = Firebase.getDatabase().getReference("chats").child(chatId);

        messagesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;
                
                mediaUrls.clear();
                fileMessages.clear();
                linkItems.clear();
                currentPinnedMessages.clear();

                for (DataSnapshot data : snapshot.getChildren()) {
                    Message msg = data.getValue(Message.class);
                    if (msg == null || msg.isDeleted()) continue;

                    msg.setMessageId(data.getKey());

                    if (msg.isPinned()) {
                        currentPinnedMessages.add(msg);
                    }

                    String type = msg.getType();
                    String content = msg.getContent();
                    if (content == null) continue;

                    if ("image".equals(type)) {
                        mediaUrls.add(content);
                    } else if ("file".equals(type)) {
                        fileMessages.add(msg);
                    } else {
                        Matcher matcher = URL_PATTERN.matcher(content);
                        while (matcher.find()) {
                            String url = matcher.group(1);
                            String ctx = content.length() > 60 ? content.substring(0, 60) + "..." : content;
                            linkItems.add(new String[]{url, ctx});
                        }
                    }
                }

                if (binding != null) {
                    binding.tvMediaCount.setText(String.valueOf(mediaUrls.size()));
                    binding.tvFileCount.setText(String.valueOf(fileMessages.size()));
                    binding.tvLinkCount.setText(String.valueOf(linkItems.size()));
                }

                if (pagerAdapter != null) {
                    pagerAdapter.notifyDataSetChanged();
                }
                if (pinnedAdapter != null) {
                    pinnedAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        chatRef.addValueEventListener(messagesListener);
    }

    private void setupMuteNotifications() {
        if (binding == null || getContext() == null) return;
        
        SharedPreferences prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        boolean isMuted = prefs.getBoolean("mute_" + chatId, false);
        binding.switchMute.setChecked(isMuted);

        binding.switchMute.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("mute_" + chatId, isChecked).apply();
        });

        binding.btnMuteNotifications.setOnClickListener(v -> {
            if (binding != null) {
                binding.switchMute.setChecked(!binding.switchMute.isChecked());
            }
        });
    }

    private void setupSearchInChat() {
        if (binding == null) return;
        binding.btnSearchInChat.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("CHAT_ID", chatId);
            args.putString("CHAT_NAME", chatName);
            args.putString("SERVER_ID", serverId);
            args.putString("SERVER_COLOR", serverColor);
            Navigation.findNavController(v).navigate(R.id.action_chat_to_search, args);
        });
    }

    private void setupNicknames() {
        if (serverId == null && chatId != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("Channels").document(chatId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && binding != null) {
                        String sId = documentSnapshot.getString("serverId");
                        if (sId != null) {
                            serverId = sId;
                            loadServerMembers(sId);
                        } else {
                            setupDMNicknamesButton();
                            loadDMParticipants();
                        }
                    } else if (binding != null) {
                        setupDMNicknamesButton();
                        loadDMParticipants();
                    }
                });
        } else if (serverId != null) {
            loadServerMembers(serverId);
        } else {
            setupDMNicknamesButton();
            loadDMParticipants();
        }
    }

    private void loadServerMembers(String sId) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("servers").document(sId).collection("members")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (queryDocumentSnapshots != null) {
                    serverMembers.clear();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        ServerMember m = doc.toObject(ServerMember.class);
                        if (m != null) {
                            serverMembers.add(m);
                        }
                    }
                    if (pinnedAdapter != null) {
                        pinnedAdapter.setServerMembers(serverMembers);
                    }
                }
            });
    }

    private void setupDMNicknamesButton() {
        if (binding == null) return;
        binding.btnNicknames.setVisibility(View.VISIBLE);
        binding.dividerNicknames.setVisibility(View.VISIBLE);
        
        try {
            android.widget.TextView tvNicknames = (android.widget.TextView) binding.btnNicknames.getChildAt(1);
            if (tvNicknames != null) {
                tvNicknames.setText("Đặt biệt danh");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        binding.btnNicknames.setOnClickListener(v -> showDMNicknamesDialog());
    }

    private void showDMNicknamesDialog() {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(requireContext());
        android.widget.LinearLayout root = new android.widget.LinearLayout(requireContext());
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(Color.parseColor("#313338"));
        
        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("Đặt biệt danh");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setPadding(0, 0, 0, 32);
        root.addView(tvTitle);
        
        for (ServerMember member : serverMembers) {
            View itemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_chat_member, root, false);
            
            ImageView ivAvatar = itemView.findViewById(R.id.ivAvatar);
            View viewStatusIndicator = itemView.findViewById(R.id.viewStatusIndicator);
            TextView tvUsername = itemView.findViewById(R.id.tvUsername);
            TextView tvNickname = itemView.findViewById(R.id.tvNickname);
            
            String displayName = member.getNickname();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = member.getUserName();
            }
            tvUsername.setText(displayName);
            
            if (member.getNickname() != null && !member.getNickname().isEmpty() && !member.getNickname().equals(member.getUserName())) {
                tvNickname.setText(member.getUserName());
                tvNickname.setVisibility(View.VISIBLE);
            } else {
                tvNickname.setVisibility(View.GONE);
            }
            
            viewStatusIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.discord_text_muted, null)));
            String uid = member.getUserId();
            itemView.setTag(uid);
            
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && uid.equals(itemView.getTag()) && getContext() != null) {
                        String profilePic = doc.getString("profilePic");
                        String status = doc.getString("status");
                        
                        if (profilePic != null && !profilePic.isEmpty()) {
                            Glide.with(ChatInfoFragment.this)
                                .load(profilePic)
                                .placeholder(R.drawable.ic_user)
                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                                .into(ivAvatar);
                        } else {
                            ivAvatar.setImageResource(R.drawable.ic_user);
                        }
                        
                        if ("online".equalsIgnoreCase(status)) {
                            viewStatusIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.discord_green, null)));
                        } else {
                            viewStatusIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.discord_text_muted, null)));
                        }
                    }
                });
                
            itemView.setOnClickListener(v -> {
                dialog.dismiss();
                showSetNicknameDialog(member);
            });
            
            root.addView(itemView);
        }
        
        scrollView.addView(root);
        dialog.setContentView(scrollView);
        dialog.show();
    }

    private void showSetNicknameDialog(ServerMember member) {
        if (getContext() == null) return;
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setText(member.getNickname() != null ? member.getNickname() : "");
        input.setSelection(input.getText().length());
        
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Đặt biệt danh")
            .setMessage("Đặt biệt danh cho " + member.getUserName())
            .setView(input)
            .setPositiveButton("Lưu", (d, w) -> {
                String nickname = input.getText().toString().trim();
                saveDMNickname(member.getUserId(), nickname);
            })
            .setNegativeButton("Hủy", null)
            .show();
    }

    private void saveDMNickname(String targetUid, String newNickname) {
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        java.util.Map<String, Object> update = new java.util.HashMap<>();
        update.put("nicknames." + targetUid, newNickname);
        
        db.collection("Channels").document(chatId).update(update)
            .addOnFailureListener(e -> {
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                java.util.Map<String, Object> nicknames = new java.util.HashMap<>();
                nicknames.put(targetUid, newNickname);
                data.put("nicknames", nicknames);
                db.collection("Channels").document(chatId).set(data, com.google.firebase.firestore.SetOptions.merge());
            });
    }

    private void loadDMParticipants() {
        String currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        if (currentUid.isEmpty()) return;

        serverMembers.clear();
        
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        
        db.collection("users").document(currentUid).get()
            .addOnSuccessListener(docMe -> {
                if (docMe.exists() && binding != null) {
                    String username = docMe.getString("username");
                    ServerMember me = new ServerMember(currentUid, username, "online");
                    serverMembers.add(me);
                    
                    String otherUid = null;
                    if (chatId != null && chatId.startsWith("dm_")) {
                        String[] parts = chatId.split("_");
                        if (parts.length == 3) {
                            otherUid = parts[1].equals(currentUid) ? parts[2] : parts[1];
                        }
                    }
                    
                    if (otherUid != null) {
                        String finalOtherUid = otherUid;
                        db.collection("users").document(finalOtherUid).get()
                            .addOnSuccessListener(docOther -> {
                                if (docOther.exists() && binding != null) {
                                    String otherUsername = docOther.getString("username");
                                    ServerMember other = new ServerMember(finalOtherUid, otherUsername, "online");
                                    serverMembers.add(other);
                                    
                                    listenToDMNicknames();
                                }
                            });
                    } else {
                        listenToDMNicknames();
                    }
                }
            });
    }

    private void listenToDMNicknames() {
        if (chatId == null) return;
        if (dmNicknamesListener != null) {
            dmNicknamesListener.remove();
        }
        
        dmNicknamesListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("Channels").document(chatId)
            .addSnapshotListener((snapshot, error) -> {
                if (error != null || snapshot == null || !snapshot.exists()) return;
                
                java.util.Map<String, Object> nicknames = (java.util.Map<String, Object>) snapshot.get("nicknames");
                if (nicknames != null) {
                    for (ServerMember m : serverMembers) {
                        String uid = m.getUserId();
                        if (nicknames.containsKey(uid)) {
                            m.setNickname((String) nicknames.get(uid));
                        }
                    }
                    if (pinnedAdapter != null) {
                        pinnedAdapter.setServerMembers(serverMembers);
                    }
                    updateChatHeaderTitle(nicknames);
                }
            });
    }

    private void updateChatHeaderTitle(java.util.Map<String, Object> nicknames) {
        if (binding == null) return;
        String currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        String otherUid = null;
        if (chatId != null && chatId.startsWith("dm_")) {
            String[] parts = chatId.split("_");
            if (parts.length == 3) {
                otherUid = parts[1].equals(currentUid) ? parts[2] : parts[1];
            }
        }
        if (otherUid != null && nicknames.containsKey(otherUid)) {
            String nickname = (String) nicknames.get(otherUid);
            if (nickname != null && !nickname.trim().isEmpty()) {
                binding.tvChannelInfoName.setText("# " + nickname.toLowerCase());
            }
        }
    }

    private void setupPinnedMessages() {
        if (binding == null) return;
        binding.btnPinnedMessages.setOnClickListener(v -> showPinnedMessages());
    }

    private void showPinnedMessages() {
        if (getContext() == null) return;
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_chat_bottom_sheet_pinned_messages, null);
        bottomSheetDialog.setContentView(sheetView);

        try {
            ((View) sheetView.getParent()).setBackgroundColor(Color.TRANSPARENT);
        } catch (Exception e) {}

        RecyclerView rvPinnedMessages = sheetView.findViewById(R.id.rvPinnedMessages);
        View tvNoPinnedMessages = sheetView.findViewById(R.id.tvNoPinnedMessages);

        if (currentPinnedMessages.isEmpty()) {
            if (tvNoPinnedMessages != null) tvNoPinnedMessages.setVisibility(View.VISIBLE);
            if (rvPinnedMessages != null) rvPinnedMessages.setVisibility(View.GONE);
        } else {
            if (tvNoPinnedMessages != null) tvNoPinnedMessages.setVisibility(View.GONE);
            if (rvPinnedMessages != null) rvPinnedMessages.setVisibility(View.VISIBLE);
        }

        pinnedAdapter = new PinnedMessagesAdapter(currentPinnedMessages, serverColor, new PinnedMessagesAdapter.OnPinnedMessageInteractListener() {
            @Override
            public void onGoTo(Message message) {
                androidx.navigation.NavController navController = Navigation.findNavController(requireView());
                if (navController.getPreviousBackStackEntry() != null) {
                    navController.getPreviousBackStackEntry().getSavedStateHandle().set("GOTO_MESSAGE_ID", message.getMessageId());
                }
                navController.popBackStack();
                bottomSheetDialog.dismiss();
            }

            @Override
            public void onUnpin(Message message) {
                if (chatRef != null && message.getMessageId() != null) {
                    chatRef.child(message.getMessageId()).child("pinned").setValue(false)
                        .addOnSuccessListener(aVoid -> {
                            currentPinnedMessages.remove(message);
                            if (currentPinnedMessages.isEmpty()) {
                                if (tvNoPinnedMessages != null) tvNoPinnedMessages.setVisibility(View.VISIBLE);
                                if (rvPinnedMessages != null) rvPinnedMessages.setVisibility(View.GONE);
                            } else {
                                if (pinnedAdapter != null) pinnedAdapter.notifyDataSetChanged();
                            }
                        });
                }
            }
        });
        pinnedAdapter.setServerMembers(serverMembers);

        if (rvPinnedMessages != null) {
            rvPinnedMessages.setLayoutManager(new LinearLayoutManager(getContext()));
            rvPinnedMessages.setAdapter(pinnedAdapter);
        }

        bottomSheetDialog.setOnDismissListener(dialog -> {
            pinnedAdapter = null;
        });

        bottomSheetDialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (chatRef != null && messagesListener != null) {
            chatRef.removeEventListener(messagesListener);
        }
        if (dmNicknamesListener != null) {
            dmNicknamesListener.remove();
            dmNicknamesListener = null;
        }
    }

    // Getters for child fragments to pull lists
    public List<String> getMediaUrls() { return mediaUrls; }
    public List<Message> getFileMessages() { return fileMessages; }
    public List<String[]> getLinkItems() { return linkItems; }
    public String getServerId() { return serverId; }
    public String getChatId() { return chatId; }
}
