package com.example.se114_callingsystem.features.chat.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.example.se114_callingsystem.core.model.Message;

@Entity(tableName = "cached_messages")
public class CachedMessage {
    @PrimaryKey
    @NonNull
    private String messageId = "";
    private String senderId;
    private String receiverId;
    private String content;
    private long timestamp;
    private String type;
    private String fileUrl;
    private boolean isDeleted;
    private String reactionEmoji;
    private String repliedToContent;
    private String repliedToType;
    private String repliedToMessageId;
    private boolean pinned;
    private long reminderTime;

    public CachedMessage() {
    }

    public CachedMessage(Message m) {
        this.messageId = m.getMessageId() != null ? m.getMessageId() : "";
        this.senderId = m.getSenderId();
        this.receiverId = m.getReceiverId();
        this.content = m.getContent();
        this.timestamp = m.getTimestamp();
        this.type = m.getType();
        this.fileUrl = m.getFileUrl();
        this.isDeleted = m.isDeleted();
        this.reactionEmoji = m.getReactionEmoji();
        this.repliedToContent = m.getRepliedToContent();
        this.repliedToType = m.getRepliedToType();
        this.repliedToMessageId = m.getRepliedToMessageId();
        this.pinned = m.isPinned();
        this.reminderTime = m.getReminderTime();
    }

    public Message toMessage() {
        Message m = new Message();
        m.setMessageId(this.messageId);
        m.setSenderId(this.senderId);
        m.setReceiverId(this.receiverId);
        m.setContent(this.content);
        m.setTimestamp(this.timestamp);
        m.setType(this.type);
        m.setFileUrl(this.fileUrl);
        m.setDeleted(this.isDeleted);
        m.setReactionEmoji(this.reactionEmoji);
        m.setRepliedToContent(this.repliedToContent);
        m.setRepliedToType(this.repliedToType);
        m.setRepliedToMessageId(this.repliedToMessageId);
        m.setPinned(this.pinned);
        m.setReminderTime(this.reminderTime);
        return m;
    }

    // Getters and Setters
    @NonNull
    public String getMessageId() { return messageId; }
    public void setMessageId(@NonNull String messageId) { this.messageId = messageId; }

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

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public String getReactionEmoji() { return reactionEmoji; }
    public void setReactionEmoji(String reactionEmoji) { this.reactionEmoji = reactionEmoji; }

    public String getRepliedToContent() { return repliedToContent; }
    public void setRepliedToContent(String repliedToContent) { this.repliedToContent = repliedToContent; }

    public String getRepliedToType() { return repliedToType; }
    public void setRepliedToType(String repliedToType) { this.repliedToType = repliedToType; }

    public String getRepliedToMessageId() { return repliedToMessageId; }
    public void setRepliedToMessageId(String repliedToMessageId) { this.repliedToMessageId = repliedToMessageId; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public long getReminderTime() { return reminderTime; }
    public void setReminderTime(long reminderTime) { this.reminderTime = reminderTime; }
}
