package com.example.se114_callingsystem.features.home.data;

import com.example.se114_callingsystem.core.model.Firebase;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.example.se114_callingsystem.core.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

public class HomeRepository {

    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;
    private final FirebaseDatabase rtdb;

    @Inject
    public HomeRepository(FirebaseAuth mAuth, FirebaseFirestore db, FirebaseDatabase rtdb) {
        this.mAuth = mAuth;
        this.db = db;
        this.rtdb = rtdb;
    }

    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onFailure(Exception e);
    }

    public interface RealtimeCallback<T> {
        void onData(T data);
        void onError(Exception e);
    }

    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    public ValueEventListener listenToUserStatus(String userId, RealtimeCallback<String> callback) {
        DatabaseReference statusRef = rtdb.getReference("users/" + userId + "/status");
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                callback.onData(status);
            }

            @Override
            public void onCancelled(com.google.firebase.database.DatabaseError error) {
                callback.onError(error.toException());
            }
        };
        statusRef.addValueEventListener(listener);
        return listener;
    }

    public void removeUserStatusListener(String userId, ValueEventListener listener) {
        if (listener != null) {
            rtdb.getReference("users/" + userId + "/status").removeEventListener(listener);
        }
    }

    public void updateStatus(String userId, String status, RepositoryCallback<Void> callback) {
        db.collection("users").document(userId).update("status", status)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public ValueEventListener listenToFriendsList(String userId, RealtimeCallback<List<String>> callback) {
        DatabaseReference friendsRef = Firebase.getUserFriendsRef(userId);
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                List<String> friendUids = new ArrayList<>();
                if (snapshot.exists()) {
                    for (com.google.firebase.database.DataSnapshot snap : snapshot.getChildren()) {
                        if (snap.getKey() != null) {
                            friendUids.add(snap.getKey());
                        }
                    }
                }
                callback.onData(friendUids);
            }

            @Override
            public void onCancelled(com.google.firebase.database.DatabaseError error) {
                callback.onError(error.toException());
            }
        };
        friendsRef.addValueEventListener(listener);
        return listener;
    }

    public void removeFriendsListListener(String userId, ValueEventListener listener) {
        if (listener != null) {
            Firebase.getUserFriendsRef(userId).removeEventListener(listener);
        }
    }

    public ListenerRegistration listenToFriendProfile(String friendUid, RealtimeCallback<User> callback) {
        return db.collection("users").document(friendUid)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            user.setUserId(documentSnapshot.getId());
                            callback.onData(user);
                        }
                    }
                });
    }

    public void joinServer(String inviteCode, String userId, String userName, RepositoryCallback<String> callback) {
        db.collection("servers").document(inviteCode).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                db.collection("users").document(userId).get().addOnSuccessListener(userDoc -> {
                    List<String> order = new ArrayList<>();
                    if (userDoc.exists()) {
                        List<String> currentOrder = (List<String>) userDoc.get("serverOrder");
                        if (currentOrder != null) {
                            order.addAll(currentOrder);
                        }
                    }

                    List<String> members = (List<String>) doc.get("members");
                    if (members != null && members.contains(userId)) {
                        if (!order.contains(inviteCode)) {
                            order.add(inviteCode);
                            db.collection("users").document(userId).update("serverOrder", order);
                        }
                        callback.onSuccess("ALREADY_IN_SERVER");
                        return;
                    }

                    // Add user to server members array
                    db.collection("servers").document(inviteCode).update("members", FieldValue.arrayUnion(userId));

                    // Add user to server members subcollection
                    ServerMember newMember = new ServerMember(userId, userName, "member");
                    db.collection("servers").document(inviteCode).collection("members").document(userId).set(newMember);

                    // Add server to user's server order
                    if (!order.contains(inviteCode)) {
                        order.add(inviteCode);
                        db.collection("users").document(userId).update("serverOrder", order);
                    }

                    callback.onSuccess("SUCCESS");
                }).addOnFailureListener(callback::onFailure);
            } else {
                callback.onSuccess("INVALID_CODE");
            }
        }).addOnFailureListener(callback::onFailure);
    }
}
