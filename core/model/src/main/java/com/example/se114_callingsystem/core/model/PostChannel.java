package com.example.se114_callingsystem.core.model;

public class PostChannel {
    private String id;
    private String name;
    private String serverId;
    private long createdAt;
    private int orderIndex;

    public PostChannel() {
    }

    public PostChannel(String name, String serverId, int orderIndex) {
        this.name = name;
        this.serverId = serverId;
        this.orderIndex = orderIndex;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}

