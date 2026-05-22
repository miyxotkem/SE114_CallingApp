package com.example.se114_callingsystem.post;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.model.Comment;
import com.example.se114_callingsystem.model.User;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private Context context;
    private List<Comment> comments;
    private String postAuthorId;
    private FirebaseFirestore db;

    public CommentAdapter(Context context, List<Comment> comments, String postAuthorId) {
        this.context = context;
        this.comments = comments;
        this.postAuthorId = postAuthorId;
        this.db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = comments.get(position);
        holder.tvCommentContent.setText(comment.getContent());

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        holder.tvCommentTime.setText(sdf.format(new Date(comment.getCreatedAt())));

        db.collection("users").document(comment.getAuthorId()).get().addOnSuccessListener(doc -> {
            User user = doc.toObject(User.class);
            if (user != null) {
                String authorName = user.getUsername();
                if (comment.getAuthorId().equals(postAuthorId)) {
                    authorName += " (Tác giả)";
                    holder.tvCommentAuthor.setTextColor(android.graphics.Color.parseColor("#5865F2"));
                } else {
                    holder.tvCommentAuthor.setTextColor(context.getResources().getColor(R.color.text_primary));
                }
                holder.tvCommentAuthor.setText(authorName);
                if (user.getProfilePic() != null && !user.getProfilePic().isEmpty()) {
                    Glide.with(context).load(user.getProfilePic()).into(holder.ivCommentAvatar);
                } else {
                    holder.ivCommentAvatar.setImageResource(R.drawable.icon_user);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    public static class CommentViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCommentAvatar;
        TextView tvCommentAuthor, tvCommentContent, tvCommentTime;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCommentAvatar = itemView.findViewById(R.id.ivCommentAvatar);
            tvCommentAuthor = itemView.findViewById(R.id.tvCommentAuthor);
            tvCommentContent = itemView.findViewById(R.id.tvCommentContent);
            tvCommentTime = itemView.findViewById(R.id.tvCommentTime);
        }
    }
}
