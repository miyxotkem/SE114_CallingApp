package com.example.se114_callingsystem.core.model;

import java.util.List;
import java.util.Map;

public class Post {
    private String id;
    private String channelId;
    private String serverId;
    private String authorId;
    private String content;
    private List<String> mediaUrls;
    private List<String> mediaTypes; // "image", "video", "document"
    private long createdAt;
    private Map<String, String> reactions; // userId -> emoji
    private int commentCount;

    public Post() {}

    public Post(String id, String channelId, String serverId, String authorId, String content, List<String> mediaUrls, List<String> mediaTypes, long createdAt) {
        this.id = id;
        this.channelId = channelId;
        this.serverId = serverId;
        this.authorId = authorId;
        this.content = content;
        this.mediaUrls = mediaUrls;
        this.mediaTypes = mediaTypes;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }
    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<String> getMediaUrls() { return mediaUrls; }
    public void setMediaUrls(List<String> mediaUrls) { this.mediaUrls = mediaUrls; }
    public List<String> getMediaTypes() { return mediaTypes; }
    public void setMediaTypes(List<String> mediaTypes) { this.mediaTypes = mediaTypes; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public Map<String, String> getReactions() { return reactions; }
    public void setReactions(Map<String, String> reactions) { this.reactions = reactions; }
    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }
}

