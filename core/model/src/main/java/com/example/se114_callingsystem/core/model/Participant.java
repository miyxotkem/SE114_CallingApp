package com.example.se114_callingsystem.core.model;

public class Participant {
    public int uid;
    public String name;
    public boolean isMuted;
    public boolean isVideoOff;
    public boolean isSpeaking; // <-- ADD THIS NEW VARIABLE
    public boolean isSharingScreen;
    public boolean isMutedLocally;
    public boolean isVideoMutedLocally;

    public Participant(int uid, String name) {
        this.uid = uid;
        this.name = name;
        this.isMuted = false;
        this.isVideoOff = false;
        this.isSpeaking = false; // Default to false
        this.isSharingScreen = false;
        this.isMutedLocally = false;
        this.isVideoMutedLocally = false;
    }
}
