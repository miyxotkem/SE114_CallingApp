package com.example.se114_callingsystem;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class Firebase {
    private static final String DB_URL = "https://calling-app-5374e-default-rtdb.asia-southeast1.firebasedatabase.app/";

    public static DatabaseReference getMessagesRef() {
        return getDatabase().getReference("chats");
    }
    public static DatabaseReference getMessagesRefByRoom(String chatRoomID) {
        return getDatabase().getReference("chats").child(chatRoomID);
    }
    public static FirebaseDatabase getDatabase() {
        return FirebaseDatabase.getInstance(DB_URL);
    }

    public static DatabaseReference getUsersRef() {
        return getDatabase().getReference("users");
    }

    public static DatabaseReference getUsersRefByID(String ID) { return getDatabase().getReference("users").child(ID); }
}