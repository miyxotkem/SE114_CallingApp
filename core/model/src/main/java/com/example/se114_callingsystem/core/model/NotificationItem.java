package com.example.se114_callingsystem.core.model;

import com.google.firebase.firestore.PropertyName;

public class NotificationItem {
    private String notificationId;
    private String title;
    private String content;
    private String type; // "dm", "mention", "friend_request", "friend_accepted"
    private String senderId;
    private String senderName;
    private String targetId; // CHAT_ID or other target
    private long timestamp;
    @PropertyName("isRead")
    private boolean isRead;

    public NotificationItem() {
    }

    public NotificationItem(String notificationId, String title, String content, String type, 
                            String senderId, String senderName, String targetId, long timestamp, boolean isRead) {
        this.notificationId = notificationId;
        this.title = title;
        this.content = content;
        this.type = type;
        this.senderId = senderId;
        this.senderName = senderName;
        this.targetId = targetId;
        this.timestamp = timestamp;
        this.isRead = isRead;
    }

    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @PropertyName("isRead")
    public boolean isRead() { return isRead; }
    @PropertyName("isRead")
    public void setRead(boolean read) { isRead = read; }
}
