package com.example.se114_callingsystem.features.chat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
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
                case 0: tab.setText("ðŸ‘¥ Members"); break;
                case 1: tab.setText("ðŸ“· Media"); break;
                case 2: tab.setText("ðŸ“Ž Files"); break;
                case 3: tab.setText("ðŸ”— Links"); break;
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
                            setupNicknamesButton(sId);
                            loadServerMembers(sId);
                        } else {
                            loadDMParticipants();
                        }
                    }
                });
        } else if (serverId != null) {
            setupNicknamesButton(serverId);
            loadServerMembers(serverId);
        } else {
            loadDMParticipants();
        }
    }

    private void setupNicknamesButton(String sId) {
        if (binding == null) return;
        binding.btnNicknames.setVisibility(View.VISIBLE);
        binding.dividerNicknames.setVisibility(View.VISIBLE);
        binding.btnNicknames.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("SERVER_ID", sId);
            Navigation.findNavController(v).navigate(R.id.nav_server_manage_members, args);
        });
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

    private void loadDMParticipants() {
        String currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        if (currentUid.isEmpty()) return;

        serverMembers.clear();
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users").document(currentUid).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String username = doc.getString("username");
                    ServerMember me = new ServerMember(currentUid, username, "online");
                    me.setNickname("You");
                    serverMembers.add(me);
                    if (pinnedAdapter != null) {
                        pinnedAdapter.setServerMembers(serverMembers);
                    }
                }
            });
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
    }

    // Getters for child fragments to pull lists
    public List<String> getMediaUrls() { return mediaUrls; }
    public List<Message> getFileMessages() { return fileMessages; }
    public List<String[]> getLinkItems() { return linkItems; }
    public String getServerId() { return serverId; }
    public String getChatId() { return chatId; }
}

