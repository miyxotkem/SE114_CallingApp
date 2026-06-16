package com.example.se114_callingsystem.features.chat.ui;

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
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.Firebase;
import com.example.se114_callingsystem.core.model.Message;
import com.example.se114_callingsystem.core.model.ServerMember;
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

@dagger.hilt.android.AndroidEntryPoint
public class SearchInChatFragment extends Fragment {

    @javax.inject.Inject
    public com.example.se114_callingsystem.features.chat.data.MessageDao messageDao;

    private String chatId, chatName, serverId, serverColor;
    private EditText edtSearch;
    private ImageView btnBack, btnClear;
    private RecyclerView rvSearchResults;
    private TextView tvEmptyState;
    private View layoutEmptyState;

    private List<Message> allMessages = new ArrayList<>();
    private List<Message> searchResults = new ArrayList<>();
    private List<ServerMember> serverMembers = new ArrayList<>();

    private SearchResultsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            chatId = getArguments().getString("CHAT_ID");
            chatName = getArguments().getString("CHAT_NAME");
            serverId = getArguments().getString("SERVER_ID");
            serverColor = getArguments().getString("SERVER_COLOR");
        }
        if (serverColor == null) serverColor = "#6C63FF";

        initViews(view);
        setupRecyclerView();
        loadServerMembers();
        loadMessages();
    }

    private void initViews(View view) {
        btnBack = view.findViewById(R.id.btnBack);
        btnClear = view.findViewById(R.id.btnClear);
        edtSearch = view.findViewById(R.id.edtSearch);
        rvSearchResults = view.findViewById(R.id.rvSearchResults);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);

        btnBack.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());
        
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
        com.google.android.material.card.MaterialCardView cardSearchBar = view.findViewById(R.id.cardSearchBar);
        if (cardSearchBar != null) {
            try {
                cardSearchBar.setStrokeColor(Color.parseColor(serverColor));
            } catch (Exception e) {}
        }
    }

    private void setupRecyclerView() {
        adapter = new SearchResultsAdapter();
        rvSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSearchResults.setAdapter(adapter);
    }

    private void loadServerMembers() {
        if (serverId != null && !serverId.isEmpty()) {
            FirebaseFirestore.getInstance().collection("servers").document(serverId).collection("members")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (getView() == null) return;
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
        
        // 1. Nạp từ Room DB trước để có dữ liệu tìm kiếm lập tức (offline-first)
        new Thread(() -> {
            if (messageDao == null) return;
            androidx.lifecycle.LiveData<List<com.example.se114_callingsystem.features.chat.data.CachedMessage>> liveData = messageDao.getMessagesForGroup(chatId);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    liveData.observe(getViewLifecycleOwner(), cachedList -> {
                        if (cachedList != null) {
                            allMessages.clear();
                            for (com.example.se114_callingsystem.features.chat.data.CachedMessage cm : cachedList) {
                                allMessages.add(cm.toMessage());
                            }
                            performSearch(edtSearch.getText().toString());
                        }
                    });
                });
            }
        }).start();

        // 2. Nạp thêm từ Firebase (nếu online) để cập nhật Room DB
        com.google.firebase.database.Query chatRef = Firebase.getDatabase().getReference("chats").child(chatId).limitToLast(1000);
        chatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Message> list = new ArrayList<>();
                for (DataSnapshot data : snapshot.getChildren()) {
                    Message msg = data.getValue(Message.class);
                    if (msg != null) {
                        msg.setMessageId(data.getKey());
                        list.add(msg);
                    }
                }
                if (!list.isEmpty()) {
                    new Thread(() -> {
                        if (messageDao == null) return;
                        List<com.example.se114_callingsystem.features.chat.data.CachedMessage> cachedList = new ArrayList<>();
                        for (Message m : list) {
                            cachedList.add(new com.example.se114_callingsystem.features.chat.data.CachedMessage(m));
                        }
                        messageDao.insertOrUpdateAll(cachedList);
                    }).start();
                }
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
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
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
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
        }
    }

    private class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.ViewHolder> {
        private final java.util.Map<String, com.example.se114_callingsystem.core.model.User> userCache = new java.util.HashMap<>();

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_search_result, parent, false);
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
            } else if (userCache.containsKey(uid)) {
                com.example.se114_callingsystem.core.model.User cachedUser = userCache.get(uid);
                if (cachedUser != null) {
                    holder.tvSenderName.setText(cachedUser.getUsername());
                } else {
                    holder.tvSenderName.setText("Loading...");
                }
            } else {
                holder.tvSenderName.setText("Loading...");
                fetchUserAndCache(uid);
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
            holder.ivAvatar.setImageResource(R.drawable.ic_user);
            try {
                holder.ivAvatar.setColorFilter(Color.parseColor(serverColor));
            } catch (Exception e) {
                holder.ivAvatar.setColorFilter(Color.parseColor("#FF007F"));
            }

            if (userCache.containsKey(uid)) {
                com.example.se114_callingsystem.core.model.User cachedUser = userCache.get(uid);
                if (cachedUser != null && cachedUser.getProfilePic() != null && !cachedUser.getProfilePic().isEmpty() && getContext() != null) {
                    holder.ivAvatar.setColorFilter(null);
                    Glide.with(holder.itemView.getContext())
                        .load(cachedUser.getProfilePic())
                        .placeholder(R.drawable.ic_user)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(holder.ivAvatar);
                }
            } else {
                fetchUserAndCache(uid);
            }

            holder.itemView.setOnClickListener(v -> {
                NavController navController = Navigation.findNavController(requireView());
                if (navController.getPreviousBackStackEntry() != null) {
                    navController.getPreviousBackStackEntry().getSavedStateHandle().set("GOTO_MESSAGE_ID", msg.getMessageId());
                }
                navController.popBackStack();
            });
        }

        private void fetchUserAndCache(String uid) {
            if (uid == null || userCache.containsKey(uid)) return;
            userCache.put(uid, null); // Placeholder to prevent duplicate calls
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        com.example.se114_callingsystem.core.model.User user = doc.toObject(com.example.se114_callingsystem.core.model.User.class);
                        if (user != null) {
                            userCache.put(uid, user);
                            notifyDataSetChanged();
                        }
                    }
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
