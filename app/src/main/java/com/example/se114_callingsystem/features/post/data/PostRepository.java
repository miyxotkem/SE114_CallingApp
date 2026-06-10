package com.example.se114_callingsystem.features.post.data;

import com.example.se114_callingsystem.core.model.ChatChannel;
import com.example.se114_callingsystem.core.model.Comment;
import com.example.se114_callingsystem.core.model.Message;
import com.example.se114_callingsystem.core.model.Post;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

public class PostRepository {

    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;
    private final FirebaseDatabase rtdb;

    @Inject
    public PostRepository(FirebaseAuth mAuth, FirebaseFirestore db, FirebaseDatabase rtdb) {
        this.mAuth = mAuth;
        this.db = db;
        this.rtdb = rtdb;
    }

    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onFailure(Exception e);
    }

    public interface RealtimeCallback<T> {
        void onData(T data);
        void onError(Exception e);
    }

    public String getCurrentUserId() {
        return mAuth.getUid();
    }

    public ListenerRegistration listenToPosts(String channelId, RealtimeCallback<List<Post>> callback) {
        return db.collection("Posts")
                .whereEqualTo("channelId", channelId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snapshots != null) {
                        List<Post> postList = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshots) {
                            Post p = doc.toObject(Post.class);
                            if (p != null) {
                                p.setId(doc.getId());
                                postList.add(p);
                            }
                        }
                        Collections.sort(postList, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                        callback.onData(postList);
                    }
                });
    }

    public void fetchServerMembers(String serverId, RepositoryCallback<List<ServerMember>> callback) {
        if (serverId == null) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        db.collection("servers").document(serverId).collection("members").get()
                .addOnSuccessListener(snapshots -> {
                    List<ServerMember> members = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots) {
                            ServerMember member = doc.toObject(ServerMember.class);
                            if (member != null) {
                                members.add(member);
                            }
                        }
                    }
                    callback.onSuccess(members);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void updateReaction(Post post, String emoji, String userId, RepositoryCallback<Void> callback) {
        Map<String, String> reactions = post.getReactions();
        if (reactions == null) {
            reactions = new HashMap<>();
        }

        if (reactions.containsKey(userId) && reactions.get(userId).equals(emoji)) {
            reactions.remove(userId);
        } else {
            reactions.put(userId, emoji);
        }

        db.collection("Posts").document(post.getId())
                .update("reactions", reactions)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void deletePost(String postId, RepositoryCallback<Void> callback) {
        db.collection("Posts").document(postId)
                .delete()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void fetchChannelsForServer(String serverId, RepositoryCallback<List<ChatChannel>> callback) {
        if (serverId == null) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        db.collection("Channels").whereEqualTo("serverId", serverId).get()
                .addOnSuccessListener(snapshots -> {
                    List<ChatChannel> channels = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots) {
                            ChatChannel c = doc.toObject(ChatChannel.class);
                            if (c != null) {
                                c.setChatId(doc.getId());
                                channels.add(c);
                            }
                        }
                    }
                    callback.onSuccess(channels);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void sharePostToChannel(Post post, ChatChannel channel, String userId, RepositoryCallback<Void> callback) {
        Message msg = new Message(userId, channel.getChatId(), post.getId(), System.currentTimeMillis());
        msg.setType("post_share");
        rtdb.getReference("chats").child(channel.getChatId()).push().setValue(msg)
                .addOnSuccessListener(a -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public ListenerRegistration listenToComments(String postId, RealtimeCallback<List<Comment>> callback) {
        return db.collection("Posts").document(postId).collection("comments")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snapshots != null) {
                        List<Comment> commentList = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshots) {
                            Comment c = doc.toObject(Comment.class);
                            if (c != null) {
                                c.setId(doc.getId());
                                commentList.add(c);
                            }
                        }
                        Collections.sort(commentList, (a, b) -> Long.compare(a.getCreatedAt(), b.getCreatedAt()));
                        callback.onData(commentList);
                    }
                });
    }

    public void addComment(String postId, Comment comment, RepositoryCallback<Void> callback) {
        db.collection("Posts").document(postId).collection("comments").add(comment)
                .addOnSuccessListener(doc -> {
                    comment.setId(doc.getId());
                    db.collection("Posts").document(postId).collection("comments").document(doc.getId()).set(comment)
                            .addOnSuccessListener(aVoid -> {
                                incrementCommentCount(postId);
                                callback.onSuccess(null);
                            })
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    private void incrementCommentCount(String postId) {
        db.collection("Posts").document(postId).get().addOnSuccessListener(postDoc -> {
            if (postDoc.exists()) {
                Long currentCount = postDoc.getLong("commentCount");
                if (currentCount == null) currentCount = 0L;
                db.collection("Posts").document(postId).update("commentCount", currentCount + 1);
            }
        });
    }

    public void deleteComment(String postId, Comment comment, RepositoryCallback<Void> callback) {
        db.collection("Posts").document(postId).collection("comments").document(comment.getId()).delete()
                .addOnSuccessListener(aVoid -> {
                    decrementCommentCount(postId);
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onFailure);
    }

    private void decrementCommentCount(String postId) {
        db.collection("Posts").document(postId).get().addOnSuccessListener(postDoc -> {
            if (postDoc.exists()) {
                Long currentCount = postDoc.getLong("commentCount");
                if (currentCount == null) currentCount = 0L;
                long newCount = Math.max(0L, currentCount - 1);
                db.collection("Posts").document(postId).update("commentCount", newCount);
            }
        });
    }

    public void savePost(Post post, RepositoryCallback<Void> callback) {
        db.collection("Posts").add(post)
                .addOnSuccessListener(doc -> {
                    post.setId(doc.getId());
                    db.collection("Posts").document(doc.getId()).set(post)
                            .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void updatePostContent(String postId, String content, RepositoryCallback<Void> callback) {
        db.collection("Posts").document(postId).update("content", content)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
}
