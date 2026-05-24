package com.example.se114_callingsystem.features.post;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.Post;
import com.example.se114_callingsystem.core.model.User;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PostListAdapter extends RecyclerView.Adapter<PostListAdapter.PostViewHolder> {

    private List<Post> postList;
    private Context context;
    private OnPostInteractionListener listener;
    private String currentUserId;
    private FirebaseFirestore db;
    private List<com.example.se114_callingsystem.core.model.ServerMember> serverMembers = new ArrayList<>();
    private String serverColor = "#6C63FF";

    public interface OnPostInteractionListener {
        void onLikeClick(Post post, String emoji);
        void onLikeLongClick(Post post, View anchorView);
        void onCommentClick(Post post);
        void onShareClick(Post post);
        void onMediaClick(String url, String type);
        void onOptionsClick(Post post, View anchorView);
    }

    public PostListAdapter(Context context, List<Post> postList, String serverColor, OnPostInteractionListener listener) {
        this.context = context;
        this.postList = postList;
        this.serverColor = serverColor != null ? serverColor : "#6C63FF";
        this.listener = listener;
        this.currentUserId = FirebaseAuth.getInstance().getUid();
        this.db = FirebaseFirestore.getInstance();
    }

    public void setServerMembers(List<com.example.se114_callingsystem.core.model.ServerMember> members) {
        this.serverMembers = members != null ? members : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = postList.get(position);

        // Render content
        if (post.getContent() == null || post.getContent().isEmpty()) {
            holder.tvContent.setVisibility(View.GONE);
        } else {
            holder.tvContent.setVisibility(View.VISIBLE);
            holder.tvContent.setText(post.getContent());
            highlightMentionsInSpannable(holder.tvContent, serverColor, serverMembers);
        }

        // Render time
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        holder.tvTimestamp.setText(sdf.format(new Date(post.getCreatedAt())));

        // Fetch author info
        db.collection("users").document(post.getAuthorId()).get().addOnSuccessListener(doc -> {
            User user = doc.toObject(User.class);
            if (user != null) {
                holder.tvAuthorName.setText(user.getUsername());
                if (user.getProfilePic() != null && !user.getProfilePic().isEmpty()) {
                    Glide.with(context).load(user.getProfilePic()).into(holder.ivAuthorAvatar);
                } else {
                    holder.ivAuthorAvatar.setImageResource(R.drawable.ic_user);
                }
            }
        });

        // Handle Media
        if (post.getMediaUrls() != null && !post.getMediaUrls().isEmpty()) {
            holder.mediaContainer.setVisibility(View.VISIBLE);
            String url = post.getMediaUrls().get(0);
            String type = post.getMediaTypes() != null && !post.getMediaTypes().isEmpty() ? post.getMediaTypes().get(0) : "image";

            holder.ivPostImage.setVisibility(View.VISIBLE);
            Glide.with(context).load(url).into(holder.ivPostImage);

            if ("video".equals(type)) {
                holder.ivPlayVideo.setVisibility(View.VISIBLE);
            } else {
                holder.ivPlayVideo.setVisibility(View.GONE);
            }

            holder.mediaContainer.setOnClickListener(v -> listener.onMediaClick(url, type));
        } else {
            holder.mediaContainer.setVisibility(View.GONE);
        }

        // Handle Like Status
        boolean isLikedByMe = post.getReactions() != null && post.getReactions().containsKey(currentUserId);
        if (isLikedByMe) {
            String myEmoji = post.getReactions().get(currentUserId);
            holder.tvLikeIcon.setText(myEmoji != null ? myEmoji : "â¤ï¸");
            holder.tvLikeCount.setTextColor(android.graphics.Color.parseColor("#ED4245"));
        } else {
            holder.tvLikeIcon.setText("ðŸ¤");
            holder.tvLikeCount.setTextColor(context.getResources().getColor(R.color.text_secondary));
        }
        int likeCount = post.getReactions() != null ? post.getReactions().size() : 0;
        holder.tvLikeCount.setText(likeCount > 0 ? String.valueOf(likeCount) : "ThÃ­ch");

        // Handle Comments count
        holder.tvCommentCount.setText(post.getCommentCount() > 0 ? String.valueOf(post.getCommentCount()) : "BÃ¬nh luáº­n");

        // Button clicks
        holder.btnLike.setOnClickListener(v -> listener.onLikeClick(post, "â¤ï¸"));
        holder.btnLike.setOnLongClickListener(v -> {
            listener.onLikeLongClick(post, holder.btnLike);
            return true;
        });
        
        holder.btnComment.setOnClickListener(v -> listener.onCommentClick(post));
        holder.btnShare.setOnClickListener(v -> listener.onShareClick(post));

        // Options Button
        if (post.getAuthorId().equals(currentUserId)) {
            holder.btnOptions.setVisibility(View.VISIBLE);
            holder.btnOptions.setOnClickListener(v -> listener.onOptionsClick(post, holder.btnOptions));
        } else {
            holder.btnOptions.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAuthorAvatar, ivPostImage, ivPlayVideo, btnOptions;
        TextView tvAuthorName, tvTimestamp, tvContent, tvLikeIcon, tvLikeCount, tvCommentCount;
        MaterialCardView mediaContainer;
        LinearLayout btnLike, btnComment, btnShare;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAuthorAvatar = itemView.findViewById(R.id.ivAuthorAvatar);
            btnOptions = itemView.findViewById(R.id.btnOptions);
            tvAuthorName = itemView.findViewById(R.id.tvAuthorName);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvContent = itemView.findViewById(R.id.tvContent);
            mediaContainer = itemView.findViewById(R.id.mediaContainer);
            ivPostImage = itemView.findViewById(R.id.ivPostImage);
            ivPlayVideo = itemView.findViewById(R.id.ivPlayVideo);
            btnLike = itemView.findViewById(R.id.btnLike);
            btnComment = itemView.findViewById(R.id.btnComment);
            btnShare = itemView.findViewById(R.id.btnShare);
            tvLikeIcon = itemView.findViewById(R.id.tvLikeIcon);
            tvLikeCount = itemView.findViewById(R.id.tvLikeCount);
            tvCommentCount = itemView.findViewById(R.id.tvCommentCount);
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

        List<MemberNameMapping> nameMappings = new ArrayList<>();
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
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("@[A-Za-z0-9_Ã€ÃÃ‚ÃƒÃˆÃ‰ÃŠÃŒÃÃ’Ã“Ã”Ã•Ã™ÃšÃÃ Ã¡Ã¢Ã£Ã¨Ã©ÃªÃ¬Ã­Ã²Ã³Ã´ÃµÃ¹ÃºÃ½Ä‚ÄƒÄÄ‘Ä¨Ä©Å¨Å©Æ Æ¡Æ¯Æ°áº -á»¹]+");
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


