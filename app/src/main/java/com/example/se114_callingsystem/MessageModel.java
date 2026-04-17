package com.example.se114_callingsystem;

public class MessageModel {
    private String messageId;
    private String senderId;
    private String receiverId;
    private String content;
    private long timestamp;
    private String type; // 0: Text, 1: Image, 2: File
    private String fileUrl; // Link đến file nếu có

    // Constructor trống để Firebase mapping
    public MessageModel() {
    }

    // Constructor đầy đủ để khởi tạo nhanh
    public MessageModel(String senderId, String receiverId, String content, long timestamp) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.content = content;
        this.timestamp = timestamp;
        this.type = "Message"; // Mặc định là tin nhắn văn bản
    }

    public MessageModel(String senderId, String receiverId, String content, long timestamp, String type, String fileUrl) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.content = content;
        this.timestamp = timestamp;
        this.type = type;
        this.fileUrl = fileUrl;
    }

    // Getter và Setter
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }
}