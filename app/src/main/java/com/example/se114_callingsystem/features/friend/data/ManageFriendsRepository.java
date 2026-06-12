package com.example.se114_callingsystem.features.friend.data;

import com.example.se114_callingsystem.core.model.Firebase;
import com.example.se114_callingsystem.core.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

public class ManageFriendsRepository {

    private final FirebaseAuth mAuth;
    private final FirebaseFirestore db;
    private final FirebaseDatabase rtdb;

    @Inject
    public ManageFriendsRepository(FirebaseAuth mAuth, FirebaseFirestore db, FirebaseDatabase rtdb) {
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

    public ValueEventListener listenToFriendRequests(String userId, RealtimeCallback<List<String>> callback) {
        DatabaseReference requestsRef = Firebase.getUserFriendRequestsRef(userId);
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<String> list = new ArrayList<>();
                if (snapshot.exists()) {
                    for (DataSnapshot snap : snapshot.getChildren()) {
                        if (snap.getKey() != null) {
                            list.add(snap.getKey());
                        }
                    }
                }
                callback.onData(list);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                callback.onError(error.toException());
            }
        };
        requestsRef.addValueEventListener(listener);
        return listener;
    }

    public void removeFriendRequestsListener(String userId, ValueEventListener listener) {
        if (listener != null) {
            Firebase.getUserFriendRequestsRef(userId).removeEventListener(listener);
        }
    }

    public ValueEventListener listenToFriendsList(String userId, RealtimeCallback<List<String>> callback) {
        DatabaseReference friendsRef = Firebase.getUserFriendsRef(userId);
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<String> list = new ArrayList<>();
                if (snapshot.exists()) {
                    for (DataSnapshot snap : snapshot.getChildren()) {
                        if (snap.getKey() != null) {
                            list.add(snap.getKey());
                        }
                    }
                }
                callback.onData(list);
            }

            @Override
            public void onCancelled(DatabaseError error) {
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

    public void loadUserProfile(String userId, RepositoryCallback<User> callback) {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            user.setUserId(documentSnapshot.getId());
                            callback.onSuccess(user);
                        } else {
                            callback.onFailure(new Exception("Failed to parse user profile"));
                        }
                    } else {
                        callback.onFailure(new Exception("User not found"));
                    }
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void acceptFriendRequest(String myUid, String friendUid, RepositoryCallback<Void> callback) {
        // 1. Add to friends lists in RTDB
        Firebase.getUserFriendsRef(myUid).child(friendUid).setValue(true)
                .addOnSuccessListener(aVoid -> {
                    Firebase.getUserFriendsRef(friendUid).child(myUid).setValue(true)
                            .addOnSuccessListener(aVoid2 -> {
                                // 2. Remove from requests in RTDB
                                Firebase.getUserFriendRequestsRef(myUid).child(friendUid).removeValue()
                                        .addOnSuccessListener(aVoid3 -> {
                                            sendFriendNotification(myUid, friendUid, "friend_accepted");
                                            callback.onSuccess(null);
                                        })
                                        .addOnFailureListener(callback::onFailure);
                            })
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void rejectFriendRequest(String myUid, String friendUid, RepositoryCallback<Void> callback) {
        Firebase.getUserFriendRequestsRef(myUid).child(friendUid).removeValue()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void removeFriend(String myUid, String friendUid, RepositoryCallback<Void> callback) {
        Firebase.getUserFriendsRef(myUid).child(friendUid).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Firebase.getUserFriendsRef(friendUid).child(myUid).removeValue()
                            .addOnSuccessListener(aVoid2 -> callback.onSuccess(null))
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void sendFriendRequest(String myUid, String myEmail, String targetEmail, RepositoryCallback<Void> callback) {
        if (myEmail != null && myEmail.equals(targetEmail)) {
            callback.onFailure(new Exception("SELF_REQUEST"));
            return;
        }

        db.collection("users").whereEqualTo("email", targetEmail).get()
                .addOnSuccessListener(snaps -> {
                    if (!snaps.isEmpty()) {
                        DocumentSnapshot userSnap = snaps.getDocuments().get(0);
                        String friendUid = userSnap.getString("uid");
                        String targetPlan = userSnap.getString("plan");
                        if (targetPlan == null) targetPlan = "Basic";
                        
                        final int limit;
                        if ("Standard".equals(targetPlan)) limit = 100;
                        else if ("Pro".equals(targetPlan)) limit = Integer.MAX_VALUE;
                        else limit = 25;

                        if (friendUid != null) {
                            Firebase.getUserFriendsRef(friendUid).addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot snapshot) {
                                    long count = snapshot.getChildrenCount();
                                    if (count >= limit) {
                                        callback.onFailure(new Exception("TARGET_LIMIT_REACHED"));
                                    } else {
                                        Firebase.getUserFriendRequestsRef(friendUid).child(myUid).setValue(true)
                                                .addOnSuccessListener(aVoid -> {
                                                    sendFriendNotification(myUid, friendUid, "friend_request");
                                                    callback.onSuccess(null);
                                                })
                                                .addOnFailureListener(callback::onFailure);
                                    }
                                }

                                @Override
                                public void onCancelled(DatabaseError error) {
                                    callback.onFailure(error.toException());
                                }
                            });
                        } else {
                            callback.onFailure(new Exception("INVALID_USER_DATA"));
                        }
                    } else {
                        callback.onFailure(new Exception("USER_NOT_FOUND"));
                    }
                })
                .addOnFailureListener(callback::onFailure);
    }

    private void sendFriendNotification(String myUid, String targetUid, String type) {
        db.collection("users").document(myUid).get()
                .addOnSuccessListener(doc -> {
                    String myName = "Ai đó";
                    if (doc.exists()) {
                        String name = doc.getString("username");
                        if (name != null && !name.isEmpty()) {
                            myName = name;
                        } else {
                            String email = doc.getString("email");
                            if (email != null && !email.isEmpty()) {
                                myName = email;
                            }
                        }
                    }

                    String title = "friend_request".equals(type) ? "Lời mời kết bạn" : "Chấp nhận kết bạn";
                    String content = "friend_request".equals(type)
                            ? myName + " đã gửi cho bạn một lời mời kết bạn."
                            : myName + " đã đồng ý lời mời kết bạn của bạn.";

                    Map<String, Object> notif = new HashMap<>();
                    String notifId = String.valueOf(System.currentTimeMillis());
                    notif.put("notificationId", notifId);
                    notif.put("title", title);
                    notif.put("content", content);
                    notif.put("type", type);
                    notif.put("senderId", myUid);
                    notif.put("senderName", myName);
                    notif.put("targetId", myUid);
                    notif.put("timestamp", System.currentTimeMillis());
                    notif.put("isRead", false);

                    db.collection("users").document(targetUid)
                            .collection("notifications").document(notifId)
                            .set(notif);
                });
    }
}
