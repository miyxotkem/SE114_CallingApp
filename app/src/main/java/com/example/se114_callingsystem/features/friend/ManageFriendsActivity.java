package com.example.se114_callingsystem.features.friend;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.Firebase;
import com.example.se114_callingsystem.core.model.User;
import com.example.se114_callingsystem.core.util.ThemeHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;

public class ManageFriendsActivity extends AppCompatActivity {

    private RecyclerView rvFriendRequests, rvFriends;
    private FriendListAdapter requestAdapter, FriendListAdapter;
    private List<User> requestList = new ArrayList<>();
    private List<User> friendList = new ArrayList<>();
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_manage);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
        
        ImageView btnAddFriendTop = findViewById(R.id.btnAddFriendTop);
        btnAddFriendTop.setOnClickListener(v -> {
            AddFriendDialog dialog = new AddFriendDialog();
            dialog.show(getSupportFragmentManager(), "Add_friend");
        });

        rvFriendRequests = findViewById(R.id.rvFriendRequests);
        rvFriendRequests.setLayoutManager(new LinearLayoutManager(this));
        
        rvFriends = findViewById(R.id.rvFriends);
        rvFriends.setLayoutManager(new LinearLayoutManager(this));

        setupAdapters();
        loadFriendRequests();
        loadFriends();
    }

    private void setupAdapters() {
        requestAdapter = new FriendListAdapter(requestList, true, new FriendListAdapter.OnFriendActionListener() {
            @Override
            public void onAccept(User user) {
                String myUid = currentUser.getUid();
                String friendUid = user.getUserId();
                
                // Add to friends
                Firebase.getUserFriendsRef(myUid).child(friendUid).setValue(true);
                Firebase.getUserFriendsRef(friendUid).child(myUid).setValue(true);
                
                // Remove from requests
                Firebase.getUserFriendRequestsRef(myUid).child(friendUid).removeValue();
                
                Toast.makeText(ManageFriendsActivity.this, "ÄÃ£ cháº¥p nháº­n káº¿t báº¡n", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onReject(User user) {
                Firebase.getUserFriendRequestsRef(currentUser.getUid()).child(user.getUserId()).removeValue();
                Toast.makeText(ManageFriendsActivity.this, "ÄÃ£ tá»« chá»‘i", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onRemove(User user) {
                // Not called here
            }
        });
        rvFriendRequests.setAdapter(requestAdapter);

        FriendListAdapter = new FriendListAdapter(friendList, false, new FriendListAdapter.OnFriendActionListener() {
            @Override
            public void onAccept(User user) {}

            @Override
            public void onReject(User user) {}

            @Override
            public void onRemove(User user) {
                String myUid = currentUser.getUid();
                String friendUid = user.getUserId();
                
                Firebase.getUserFriendsRef(myUid).child(friendUid).removeValue();
                Firebase.getUserFriendsRef(friendUid).child(myUid).removeValue();
                Toast.makeText(ManageFriendsActivity.this, "ÄÃ£ xÃ³a báº¡n bÃ¨", Toast.LENGTH_SHORT).show();
            }
        });
        rvFriends.setAdapter(FriendListAdapter);
    }

    private void loadFriendRequests() {
        Firebase.getUserFriendRequestsRef(currentUser.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                requestList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot snap : snapshot.getChildren()) {
                        String senderUid = snap.getKey();
                        if (senderUid != null) {
                            loadUserAndAddToList(senderUid, requestList, requestAdapter);
                        }
                    }
                } else {
                    requestAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadFriends() {
        Firebase.getUserFriendsRef(currentUser.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                friendList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot snap : snapshot.getChildren()) {
                        String friendUid = snap.getKey();
                        if (friendUid != null) {
                            loadUserAndAddToList(friendUid, friendList, FriendListAdapter);
                        }
                    }
                } else {
                    FriendListAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadUserAndAddToList(String uid, List<User> list, FriendListAdapter adapter) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
                        user.setUserId(documentSnapshot.getId());
                        // Check if already in list to avoid duplicates due to async
                        boolean exists = false;
                        for(User u : list) {
                            if(u.getUserId() != null && u.getUserId().equals(user.getUserId())) {
                                exists = true; break;
                            }
                        }
                        if(!exists) {
                            list.add(user);
                            adapter.notifyDataSetChanged();
                        }
                    }
                }
            });
    }
}

