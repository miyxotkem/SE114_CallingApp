package com.example.se114_callingsystem.chat;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.model.Firebase;
import com.example.se114_callingsystem.model.Message;
import com.example.se114_callingsystem.model.Server;
import com.example.se114_callingsystem.model.ServerMember;
import com.example.se114_callingsystem.util.ThemeHelper;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SearchInChatActivity extends AppCompatActivity {

    private String chatId, chatName, serverId, serverColor;
    private EditText edtSearch;
    private ImageView btnBack, btnClear;
    private RecyclerView rvSearchResults;
    private TextView tvEmptyState;

    private List<Message> allMessages = new ArrayList<>();
    private List<Message> searchResults = new ArrayList<>();
    private List<ServerMember> serverMembers = new ArrayList<>();

    private SearchResultsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_in_chat);

        chatId = getIntent().getStringExtra("CHAT_ID");
        chatName = getIntent().getStringExtra("CHAT_NAME");
        serverId = getIntent().getStringExtra("SERVER_ID");
        serverColor = getIntent().getStringExtra("SERVER_COLOR");
        if (serverColor == null) serverColor = "#6C63FF";

        initViews();
        setupRecyclerView();
        loadServerMembers();
        loadMessages();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnClear = findViewById(R.id.btnClear);
        edtSearch = findViewById(R.id.edtSearch);
        rvSearchResults = findViewById(R.id.rvSearchResults);
        tvEmptyState = findViewById(R.id.tvEmptyState);

        btnBack.setOnClickListener(v -> finish());
        
        btnClear.setOnClickListener(v -> {
            edtSearch.setText("");
        });

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    btnClear.setVisibility(View.VISIBLE);
                } else {
                    btnClear.setVisibility(View.GONE);
                }
                performSearch(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Apply Server Color theme to Search Bar Border
        com.google.android.material.card.MaterialCardView cardSearchBar = findViewById(R.id.cardSearchBar);
        if (cardSearchBar != null) {
            try {
                cardSearchBar.setStrokeColor(Color.parseColor(serverColor));
            } catch (Exception e) {}
        }
    }

    private void setupRecyclerView() {
        adapter = new SearchResultsAdapter();
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        rvSearchResults.setAdapter(adapter);
    }

    private void loadServerMembers() {
        if (serverId != null && !serverId.isEmpty()) {
            FirebaseFirestore.getInstance().collection("servers").document(serverId).collection("members")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    serverMembers.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        ServerMember m = doc.toObject(ServerMember.class);
                        if (m != null) {
                            serverMembers.add(m);
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
        }
    }

    private void loadMessages() {
        if (chatId == null) return;
        DatabaseReference chatRef = Firebase.getDatabase().getReference("chats").child(chatId);
        chatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allMessages.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    Message msg = data.getValue(Message.class);
                    if (msg != null) {
                        msg.setMessageId(data.getKey());
                        allMessages.add(msg);
                    }
                }
                // Update search once messages are loaded
                performSearch(edtSearch.getText().toString());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            searchResults.clear();
            adapter.notifyDataSetChanged();
            tvEmptyState.setText("Nhập từ khóa để tìm kiếm tin nhắn");
            tvEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        String lowerQuery = query.toLowerCase().trim();
        searchResults.clear();

        for (Message msg : allMessages) {
            if (msg.isDeleted()) continue;

            boolean match = false;
            String type = msg.getType();
            if ("file".equals(type)) {
                String fileUrl = msg.getContent();
                String fileName = "";
                try {
                    fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
                } catch (Exception e) {}
                if (fileName.toLowerCase().contains(lowerQuery)) {
                    match = true;
                }
            } else if ("image".equals(type)) {
                // Ignore matching raw image URLs, search usually matches text
            } else {
                String content = msg.getContent();
                if (content != null && content.toLowerCase().contains(lowerQuery)) {
                    match = true;
                }
            }

            if (match) {
                searchResults.add(msg);
            }
        }

        adapter.notifyDataSetChanged();

        if (searchResults.isEmpty()) {
            tvEmptyState.setText("Không tìm thấy kết quả phù hợp");
            tvEmptyState.setVisibility(View.VISIBLE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
        }
    }

    private class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Message msg = searchResults.get(position);
            String uid = msg.getSenderId();

            // Reset profile picture color filter
            holder.ivAvatar.setColorFilter(null);
            holder.ivAvatar.setTag(uid);
            holder.tvSenderName.setTag(uid);

            // Fetch sender nickname / username
            ServerMember foundMember = null;
            for (ServerMember m : serverMembers) {
                if (m.getUserId() != null && m.getUserId().equals(uid)) {
                    foundMember = m;
                    break;
                }
            }

            if (foundMember != null) {
                String displayName = foundMember.getNickname();
                if (displayName == null || displayName.trim().isEmpty()) {
                    displayName = foundMember.getUserName();
                }
                holder.tvSenderName.setText(displayName);
            } else {
                holder.tvSenderName.setText("Loading...");
                FirebaseFirestore.getInstance().collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists() && uid.equals(holder.tvSenderName.getTag())) {
                            holder.tvSenderName.setText(doc.getString("username"));
                        }
                    });
            }

            // Bind text
            if ("image".equals(msg.getType())) {
                holder.tvMessageBody.setText("📷 [Hình ảnh]");
            } else if ("file".equals(msg.getType())) {
                String fileUrl = msg.getContent();
                String fileName = "Tài liệu đính kèm";
                try {
                    fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
                } catch (Exception e) {}
                holder.tvMessageBody.setText("📎 [Tài liệu] " + fileName);
            } else {
                holder.tvMessageBody.setText(msg.getContent());
            }

            // Format timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            holder.tvTime.setText(sdf.format(new Date(msg.getTimestamp())));

            // Load Avatar
            holder.ivAvatar.setImageResource(R.drawable.icon_user);
            try {
                holder.ivAvatar.setColorFilter(Color.parseColor(serverColor));
            } catch (Exception e) {
                holder.ivAvatar.setColorFilter(Color.parseColor("#FF007F"));
            }

            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && uid.equals(holder.ivAvatar.getTag())) {
                        String profilePic = doc.getString("profilePic");
                        if (profilePic != null && !profilePic.isEmpty()) {
                            holder.ivAvatar.setColorFilter(null);
                            Glide.with(holder.itemView.getContext())
                                .load(profilePic)
                                .placeholder(R.drawable.icon_user)
                                .into(holder.ivAvatar);
                        }
                    }
                });

            holder.itemView.setOnClickListener(v -> {
                Intent data = new Intent();
                data.putExtra("SCROLL_TO_MESSAGE_ID", msg.getMessageId());
                setResult(RESULT_OK, data);
                finish();
            });
        }

        @Override
        public int getItemCount() {
            return searchResults.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView tvSenderName, tvTime, tvMessageBody;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.ivAvatar);
                tvSenderName = itemView.findViewById(R.id.tvSenderName);
                tvTime = itemView.findViewById(R.id.tvTime);
                tvMessageBody = itemView.findViewById(R.id.tvMessageBody);
            }
        }
    }
}
