package com.example.se114_callingsystem.post;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.model.Comment;
import com.example.se114_callingsystem.util.ThemeHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PostCommentActivity extends AppCompatActivity {

    private String postId;
    private String postAuthorId;
    private String serverId;
    private RecyclerView rvComments;
    private CommentAdapter commentAdapter;
    private List<Comment> commentList = new ArrayList<>();
    private FirebaseFirestore db;
    private EditText etComment;

    // Mentions
    private com.google.android.material.card.MaterialCardView cardMentionSuggestions;
    private androidx.recyclerview.widget.RecyclerView rvMentionSuggestions;
    private com.example.se114_callingsystem.util.MentionAdapter mentionAdapter;
    private List<com.example.se114_callingsystem.model.ServerMember> serverMembers = new ArrayList<>();
    private List<com.example.se114_callingsystem.model.ServerMember> filteredMembers = new ArrayList<>();
    private int mentionStartIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_comment);

        postId = getIntent().getStringExtra("POST_ID");
        postAuthorId = getIntent().getStringExtra("POST_AUTHOR_ID");
        serverId = getIntent().getStringExtra("SERVER_ID");
        db = FirebaseFirestore.getInstance();

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        etComment = findViewById(R.id.etComment);
        ImageView btnSendComment = findViewById(R.id.btnSendComment);
        btnSendComment.setOnClickListener(v -> postComment());

        setupMentionSuggestions();
        fetchServerMembers();

        rvComments = findViewById(R.id.rvComments);
        commentAdapter = new CommentAdapter(this, commentList, postAuthorId);
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        rvComments.setAdapter(commentAdapter);

        loadComments();
    }

    private void loadComments() {
        db.collection("Posts").document(postId).collection("comments")
          .addSnapshotListener((snapshots, error) -> {
              if (error != null) return;
              if (snapshots != null) {
                  commentList.clear();
                  for (DocumentSnapshot doc : snapshots) {
                      Comment c = doc.toObject(Comment.class);
                      if (c != null) { c.setId(doc.getId()); commentList.add(c); }
                  }
                  Collections.sort(commentList, (a, b) -> Long.compare(a.getCreatedAt(), b.getCreatedAt()));
                  commentAdapter.notifyDataSetChanged();
                  if (!commentList.isEmpty()) {
                      rvComments.scrollToPosition(commentList.size() - 1);
                  }
              }
          });
    }

    private void fetchServerMembers() {
        if (serverId != null) {
            db.collection("servers").document(serverId).collection("members").get().addOnSuccessListener(snapshots -> {
                serverMembers.clear();
                for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots) {
                    com.example.se114_callingsystem.model.ServerMember member = doc.toObject(com.example.se114_callingsystem.model.ServerMember.class);
                    if (member != null) serverMembers.add(member);
                }
            });
        }
    }

    private void setupMentionSuggestions() {
        cardMentionSuggestions = findViewById(R.id.cardMentionSuggestions);
        rvMentionSuggestions = findViewById(R.id.rvMentionSuggestions);
        
        mentionAdapter = new com.example.se114_callingsystem.util.MentionAdapter(filteredMembers, member -> insertMention(member));
        rvMentionSuggestions.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        rvMentionSuggestions.setAdapter(mentionAdapter);

        etComment.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                int cursor = etComment.getSelectionStart();
                if (cursor < 0) { hideMentionSuggestions(); return; }
                String text = s.toString();
                int atIndex = text.lastIndexOf("@", cursor - 1);
                if (atIndex != -1 && (atIndex == 0 || text.charAt(atIndex - 1) == ' ' || text.charAt(atIndex - 1) == '\n')) {
                    mentionStartIndex = atIndex;
                    String query = text.substring(atIndex + 1, cursor);
                    if (query.contains(" ") || query.contains("\n")) {
                        hideMentionSuggestions();
                    } else {
                        showMentionSuggestions(query);
                    }
                } else {
                    hideMentionSuggestions();
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void showMentionSuggestions(String query) {
        filteredMembers.clear();
        String lowercaseQuery = query.toLowerCase();
        for (com.example.se114_callingsystem.model.ServerMember member : serverMembers) {
            String name = member.getNickname() != null ? member.getNickname() : member.getUserName();
            if (name != null && name.toLowerCase().contains(lowercaseQuery)) {
                filteredMembers.add(member);
            }
        }
        if (filteredMembers.isEmpty()) {
            hideMentionSuggestions();
        } else {
            mentionAdapter.setList(filteredMembers);
            if (cardMentionSuggestions != null) cardMentionSuggestions.setVisibility(android.view.View.VISIBLE);
        }
    }

    private void hideMentionSuggestions() {
        mentionStartIndex = -1;
        if (cardMentionSuggestions != null) cardMentionSuggestions.setVisibility(android.view.View.GONE);
    }

    private void insertMention(com.example.se114_callingsystem.model.ServerMember member) {
        String nickname = member.getNickname() != null ? member.getNickname() : member.getUserName();
        String mention = "@" + nickname + " ";
        String text = etComment.getText().toString();
        String newText = text.substring(0, mentionStartIndex) + mention;
        etComment.setText(newText);
        etComment.setSelection(newText.length());
        hideMentionSuggestions();
    }

    private void postComment() {
        String text = etComment.getText().toString().trim();
        if (text.isEmpty()) return;

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        Comment c = new Comment(null, postId, uid, text, System.currentTimeMillis());
        
        db.collection("Posts").document(postId).collection("comments").add(c).addOnSuccessListener(doc -> {
            c.setId(doc.getId());
            db.collection("Posts").document(postId).collection("comments").document(doc.getId()).set(c);
            etComment.setText("");
            
            // Tăng số đếm comment trong Post
            db.collection("Posts").document(postId).get().addOnSuccessListener(postDoc -> {
                if (postDoc.exists()) {
                    Long currentCount = postDoc.getLong("commentCount");
                    if (currentCount == null) currentCount = 0L;
                    db.collection("Posts").document(postId).update("commentCount", currentCount + 1);
                }
            });
        });
    }
}
