package com.example.se114_callingsystem.features.post;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.Post;
import com.example.se114_callingsystem.core.util.ThemeHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.content.Intent;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.auth.FirebaseAuth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.example.se114_callingsystem.core.model.ChatChannel;
import com.example.se114_callingsystem.core.model.Message;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class PostChannelActivity extends AppCompatActivity {

    private String channelId, channelName, serverId, serverColor;
    private RecyclerView rvPosts;
    private PostListAdapter PostListAdapter;
    private List<Post> postList = new ArrayList<>();
    private FirebaseFirestore db;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_channel);

        channelId = getIntent().getStringExtra("CHANNEL_ID");
        channelName = getIntent().getStringExtra("CHANNEL_NAME");
        serverId = getIntent().getStringExtra("SERVER_ID");
        serverColor = getIntent().getStringExtra("SERVER_COLOR");

        TextView tvChannelName = findViewById(R.id.tvChannelName);
        if (channelName != null) {
            tvChannelName.setText("ðŸ“° " + channelName);
        }

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        FloatingActionButton fabCreatePost = findViewById(R.id.fabCreatePost);
        fabCreatePost.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreatePostActivity.class);
            intent.putExtra("CHANNEL_ID", channelId);
            intent.putExtra("SERVER_ID", serverId);
            intent.putExtra("SERVER_COLOR", serverColor);
            startActivity(intent);
        });
        
        try {
            int color = android.graphics.Color.parseColor(serverColor);
            fabCreatePost.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        } catch (Exception e) {}

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();

        setupRecyclerView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPosts();
    }

    private void setupRecyclerView() {
        rvPosts = findViewById(R.id.rvPosts);
        PostListAdapter = new PostListAdapter(this, postList, serverColor, new PostListAdapter.OnPostInteractionListener() {
            @Override
            public void onLikeClick(Post post, String emoji) {
                handleLike(post, emoji);
            }

            @Override
            public void onLikeLongClick(Post post, android.view.View anchorView) {
                showEmojiPicker(post, anchorView);
            }

            @Override
            public void onOptionsClick(Post post, android.view.View anchorView) {
                showPostOptions(post, anchorView);
            }

            @Override
            public void onCommentClick(Post post) {
                Intent intent = new Intent(PostChannelActivity.this, PostCommentActivity.class);
                intent.putExtra("POST_ID", post.getId());
                intent.putExtra("POST_AUTHOR_ID", post.getAuthorId());
                intent.putExtra("SERVER_ID", post.getServerId());
                intent.putExtra("SERVER_COLOR", serverColor);
                startActivity(intent);
            }

            @Override
            public void onShareClick(Post post) {
                showShareBottomSheet(post);
            }

            @Override
            public void onMediaClick(String url, String type) {
                // View media in fullscreen later
            }
        });
        rvPosts.setLayoutManager(new LinearLayoutManager(this));
        rvPosts.setAdapter(PostListAdapter);
    }

    private void loadPosts() {
        db.collection("Posts")
          .whereEqualTo("channelId", channelId)
          .addSnapshotListener((snapshots, error) -> {
              if (error != null) return;
              if (snapshots != null) {
                  postList.clear();
                  for (DocumentSnapshot doc : snapshots) {
                      Post p = doc.toObject(Post.class);
                      if (p != null) { p.setId(doc.getId()); postList.add(p); }
                  }
                  java.util.Collections.sort(postList, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                  PostListAdapter.notifyDataSetChanged();
              }
          });

        db.collection("servers").document(serverId).collection("members").get().addOnSuccessListener(snapshots -> {
            List<ServerMember> members = new ArrayList<>();
            for (DocumentSnapshot doc : snapshots) {
                ServerMember member = doc.toObject(ServerMember.class);
                if (member != null) {
                    members.add(member);
                }
            }
            PostListAdapter.setServerMembers(members);
        });
    }

    private void handleLike(Post post, String emoji) {
        if (currentUserId == null) return;
        Map<String, String> reactions = post.getReactions();
        if (reactions == null) reactions = new HashMap<>();

        if (reactions.containsKey(currentUserId) && reactions.get(currentUserId).equals(emoji)) {
            // Un-react if clicking the same emoji
            reactions.remove(currentUserId);
        } else {
            // React or change reaction
            reactions.put(currentUserId, emoji);
        }

        db.collection("Posts").document(post.getId()).update("reactions", reactions);
    }

    private void showEmojiPicker(Post post, android.view.View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);
        popup.getMenu().add("ðŸ‘");
        popup.getMenu().add("â¤ï¸");
        popup.getMenu().add("ðŸ˜‚");
        popup.getMenu().add("ðŸ˜®");
        popup.getMenu().add("ðŸ˜¢");
        popup.getMenu().add("ðŸ˜¡");
        popup.setOnMenuItemClickListener(item -> {
            handleLike(post, item.getTitle().toString());
            return true;
        });
        popup.show();
    }

    private void showPostOptions(Post post, android.view.View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);
        popup.getMenu().add("Chá»‰nh sá»­a");
        popup.getMenu().add("XÃ³a");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Chá»‰nh sá»­a")) {
                Intent intent = new Intent(this, CreatePostActivity.class);
                intent.putExtra("CHANNEL_ID", channelId);
                intent.putExtra("SERVER_ID", serverId);
                intent.putExtra("SERVER_COLOR", serverColor);
                intent.putExtra("POST_ID", post.getId());
                intent.putExtra("POST_CONTENT", post.getContent());
                startActivity(intent);
            } else if (item.getTitle().equals("XÃ³a")) {
                db.collection("Posts").document(post.getId()).delete();
            }
            return true;
        });
        popup.show();
    }

    private void showShareBottomSheet(Post post) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_post_share, null);
        bottomSheet.setContentView(view);
        
        android.widget.ListView lvChannels = view.findViewById(R.id.lvChannels);
        List<ChatChannel> channelList = new ArrayList<>();
        List<String> channelNames = new ArrayList<>();
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, channelNames);
        lvChannels.setAdapter(adapter);

        db.collection("Channels").whereEqualTo("serverId", serverId).get().addOnSuccessListener(snapshots -> {
            for (DocumentSnapshot doc : snapshots) {
                ChatChannel c = doc.toObject(ChatChannel.class);
                if (c != null) { 
                    c.setChatId(doc.getId()); 
                    channelList.add(c); 
                    channelNames.add("# " + c.getChatName());
                }
            }
            adapter.notifyDataSetChanged();
        });

        lvChannels.setOnItemClickListener((parent, view1, position, id) -> {
            ChatChannel targetChannel = channelList.get(position);
            sharePostToChannel(post, targetChannel);
            bottomSheet.dismiss();
        });
        
        bottomSheet.show();
    }

    private void sharePostToChannel(Post post, ChatChannel channel) {
        Message msg = new Message(currentUserId, channel.getChatId(), post.getId(), System.currentTimeMillis());
        msg.setType("post_share");
        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("chats")
            .child(channel.getChatId()).push().setValue(msg)
            .addOnSuccessListener(a -> {
                Toast.makeText(this, "ÄÃ£ chia sáº» bÃ i viáº¿t vÃ o " + channel.getChatName(), Toast.LENGTH_SHORT).show();
            });
    }
}

