package com.example.se114_callingsystem;

public class MessageModel {
    private String messageId;
    private String senderId;
    private String receiverId;
    private String content;
    private long timestamp;
    private int type; // 0: Text, 1: Image, 2: File
    public MessageModel() {
    }

    // Constructor đầy đủ để khởi tạo nhanh
    public MessageModel(String senderId, String receiverId, String content, long timestamp) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.content = content;
        this.timestamp = timestamp;
        this.type = 0; // Mặc định là tin nhắn văn bản
    }

    // Getter và Setter
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

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
}