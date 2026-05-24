package com.example.se114_callingsystem.features.server;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.features.friend.SelectFriendAdapter;
import com.example.se114_callingsystem.core.model.Firebase;
import com.example.se114_callingsystem.core.model.Server;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.example.se114_callingsystem.core.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class AddServerMemberDialog extends DialogFragment {

    private String serverId;
    private RecyclerView rvFriendsToSelect;
    private SelectFriendAdapter adapter;
    private List<User> friendList = new ArrayList<>();
    private List<String> currentServerMembers = new ArrayList<>();

    public AddServerMemberDialog(String serverId) {
        this.serverId = serverId;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return inflater.inflate(R.layout.dialog_server_add_member, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvFriendsToSelect = view.findViewById(R.id.rvFriendsToSelect);
        rvFriendsToSelect.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SelectFriendAdapter(friendList);
        rvFriendsToSelect.setAdapter(adapter);

        Button btnAddConfirm = view.findViewById(R.id.btnAddConfirm);

        loadServerMembersAndFriends();

        btnAddConfirm.setOnClickListener(v -> {
            List<User> selectedUsers = adapter.getSelectedUsers();
            if (selectedUsers.isEmpty()) {
                Toast.makeText(getContext(), "Vui lÃ²ng chá»n Ã­t nháº¥t má»™t ngÆ°á»i", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(getContext())
                    .setTitle("XÃ¡c nháº­n")
                    .setMessage("ThÃªm " + selectedUsers.size() + " ngÆ°á»i vÃ o server?")
                    .setPositiveButton("ThÃªm", (dialog, which) -> addSelectedUsersToServer(selectedUsers))
                    .setNegativeButton("Há»§y", null)
                    .show();
        });
    }

    private void loadServerMembersAndFriends() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("servers").document(serverId).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Server server = documentSnapshot.toObject(Server.class);
                if (server != null && server.getMembers() != null) {
                    currentServerMembers.addAll(server.getMembers());
                }

                // Now load friends
                Firebase.getUserFriendsRef(currentUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        friendList.clear();
                        if (snapshot.exists()) {
                            boolean hasFriendsToAdd = false;
                            for (DataSnapshot snap : snapshot.getChildren()) {
                                String friendUid = snap.getKey();
                                if (friendUid != null && !currentServerMembers.contains(friendUid)) {
                                    hasFriendsToAdd = true;
                                    // Fetch user info from Firestore
                                    db.collection("users").document(friendUid).get().addOnSuccessListener(userDoc -> {
                                        if (userDoc.exists()) {
                                            User user = userDoc.toObject(User.class);
                                            if (user != null) {
                                                user.setUserId(userDoc.getId());
                                                friendList.add(user);
                                                adapter.notifyDataSetChanged();
                                            }
                                        }
                                    });
                                }
                            }
                            if (!hasFriendsToAdd) {
                                Toast.makeText(getContext(), "Táº¥t cáº£ báº¡n bÃ¨ cá»§a báº¡n Ä‘Ã£ cÃ³ trong server nÃ y", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getContext(), "Báº¡n chÆ°a cÃ³ báº¡n bÃ¨ nÃ o Ä‘á»ƒ thÃªm", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(getContext(), "Lá»—i táº£i danh sÃ¡ch báº¡n bÃ¨", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void addSelectedUsersToServer(List<User> selectedUsers) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        List<String> uidsToAdd = new ArrayList<>();
        
        // Add each user to the members subcollection
        for (User user : selectedUsers) {
            String uid = user.getUserId();
            String name = user.getUsername() != null ? user.getUsername() : "Unknown";
            uidsToAdd.add(uid);
            
            ServerMember newMember = new ServerMember(uid, name, "member");
            db.collection("servers").document(serverId).collection("members").document(uid).set(newMember);
        }
        
        // Add all uids to the server's members array
        if (!uidsToAdd.isEmpty()) {
            db.collection("servers").document(serverId).update("members", FieldValue.arrayUnion(uidsToAdd.toArray(new Object[0])))
                .addOnSuccessListener(aVoid -> {
                    if (getActivity() != null) {
                        Toast.makeText(getContext(), "ÄÃ£ thÃªm " + selectedUsers.size() + " ngÆ°á»i vÃ o server", Toast.LENGTH_SHORT).show();
                    }
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Lá»—i: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }
}

