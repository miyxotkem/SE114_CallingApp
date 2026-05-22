package com.example.se114_callingsystem.chat;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.model.Firebase;
import com.example.se114_callingsystem.model.Message;
import com.example.se114_callingsystem.server.ManageMembersActivity;
import com.example.se114_callingsystem.util.ThemeHelper;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatInfoActivity extends AppCompatActivity {

    private String chatId, chatName, serverId;
    private DatabaseReference chatRef;

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private TextView tvChannelInfoName, tvMediaCount, tvFileCount, tvLinkCount;

    // Data lists shared with fragments
    private List<String> mediaUrls = new ArrayList<>();
    private List<Message> fileMessages = new ArrayList<>();
    private List<String[]> linkItems = new ArrayList<>(); // [url, contextText]

    private ChatInfoPagerAdapter pagerAdapter;
    private androidx.activity.result.ActivityResultLauncher<android.content.Intent> searchInChatLauncher;

    // URL regex pattern
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_info);

        chatId = getIntent().getStringExtra("CHAT_ID");
        chatName = getIntent().getStringExtra("CHAT_NAME");
        serverId = getIntent().getStringExtra("SERVER_ID");

        searchInChatLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    setResult(RESULT_OK, result.getData());
                    finish();
                }
            }
        );

        initViews();
        setupTabs();
        loadMessages();
        setupNicknames();
        setupMuteNotifications();
        setupSearchInChat();
    }

    private void setupMuteNotifications() {
        com.google.android.material.switchmaterial.SwitchMaterial switchMute = findViewById(R.id.switchMute);
        android.widget.LinearLayout btnMuteNotifications = findViewById(R.id.btnMuteNotifications);
        
        if (switchMute != null && chatId != null) {
            android.content.SharedPreferences prefs = getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
            boolean isMuted = prefs.getBoolean("mute_" + chatId, false);
            switchMute.setChecked(isMuted);

            switchMute.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("mute_" + chatId, isChecked).apply();
            });

            if (btnMuteNotifications != null) {
                btnMuteNotifications.setOnClickListener(v -> {
                    switchMute.setChecked(!switchMute.isChecked());
                });
            }
        }
    }

    private void setupSearchInChat() {
        android.widget.LinearLayout btnSearchInChat = findViewById(R.id.btnSearchInChat);
        if (btnSearchInChat != null) {
            btnSearchInChat.setOnClickListener(v -> {
                String serverColor = getIntent().getStringExtra("SERVER_COLOR");
                if (serverColor == null) serverColor = "#6C63FF";
                android.content.Intent intent = new android.content.Intent(this, SearchInChatActivity.class);
                intent.putExtra("CHAT_ID", chatId);
                intent.putExtra("CHAT_NAME", chatName);
                intent.putExtra("SERVER_ID", serverId);
                intent.putExtra("SERVER_COLOR", serverColor);
                searchInChatLauncher.launch(intent);
            });
        }
    }

    private void setupNicknames() {
        if (serverId == null && chatId != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("Channels").document(chatId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
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
        android.widget.LinearLayout btnNicknames = findViewById(R.id.btnNicknames);
        if (btnNicknames != null) {
            btnNicknames.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this, ManageMembersActivity.class);
                intent.putExtra("SERVER_ID", sId);
                startActivity(intent);
            });
        }
    }

    private void initViews() {
        ImageView btnBack = findViewById(R.id.btnBack);
        tvChannelInfoName = findViewById(R.id.tvChannelInfoName);
        tvMediaCount = findViewById(R.id.tvMediaCount);
        tvFileCount = findViewById(R.id.tvFileCount);
        tvLinkCount = findViewById(R.id.tvLinkCount);
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);

        btnBack.setOnClickListener(v -> finish());

        if (chatName != null) {
            tvChannelInfoName.setText("# " + chatName);
        }
    }

    private void setupTabs() {
        pagerAdapter = new ChatInfoPagerAdapter(this, mediaUrls, fileMessages, linkItems);
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
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

        chatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
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
                        // Extract URLs from text messages
                        Matcher matcher = URL_PATTERN.matcher(content);
                        while (matcher.find()) {
                            String url = matcher.group(1);
                            // Context = full message text (truncated)
                            String ctx = content.length() > 60 ? content.substring(0, 60) + "..." : content;
                            linkItems.add(new String[]{url, ctx});
                        }
                    }
                }

                // Update counts
                tvMediaCount.setText(String.valueOf(mediaUrls.size()));
                tvFileCount.setText(String.valueOf(fileMessages.size()));
                tvLinkCount.setText(String.valueOf(linkItems.size()));

                // Notify fragments
                pagerAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // Getters for fragments to access data
    public List<String> getMediaUrls() { return mediaUrls; }
    public List<Message> getFileMessages() { return fileMessages; }
    public List<String[]> getLinkItems() { return linkItems; }
}
