package com.example.se114_callingsystem.features.post;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.Comment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PostCommentFragment extends Fragment {

    private String postId;
    private String postAuthorId;
    private String serverId;
    private String serverColor;
    private RecyclerView rvComments;
    private CommentListAdapter CommentListAdapter;
    private List<Comment> commentList = new ArrayList<>();
    private FirebaseFirestore db;
    private EditText etComment;

    // Reply state & UI
    private android.widget.LinearLayout layoutReplyHeader;
    private android.widget.TextView tvReplyHeader;
    private android.widget.ImageView btnCancelReply;
    private String replyToCommentId = null;
    private String replyToAuthorName = null;

    // Mentions
    private com.google.android.material.card.MaterialCardView cardMentionSuggestions;
    private androidx.recyclerview.widget.RecyclerView rvMentionSuggestions;
    private com.example.se114_callingsystem.core.util.MentionAdapter mentionAdapter;
    private List<com.example.se114_callingsystem.core.model.ServerMember> serverMembers = new ArrayList<>();
    private List<com.example.se114_callingsystem.core.model.ServerMember> filteredMembers = new ArrayList<>();
    private int mentionStartIndex = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_post_comment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            postId = getArguments().getString("POST_ID");
            postAuthorId = getArguments().getString("POST_AUTHOR_ID");
            serverId = getArguments().getString("SERVER_ID");
            serverColor = getArguments().getString("SERVER_COLOR");
        }

        if (postId == null) {
            if (getContext() != null) {
                Toast.makeText(requireContext(), "Không tìm thấy bài viết", Toast.LENGTH_SHORT).show();
            }
            Navigation.findNavController(view).popBackStack();
            return;
        }
        db = FirebaseFirestore.getInstance();

        ImageView btnBack = view.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());

        etComment = view.findViewById(R.id.etComment);
        ImageView btnSendComment = view.findViewById(R.id.btnSendComment);
        btnSendComment.setOnClickListener(v -> postComment());

        // Bind reply layout elements
        layoutReplyHeader = view.findViewById(R.id.layoutReplyHeader);
        tvReplyHeader = view.findViewById(R.id.tvReplyHeader);
        btnCancelReply = view.findViewById(R.id.btnCancelReply);
        if (btnCancelReply != null) {
            btnCancelReply.setOnClickListener(v -> cancelReplyMode());
        }

        setupMentionSuggestions(view);
        fetchServerMembers();

        rvComments = view.findViewById(R.id.rvComments);
        CommentListAdapter = new CommentListAdapter(requireContext(), commentList, postAuthorId, serverColor, new CommentListAdapter.OnCommentInteractionListener() {
            @Override
            public void onReplyClick(Comment comment, String authorName) {
                setReplyMode(comment, authorName);
            }

            @Override
            public void onDeleteClick(Comment comment) {
                showDeleteConfirmationDialog(comment);
            }
        });
        rvComments.setLayoutManager(new LinearLayoutManager(getContext()));
        rvComments.setAdapter(CommentListAdapter);

        loadComments();
    }

    private void loadComments() {
        db.collection("Posts").document(postId).collection("comments")
          .addSnapshotListener((snapshots, error) -> {
              if (getView() == null) return;
              if (error != null) {
                  if (getContext() != null) {
                      Toast.makeText(requireContext(), "Lỗi tải bình luận: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                  }
                  return;
              }
              if (snapshots != null) {
                  commentList.clear();
                  for (DocumentSnapshot doc : snapshots) {
                      Comment c = doc.toObject(Comment.class);
                      if (c != null) { c.setId(doc.getId()); commentList.add(c); }
                  }
                  Collections.sort(commentList, (a, b) -> Long.compare(a.getCreatedAt(), b.getCreatedAt()));
                  CommentListAdapter.notifyDataSetChanged();
                  if (!commentList.isEmpty()) {
                      rvComments.scrollToPosition(commentList.size() - 1);
                  }
              }
          });
    }

    private void fetchServerMembers() {
        if (serverId != null) {
            db.collection("servers").document(serverId).collection("members").get().addOnSuccessListener(snapshots -> {
                if (getView() == null) return;
                serverMembers.clear();
                for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots) {
                    com.example.se114_callingsystem.core.model.ServerMember member = doc.toObject(com.example.se114_callingsystem.core.model.ServerMember.class);
                    if (member != null) serverMembers.add(member);
                }
                if (CommentListAdapter != null) {
                    CommentListAdapter.setServerMembers(serverMembers);
                }
            });
        }
    }

    private void setupMentionSuggestions(View view) {
        cardMentionSuggestions = view.findViewById(R.id.cardMentionSuggestions);
        rvMentionSuggestions = view.findViewById(R.id.rvMentionSuggestions);
        
        mentionAdapter = new com.example.se114_callingsystem.core.util.MentionAdapter(filteredMembers, member -> insertMention(member));
        rvMentionSuggestions.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
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
        for (com.example.se114_callingsystem.core.model.ServerMember member : serverMembers) {
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

    private void insertMention(com.example.se114_callingsystem.core.model.ServerMember member) {
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
        
        // Attach reply details if present
        if (replyToCommentId != null) {
            c.setParentCommentId(replyToCommentId);
            c.setParentCommentAuthorName(replyToAuthorName);
        }

        db.collection("Posts").document(postId).collection("comments").add(c)
            .addOnSuccessListener(doc -> {
                c.setId(doc.getId());
                db.collection("Posts").document(postId).collection("comments").document(doc.getId()).set(c);
                etComment.setText("");
                cancelReplyMode();
                
                // Tăng số đếm comment trong Post
                db.collection("Posts").document(postId).get().addOnSuccessListener(postDoc -> {
                    if (postDoc.exists()) {
                        Long currentCount = postDoc.getLong("commentCount");
                        if (currentCount == null) currentCount = 0L;
                        db.collection("Posts").document(postId).update("commentCount", currentCount + 1);
                    }
                });
            })
            .addOnFailureListener(e -> {
                if (getContext() != null) {
                    Toast.makeText(requireContext(), "Lỗi đăng bình luận: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void setReplyMode(Comment comment, String authorName) {
        replyToCommentId = comment.getId();
        replyToAuthorName = authorName;
        if (layoutReplyHeader != null && tvReplyHeader != null) {
            tvReplyHeader.setText("Đang trả lời @" + authorName);
            layoutReplyHeader.setVisibility(android.view.View.VISIBLE);
        }
        if (etComment != null) {
            etComment.requestFocus();
            if (getContext() != null) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etComment, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }
    }

    private void cancelReplyMode() {
        replyToCommentId = null;
        replyToAuthorName = null;
        if (layoutReplyHeader != null) {
            layoutReplyHeader.setVisibility(android.view.View.GONE);
        }
    }

    private void showDeleteConfirmationDialog(Comment comment) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Xóa bình luận")
            .setMessage("Bạn có chắc chắn muốn xóa bình luận này không?")
            .setPositiveButton("Xóa", (dialog, which) -> deleteComment(comment))
            .setNegativeButton("Hủy", null)
            .show();
    }

    private void deleteComment(Comment comment) {
        db.collection("Posts").document(postId).collection("comments").document(comment.getId()).delete()
            .addOnSuccessListener(aVoid -> {
                if (getContext() != null) {
                    Toast.makeText(requireContext(), "Đã xóa bình luận", Toast.LENGTH_SHORT).show();
                }
                
                // Giảm số đếm comment trong Post
                db.collection("Posts").document(postId).get().addOnSuccessListener(postDoc -> {
                    if (postDoc.exists()) {
                        Long currentCount = postDoc.getLong("commentCount");
                        if (currentCount == null) currentCount = 0L;
                        long newCount = Math.max(0L, currentCount - 1);
                        db.collection("Posts").document(postId).update("commentCount", newCount);
                    }
                });
            })
            .addOnFailureListener(e -> {
                if (getContext() != null) {
                    Toast.makeText(requireContext(), "Lỗi xóa bình luận: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
    }
}
