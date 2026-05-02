package com.example.se114_callingsystem.Model;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class Firebase {
    private static final String DB_URL = "https://calling-app-5374e-default-rtdb.asia-southeast1.firebasedatabase.app/";

    public static DatabaseReference getMessagesRef() {
        return getDatabase().getReference("chats");
    }

    public static DatabaseReference getMessagesRefByRoom(String chatRoomID) {
        return getDatabase().getReference("chats").child(chatRoomID);
    }
    public static FirebaseStorage getStorage() {
        return FirebaseStorage.getInstance();
    }
    public static StorageReference getChatStorageRef(String chatRoomID) {
        return getStorageRef().child("chat_files").child(chatRoomID);
    }

    public static StorageReference getStorageRef() {
        return getStorage().getReference();
    }
    public static FirebaseDatabase getDatabase() {
        return FirebaseDatabase.getInstance(DB_URL);
    }

    public static DatabaseReference getUsersRef() {
        return getDatabase().getReference("users");
    }



}