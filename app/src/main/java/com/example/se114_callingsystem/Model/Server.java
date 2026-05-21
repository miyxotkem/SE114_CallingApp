package com.example.se114_callingsystem.Model;

import com.google.firebase.firestore.DocumentId;

public class Server {
    @DocumentId
    private String serverId;
    private String serverName;
    private String ownerId;
    private String iconUrl;
    private String purpose;
    private int orderIndex;
    private String accentColor;
    private java.util.List<String> members;

    public Server() {}
    public Server(String serverName, String ownerId, String iconUrl, String purpose, String accentColor) {
        this.serverName = serverName;
        this.ownerId = ownerId;
        this.iconUrl = iconUrl;
        this.purpose = purpose;
        this.accentColor = accentColor;
        this.members = new java.util.ArrayList<>();
        this.members.add(ownerId); // Add owner as a member initially
    }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }
    public java.util.List<String> getMembers() { return members; }
    public void setMembers(java.util.List<String> members) { this.members = members; }
}