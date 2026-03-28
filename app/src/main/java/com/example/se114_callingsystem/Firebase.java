package com.example.se114_callingsystem;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class Firebase {
    private static final String DB_URL = "https://calling-app-5374e-default-rtdb.asia-southeast1.firebasedatabase.app/";

    public static FirebaseDatabase getDatabase() {
        return FirebaseDatabase.getInstance(DB_URL);
    }

    public static DatabaseReference getUsersRef() {
        return getDatabase().getReference("users");
    }

    public static DatabaseReference getUsersRefByID(String ID) { return getDatabase().getReference("users").child(ID); }
}