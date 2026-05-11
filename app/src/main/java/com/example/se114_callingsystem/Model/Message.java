package com.example.se114_callingsystem.Model;

public class Message {
    private String messageId;
    private String senderId;
    private String receiverId;
    private String content;
    private long timestamp;
    private String type; // 0: Text, 1: Image, 2: File
    private String fileUrl;

    // MỚI: 3 trường cho React, Reply, Delete
    private boolean isDeleted;
    private String reactionEmoji;
    private String repliedToContent;
    private String repliedToType; // "image", "file", or null/"Message" for text

    // Constructor trống để Firebase mapping
    public Message() {
        this.isDeleted = false;
        this.reactionEmoji = "";
        this.repliedToContent = "";
        this.repliedToType = "";
    }

    // Constructor đầy đủ cho Text
    public Message(String senderId, String receiverId, String content, long timestamp) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.content = content;
        this.timestamp = timestamp;
        this.type = "Message";
        this.isDeleted = false;
        this.reactionEmoji = "";
        this.repliedToContent = "";
        this.repliedToType = "";
    }

    // Constructor đầy đủ cho Media
    public Message(String senderId, String receiverId, String content, long timestamp, String type, String fileUrl) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.content = content;
        this.timestamp = timestamp;
        this.type = type;
        this.fileUrl = fileUrl;
        this.isDeleted = false;
        this.reactionEmoji = "";
        this.repliedToContent = "";
        this.repliedToType = "";
    }

    // --- Original Getters & Setters ---
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    // --- New Getters & Setters ---
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
    public String getReactionEmoji() { return reactionEmoji; }
    public void setReactionEmoji(String reactionEmoji) { this.reactionEmoji = reactionEmoji; }
    public String getRepliedToContent() { return repliedToContent; }
    public void setRepliedToContent(String repliedToContent) { this.repliedToContent = repliedToContent; }
    public String getRepliedToType() { return repliedToType; }
    public void setRepliedToType(String repliedToType) { this.repliedToType = repliedToType; }
}