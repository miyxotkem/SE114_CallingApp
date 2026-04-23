package com.example.se114_callingsystem;

public class Participant {
    public int uid;
    public String name;
    public boolean isMuted;
    public boolean isVideoOff;
    public boolean isSpeaking; // <-- ADD THIS NEW VARIABLE

    public Participant(int uid, String name) {
        this.uid = uid;
        this.name = name;
        this.isMuted = false;
        this.isVideoOff = false;
        this.isSpeaking = false; // Default to false
    }
}