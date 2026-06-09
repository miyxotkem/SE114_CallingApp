package com.example.se114_callingsystem.features.post;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.Comment;
import com.example.se114_callingsystem.core.model.User;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CommentListAdapter extends RecyclerView.Adapter<CommentListAdapter.CommentViewHolder> {

    public interface OnCommentInteractionListener {
        void onReplyClick(Comment comment, String authorName);
        void onDeleteClick(Comment comment);
    }

    private Context context;
    private List<Comment> comments;
    private String postAuthorId;
    private String serverColor;
    private OnCommentInteractionListener listener;
    private FirebaseFirestore db;
    private List<com.example.se114_callingsystem.core.model.ServerMember> serverMembers = new java.util.ArrayList<>();

    public CommentListAdapter(Context context, List<Comment> comments, String postAuthorId, String serverColor, OnCommentInteractionListener listener) {
        this.context = context;
        this.comments = comments;
        this.postAuthorId = postAuthorId;
        this.serverColor = serverColor;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    public void setServerMembers(List<com.example.se114_callingsystem.core.model.ServerMember> members) {
        this.serverMembers = members != null ? members : new java.util.ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_post_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = comments.get(position);
        holder.tvCommentContent.setText(comment.getContent());
        highlightMentionsInSpannable(holder.tvCommentContent, serverColor, serverMembers);

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        holder.tvCommentTime.setText(sdf.format(new Date(comment.getCreatedAt())));

        // Reset author tag visibility
        holder.tvAuthorTag.setVisibility(View.GONE);

        // Reset indentation & reply indicator
        if (comment.getParentCommentId() != null) {
            int indent = (int) (36 * context.getResources().getDisplayMetrics().density);
            holder.itemView.setPadding(indent, holder.itemView.getPaddingTop(), holder.itemView.getPaddingRight(), holder.itemView.getPaddingBottom());
            holder.layoutReplyIndicator.setVisibility(View.VISIBLE);
            holder.tvReplyAuthorName.setText("@" + (comment.getParentCommentAuthorName() != null ? comment.getParentCommentAuthorName() : "người dùng"));
            if (serverColor != null && !serverColor.isEmpty()) {
                try {
                    holder.tvReplyAuthorName.setTextColor(android.graphics.Color.parseColor(serverColor));
                } catch (Exception e) {
                    holder.tvReplyAuthorName.setTextColor(android.graphics.Color.parseColor("#5865F2"));
                }
            } else {
                holder.tvReplyAuthorName.setTextColor(android.graphics.Color.parseColor("#5865F2"));
            }
        } else {
            int normalPadding = (int) (12 * context.getResources().getDisplayMetrics().density);
            holder.itemView.setPadding(normalPadding, holder.itemView.getPaddingTop(), holder.itemView.getPaddingRight(), holder.itemView.getPaddingBottom());
            holder.layoutReplyIndicator.setVisibility(View.GONE);
        }

        // Default click action for Reply (before author name is fetched)
        holder.btnCommentReply.setOnClickListener(v -> {
            if (listener != null) {
                listener.onReplyClick(comment, "Người dùng");
            }
        });

        db.collection("users").document(comment.getAuthorId()).get().addOnSuccessListener(doc -> {
            User user = doc.toObject(User.class);
            if (user != null) {
                String authorName = user.getUsername();
                if (comment.getAuthorId().equals(postAuthorId)) {
                    holder.tvAuthorTag.setVisibility(View.VISIBLE);
                    if (serverColor != null && !serverColor.isEmpty()) {
                        try {
                            int parsedColor = android.graphics.Color.parseColor(serverColor);
                            holder.tvCommentAuthor.setTextColor(parsedColor);
                            holder.tvAuthorTag.setBackgroundTintList(android.content.res.ColorStateList.valueOf(parsedColor));
                        } catch (Exception e) {
                            holder.tvCommentAuthor.setTextColor(android.graphics.Color.parseColor("#5865F2"));
                            holder.tvAuthorTag.setBackgroundTintList(null);
                        }
                    } else {
                        holder.tvCommentAuthor.setTextColor(android.graphics.Color.parseColor("#5865F2"));
                        holder.tvAuthorTag.setBackgroundTintList(null);
                    }
                } else {
                    holder.tvAuthorTag.setVisibility(View.GONE);
                    holder.tvCommentAuthor.setTextColor(context.getResources().getColor(R.color.text_primary));
                }
                holder.tvCommentAuthor.setText(authorName);
                if (user.getProfilePic() != null && !user.getProfilePic().isEmpty()) {
                    Glide.with(context).load(user.getProfilePic()).into(holder.ivCommentAvatar);
                } else {
                    holder.ivCommentAvatar.setImageResource(R.drawable.ic_user);
                }

                // Update click action for Reply with exact authorName
                holder.btnCommentReply.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onReplyClick(comment, authorName);
                    }
                });
            }
        });

        // Set up reactions display
        if (comment.getReactions() != null && !comment.getReactions().isEmpty()) {
            holder.layoutCommentReactions.setVisibility(View.VISIBLE);
            int count = comment.getReactions().size();
            holder.tvReactionCount.setText(String.valueOf(count));

            Set<String> uniqueEmojis = new HashSet<>(comment.getReactions().values());
            StringBuilder sb = new StringBuilder();
            for (String emoji : uniqueEmojis) {
                sb.append(emoji);
            }
            holder.tvReactionEmojis.setText(sb.toString());
        } else {
            holder.layoutCommentReactions.setVisibility(View.GONE);
        }

        // Set up Like button state
        String currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        String myReaction = (comment.getReactions() != null && currentUid != null) ? comment.getReactions().get(currentUid) : null;
        
        if (myReaction != null) {
            holder.btnCommentLike.setText(myReaction + " Thích");
            if (serverColor != null && !serverColor.isEmpty()) {
                try {
                    holder.btnCommentLike.setTextColor(android.graphics.Color.parseColor(serverColor));
                } catch (Exception e) {
                    holder.btnCommentLike.setTextColor(context.getResources().getColor(R.color.accent));
                }
            } else {
                holder.btnCommentLike.setTextColor(context.getResources().getColor(R.color.accent));
            }
        } else {
            holder.btnCommentLike.setText("Thích");
            holder.btnCommentLike.setTextColor(context.getResources().getColor(R.color.text_secondary));
        }

        // Like button click listener (toggle default ❤️ reaction)
        holder.btnCommentLike.setOnClickListener(v -> {
            if (currentUid == null) return;

            if (comment.getReactions() == null) {
                comment.setReactions(new HashMap<>());
            }

            if (comment.getReactions().containsKey(currentUid)) {
                comment.getReactions().remove(currentUid);
            } else {
                comment.getReactions().put(currentUid, "❤️");
            }

            db.collection("Posts").document(comment.getPostId()).collection("comments").document(comment.getId())
                    .update("reactions", comment.getReactions());
        });

        // Like button long click listener (show emoji selection popup)
        holder.btnCommentLike.setOnLongClickListener(v -> {
            showEmojiPicker(v, comment);
            return true;
        });

        // Item long-click listener for Delete & Copy
        holder.itemView.setOnLongClickListener(v -> {
            boolean canDelete = currentUid != null && (currentUid.equals(comment.getAuthorId()) || currentUid.equals(postAuthorId));
            
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
            List<String> options = new java.util.ArrayList<>();
            options.add("Sao chép văn bản");
            if (canDelete) {
                options.add("Xóa bình luận");
            }
            
            builder.setItems(options.toArray(new String[0]), (dialog, which) -> {
                String selectedOption = options.get(which);
                if (selectedOption.equals("Sao chép văn bản")) {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Comment", comment.getContent());
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        android.widget.Toast.makeText(context, "Đã sao chép bình luận", android.widget.Toast.LENGTH_SHORT).show();
                    }
                } else if (selectedOption.equals("Xóa bình luận")) {
                    if (listener != null) {
                        listener.onDeleteClick(comment);
                    }
                }
            });
            builder.show();
            return true;
        });
    }

    private void showEmojiPicker(View anchor, Comment comment) {
        String currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (currentUid == null) return;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        int padding = (int) (8 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(context.getResources().getColor(R.color.reaction_bg));
        gd.setCornerRadius(20 * context.getResources().getDisplayMetrics().density);
        gd.setStroke(1, context.getResources().getColor(R.color.reaction_stroke));
        layout.setBackground(gd);

        String[] emojis = {"👍", "❤️", "😂", "😮", "😢", "😡"};
        PopupWindow popup = new PopupWindow(
                layout,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popup.setElevation(10f);

        for (String emoji : emojis) {
            TextView tvEmoji = new TextView(context);
            tvEmoji.setText(emoji);
            tvEmoji.setTextSize(20);
            int emojiPadding = (int) (8 * context.getResources().getDisplayMetrics().density);
            tvEmoji.setPadding(emojiPadding, emojiPadding, emojiPadding, emojiPadding);
            tvEmoji.setFocusable(true);
            tvEmoji.setClickable(true);
            
            TypedValue outValue = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
            tvEmoji.setBackgroundResource(outValue.resourceId);

            tvEmoji.setOnClickListener(v -> {
                if (comment.getReactions() == null) {
                    comment.setReactions(new HashMap<>());
                }
                comment.getReactions().put(currentUid, emoji);
                db.collection("Posts").document(comment.getPostId()).collection("comments").document(comment.getId())
                        .update("reactions", comment.getReactions());
                popup.dismiss();
            });
            layout.addView(tvEmoji);
        }

        anchor.post(() -> {
            int[] location = new int[2];
            anchor.getLocationOnScreen(location);
            layout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int popupWidth = layout.getMeasuredWidth();
            int popupHeight = layout.getMeasuredHeight();

            int x = location[0] + (anchor.getWidth() - popupWidth) / 2;
            int y = location[1] - popupHeight - (int)(8 * context.getResources().getDisplayMetrics().density);

            popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
        });
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    public static class CommentViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCommentAvatar;
        TextView tvCommentAuthor, tvCommentContent, tvCommentTime;
        TextView tvAuthorTag, btnCommentLike;
        LinearLayout layoutCommentReactions;
        TextView tvReactionEmojis, tvReactionCount;
        LinearLayout layoutReplyIndicator;
        TextView tvReplyAuthorName, btnCommentReply;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCommentAvatar = itemView.findViewById(R.id.ivCommentAvatar);
            tvCommentAuthor = itemView.findViewById(R.id.tvCommentAuthor);
            tvCommentContent = itemView.findViewById(R.id.tvCommentContent);
            tvCommentTime = itemView.findViewById(R.id.tvCommentTime);
            tvAuthorTag = itemView.findViewById(R.id.tvAuthorTag);
            btnCommentLike = itemView.findViewById(R.id.btnCommentLike);
            layoutCommentReactions = itemView.findViewById(R.id.layoutCommentReactions);
            tvReactionEmojis = itemView.findViewById(R.id.tvReactionEmojis);
            tvReactionCount = itemView.findViewById(R.id.tvReactionCount);
            layoutReplyIndicator = itemView.findViewById(R.id.layoutReplyIndicator);
            tvReplyAuthorName = itemView.findViewById(R.id.tvReplyAuthorName);
            btnCommentReply = itemView.findViewById(R.id.btnCommentReply);
        }
    }

    private static void highlightMentionsInSpannable(TextView textView, String serverColorStr, List<com.example.se114_callingsystem.core.model.ServerMember> serverMembers) {
        if (textView == null) return;
        CharSequence text = textView.getText();
        if (text == null) return;
        android.text.SpannableString spannable = new android.text.SpannableString(text);
        String textStr = spannable.toString();

        int highlightColor;
        try {
            highlightColor = android.graphics.Color.parseColor(serverColorStr);
        } catch (Exception e) {
            highlightColor = android.graphics.Color.parseColor("#FF007F");
        }
        final int finalHighlightColor = highlightColor;

        class MemberNameMapping {
            final String name;
            final String userId;
            MemberNameMapping(String name, String userId) {
                this.name = name;
                this.userId = userId;
            }
        }

        List<MemberNameMapping> nameMappings = new java.util.ArrayList<>();
        if (serverMembers != null) {
            for (com.example.se114_callingsystem.core.model.ServerMember m : serverMembers) {
                if (m.getUserId() == null) continue;
                if (m.getNickname() != null && !m.getNickname().trim().isEmpty()) {
                    nameMappings.add(new MemberNameMapping(m.getNickname(), m.getUserId()));
                }
                if (m.getUserName() != null && !m.getUserName().trim().isEmpty()) {
                    nameMappings.add(new MemberNameMapping(m.getUserName(), m.getUserId()));
                }
            }
        }

        // Sort by name length descending to avoid partial matches
        java.util.Collections.sort(nameMappings, (m1, m2) -> Integer.compare(m2.name.length(), m1.name.length()));

        boolean[] highlighted = new boolean[textStr.length()];

        for (MemberNameMapping mapping : nameMappings) {
            String mentionTag = "@" + mapping.name;
            int index = textStr.indexOf(mentionTag);
            while (index >= 0) {
                int end = index + mentionTag.length();
                boolean alreadyUsed = false;
                for (int i = index; i < end; i++) {
                    if (highlighted[i]) { alreadyUsed = true; break; }
                }

                if (!alreadyUsed) {
                    for (int i = index; i < end; i++) highlighted[i] = true;

                    final String targetUserId = mapping.userId;
                    spannable.setSpan(new android.text.style.ClickableSpan() {
                        @Override
                        public void onClick(@NonNull View widget) {
                            android.os.Bundle bundle = new android.os.Bundle();
                            bundle.putString("USER_ID", targetUserId);
                            androidx.navigation.Navigation.findNavController(widget).navigate(R.id.nav_profile, bundle);
                        }
                        @Override
                        public void updateDrawState(@NonNull android.text.TextPaint ds) {
                            super.updateDrawState(ds);
                            ds.setColor(finalHighlightColor);
                            ds.setUnderlineText(false);
                        }
                    }, index, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                    spannable.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), index, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                index = textStr.indexOf(mentionTag, index + 1);
            }
        }

        // Regex fallback
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("@[A-Za-z0-9_ÀÁÂÃÈÉÊÌÍÒÓÔÕÙÚÝàáâãèéêìíòóôõùúýĂăĐđĨĩŨũƠơƯưẠ-ỹ]+");
        java.util.regex.Matcher matcher = pattern.matcher(textStr);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            boolean alreadyUsed = false;
            for (int i = start; i < end; i++) {
                if (highlighted[i]) { alreadyUsed = true; break; }
            }

            if (!alreadyUsed) {
                for (int i = start; i < end; i++) highlighted[i] = true;
                spannable.setSpan(new android.text.style.ForegroundColorSpan(highlightColor), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        textView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        textView.setHighlightColor(android.graphics.Color.TRANSPARENT);
        textView.setText(spannable);
    }
}


