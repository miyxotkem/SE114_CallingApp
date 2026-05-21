package com.example.clonediscordapp.data.model;

import com.google.firebase.database.Exclude;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ChatMessage {
    private String messageId;
    private String senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private Object timestamp;
    private String type; // "Message", "image", "file"
    private String fileUrl;
    private boolean isDeleted;
    private String reactionEmoji;
    private String repliedToContent;
    private String repliedToType;

    // Constructor trống cho Firebase mapping
    public ChatMessage() {
        this.messageId = "";
        this.senderId = "";
        this.senderName = "User";
        this.senderAvatar = "";
        this.content = "";
        this.timestamp = System.currentTimeMillis();
        this.type = "Message";
        this.isDeleted = false;
        this.reactionEmoji = "";
        this.repliedToContent = "";
        this.repliedToType = "";
    }

    // Constructor đầy đủ
    public ChatMessage(String messageId, String senderId, String senderName, String senderAvatar, String content, long timestamp) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderAvatar = senderAvatar;
        this.content = content;
        this.timestamp = timestamp;
        this.type = "Message";
        this.isDeleted = false;
        this.reactionEmoji = "";
        this.repliedToContent = "";
        this.repliedToType = "";
    }

    // Compatibility constructor for MockData
    public ChatMessage(String messageId, User sender, String content, String timestamp) {
        this.messageId = messageId;
        if (sender != null) {
            this.senderId = sender.getId();
            this.senderName = sender.getName();
            this.senderAvatar = sender.getAvatarUrl();
        } else {
            this.senderId = "";
            this.senderName = "User";
            this.senderAvatar = "";
        }
        this.content = content;
        this.timestamp = System.currentTimeMillis();
        this.type = "Message";
        this.isDeleted = false;
        this.reactionEmoji = "";
        this.repliedToContent = "";
        this.repliedToType = "";
    }

    // Getters và Setters cho Firebase
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderAvatar() { return senderAvatar; }
    public void setSenderAvatar(String senderAvatar) { this.senderAvatar = senderAvatar; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Object getTimestamp() { return timestamp; }
    public void setTimestamp(Object timestamp) { this.timestamp = timestamp; }

    @Exclude
    public long getTimestampLong() {
        if (timestamp instanceof Number) {
            return ((Number) timestamp).longValue();
        } else if (timestamp instanceof String) {
            try {
                return Long.parseLong((String) timestamp);
            } catch (NumberFormatException e) {
                return System.currentTimeMillis();
            }
        }
        return System.currentTimeMillis();
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public String getReactionEmoji() { return reactionEmoji; }
    public void setReactionEmoji(String reactionEmoji) { this.reactionEmoji = reactionEmoji; }

    public String getRepliedToContent() { return repliedToContent; }
    public void setRepliedToContent(String repliedToContent) { this.repliedToContent = repliedToContent; }

    public String getRepliedToType() { return repliedToType; }
    public void setRepliedToType(String repliedToType) { this.repliedToType = repliedToType; }

    // --- CÁC PHƯƠNG THỨC TƯƠNG THÍCH VỚI MÃ NGUỒN ADAPTER CŨ ---
    @Exclude
    public String getId() { return messageId; }
    
    @Exclude
    public User getSender() { 
        User mockUser = new User(senderId, senderName, "");
        mockUser.setProfilePic(senderAvatar);
        return mockUser;
    }
    
    @Exclude
    public String getFormattedTimestamp() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US);
            return sdf.format(new Date(getTimestampLong()));
        } catch (Exception e) {
            return "Just now";
        }
    }
}
