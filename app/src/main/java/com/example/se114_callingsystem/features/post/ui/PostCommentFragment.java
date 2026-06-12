package com.example.se114_callingsystem.features.post.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
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
import androidx.lifecycle.ViewModelProvider;
import com.example.se114_callingsystem.features.post.viewmodel.PostViewModel;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.List;

@AndroidEntryPoint
public class PostCommentFragment extends Fragment {

    private String postId;
    private String postAuthorId;
    private String serverId;
    private String serverColor;
    private RecyclerView rvComments;
    private CommentListAdapter CommentListAdapter;
    private final List<Comment> commentList = new ArrayList<>();
    private PostViewModel viewModel;
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
    private final List<com.example.se114_callingsystem.core.model.ServerMember> serverMembers = new ArrayList<>();
    private final List<com.example.se114_callingsystem.core.model.ServerMember> filteredMembers = new ArrayList<>();
    private int mentionStartIndex = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_post_comment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(PostViewModel.class);

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
        setupObservers();

        viewModel.loadServerMembers(serverId);
        viewModel.loadComments(postId);

        try {
            if (serverColor != null && !serverColor.isEmpty()) {
                int color = Color.parseColor(serverColor);
                if (btnSendComment != null) {
                    btnSendComment.setImageTintList(ColorStateList.valueOf(color));
                }
                if (cardMentionSuggestions != null) {
                    cardMentionSuggestions.setStrokeColor(color);
                }
            }
        } catch (Exception e) {
            // fallback
        }

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
    }

    private void setupObservers() {
        viewModel.getComments().observe(getViewLifecycleOwner(), list -> {
            if (bindingNullCheck()) return;
            commentList.clear();
            if (list != null) {
                commentList.addAll(list);
            }
            CommentListAdapter.notifyDataSetChanged();
            if (!commentList.isEmpty()) {
                rvComments.scrollToPosition(commentList.size() - 1);
            }
        });

        viewModel.getServerMembers().observe(getViewLifecycleOwner(), members -> {
            if (bindingNullCheck() || members == null) return;
            serverMembers.clear();
            serverMembers.addAll(members);
            if (CommentListAdapter != null) {
                CommentListAdapter.setServerMembers(serverMembers);
            }
        });

        viewModel.getOperationStatus().observe(getViewLifecycleOwner(), status -> {
            if (status == null || getContext() == null) return;
            if (status.equals("ADD_COMMENT_SUCCESS")) {
                etComment.setText("");
                cancelReplyMode();
            } else if (status.startsWith("ADD_COMMENT_FAILED:")) {
                String error = status.substring("ADD_COMMENT_FAILED:".length());
                Toast.makeText(requireContext(), "Lỗi bình luận: " + error, Toast.LENGTH_SHORT).show();
            } else if (status.equals("DELETE_COMMENT_SUCCESS")) {
                Toast.makeText(requireContext(), "Đã xóa bình luận", Toast.LENGTH_SHORT).show();
            } else if (status.startsWith("DELETE_COMMENT_FAILED:")) {
                String error = status.substring("DELETE_COMMENT_FAILED:".length());
                Toast.makeText(requireContext(), "Lỗi xóa bình luận: " + error, Toast.LENGTH_SHORT).show();
            }
            viewModel.resetStatus();
        });
    }

    private boolean bindingNullCheck() {
        return getView() == null;
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

        viewModel.addComment(postId, text, replyToCommentId, replyToAuthorName);
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
        viewModel.deleteComment(postId, comment);
    }
}
