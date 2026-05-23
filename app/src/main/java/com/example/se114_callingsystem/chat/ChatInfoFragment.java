package com.example.se114_callingsystem.chat;

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
import com.example.se114_callingsystem.model.Firebase;
import com.example.se114_callingsystem.model.Message;
import com.example.se114_callingsystem.server.ManageMembersActivity;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                case 0: tab.setText("👥 Members"); break;
                case 1: tab.setText("📷 Media"); break;
                case 2: tab.setText("📎 Files"); break;
                case 3: tab.setText("🔗 Links"); break;
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

                for (DataSnapshot data : snapshot.getChildren()) {
                    Message msg = data.getValue(Message.class);
                    if (msg == null || msg.isDeleted()) continue;

                    String type = msg.getType();
                    String content = msg.getContent();
                    if (content == null) continue;

                    if ("image".equals(type)) {
                        mediaUrls.add(content);
                    } else if ("file".equals(type)) {
                        msg.setMessageId(data.getKey());
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
            Intent intent = new Intent(requireContext(), SearchInChatActivity.class);
            intent.putExtra("CHAT_ID", chatId);
            intent.putExtra("CHAT_NAME", chatName);
            intent.putExtra("SERVER_ID", serverId);
            intent.putExtra("SERVER_COLOR", serverColor);
            startActivity(intent);
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
                        }
                    }
                });
        } else if (serverId != null) {
            setupNicknamesButton(serverId);
        }
    }

    private void setupNicknamesButton(String sId) {
        if (binding == null) return;
        binding.btnNicknames.setVisibility(View.VISIBLE);
        binding.dividerNicknames.setVisibility(View.VISIBLE);
        binding.btnNicknames.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), ManageMembersActivity.class);
            intent.putExtra("SERVER_ID", sId);
            startActivity(intent);
        });
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
