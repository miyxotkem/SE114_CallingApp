package com.example.se114_callingsystem.Model;

import com.google.firebase.firestore.Exclude;

public class CallChannel {
    private String callId;
    private String callName;
    private String serverId;
    private int orderIndex;

    public CallChannel() {}
    public CallChannel(String callName, String serverId, int orderIndex) {
        this.callName = callName;
        this.serverId = serverId;
        this.orderIndex = orderIndex;
    }

    @Exclude
    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }
    public String getCallName() { return callName; }
    public void setCallName(String callName) { this.callName = callName; }
    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }
    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}