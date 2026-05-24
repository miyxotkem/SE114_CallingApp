package com.example.se114_callingsystem.core.model;

import java.util.HashMap;
import java.util.Map;

public class Comment {
    private String id;
    private String postId;
    private String authorId;
    private String content;
    private long createdAt;
    private Map<String, String> reactions;
    private String parentCommentId;
    private String parentCommentAuthorName;

    public Comment() {
        this.reactions = new HashMap<>();
    }

    public Comment(String id, String postId, String authorId, String content, long createdAt) {
        this.id = id;
        this.postId = postId;
        this.authorId = authorId;
        this.content = content;
        this.createdAt = createdAt;
        this.reactions = new HashMap<>();
        this.parentCommentId = null;
        this.parentCommentAuthorName = null;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public Map<String, String> getReactions() { return reactions; }
    public void setReactions(Map<String, String> reactions) { this.reactions = reactions; }
    public String getParentCommentId() { return parentCommentId; }
    public void setParentCommentId(String parentCommentId) { this.parentCommentId = parentCommentId; }
    public String getParentCommentAuthorName() { return parentCommentAuthorName; }
    public void setParentCommentAuthorName(String parentCommentAuthorName) { this.parentCommentAuthorName = parentCommentAuthorName; }
}

