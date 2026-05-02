package com.example.se114_callingsystem.Model;

import com.google.firebase.database.Exclude;

public class ChatChannel {
    private String chatId;
    private String chatName;
    private String serverId;
    private int orderIndex;

    public ChatChannel() {}
    public ChatChannel(String chatName, String serverId, int orderIndex) {
        this.chatName = chatName;
        this.serverId = serverId;
        this.orderIndex = orderIndex;
    }

    @Exclude
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
    public String getChatName() { return chatName; }
    public void setChatName(String chatName) { this.chatName = chatName; }
    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }
    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}