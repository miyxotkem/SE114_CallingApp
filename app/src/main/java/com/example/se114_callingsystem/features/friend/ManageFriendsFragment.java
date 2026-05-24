package com.example.se114_callingsystem.features.friend;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.Firebase;
import com.example.se114_callingsystem.core.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;

public class ManageFriendsFragment extends Fragment {

    private RecyclerView rvFriendRequests, rvFriends;
    private FriendListAdapter requestAdapter, FriendListAdapter;
    private List<User> requestList = new ArrayList<>();
    private List<User> friendList = new ArrayList<>();
    private FirebaseUser currentUser;
    private ValueEventListener requestsListener, friendsListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friend_manage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Navigation.findNavController(view).popBackStack();
            return;
        }

        ImageView btnBack = view.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());
        
        ImageView btnAddFriendTop = view.findViewById(R.id.btnAddFriendTop);
        btnAddFriendTop.setOnClickListener(v -> {
            AddFriendDialog dialog = new AddFriendDialog();
            dialog.show(getParentFragmentManager(), "Add_friend");
        });

        rvFriendRequests = view.findViewById(R.id.rvFriendRequests);
        rvFriendRequests.setLayoutManager(new LinearLayoutManager(getContext()));
        
        rvFriends = view.findViewById(R.id.rvFriends);
        rvFriends.setLayoutManager(new LinearLayoutManager(getContext()));

        setupAdapters();
        loadFriendRequests();
        loadFriends();
    }

    private void setupAdapters() {
        requestAdapter = new FriendListAdapter(requestList, true, new FriendListAdapter.OnFriendActionListener() {
            @Override
            public void onAccept(User user) {
                if (currentUser == null) return;
                String myUid = currentUser.getUid();
                String friendUid = user.getUserId();
                
                // Add to friends
                Firebase.getUserFriendsRef(myUid).child(friendUid).setValue(true);
                Firebase.getUserFriendsRef(friendUid).child(myUid).setValue(true);
                
                // Remove from requests
                Firebase.getUserFriendRequestsRef(myUid).child(friendUid).removeValue();
                
                if (getContext() != null) {
                    Toast.makeText(requireContext(), "Đã chấp nhận kết bạn", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onReject(User user) {
                if (currentUser == null) return;
                Firebase.getUserFriendRequestsRef(currentUser.getUid()).child(user.getUserId()).removeValue();
                if (getContext() != null) {
                    Toast.makeText(requireContext(), "Đã từ chối", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onRemove(User user) {}
        });
        rvFriendRequests.setAdapter(requestAdapter);

        FriendListAdapter = new FriendListAdapter(friendList, false, new FriendListAdapter.OnFriendActionListener() {
            @Override
            public void onAccept(User user) {}

            @Override
            public void onReject(User user) {}

            @Override
            public void onRemove(User user) {
                if (currentUser == null) return;
                String myUid = currentUser.getUid();
                String friendUid = user.getUserId();
                
                Firebase.getUserFriendsRef(myUid).child(friendUid).removeValue();
                Firebase.getUserFriendsRef(friendUid).child(myUid).removeValue();
                if (getContext() != null) {
                    Toast.makeText(requireContext(), "Đã xóa bạn bè", Toast.LENGTH_SHORT).show();
                }
            }
        });
        rvFriends.setAdapter(FriendListAdapter);
    }

    private void loadFriendRequests() {
        if (currentUser == null) return;
        requestsListener = Firebase.getUserFriendRequestsRef(currentUser.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (getView() == null) return;
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
        if (currentUser == null) return;
        friendsListener = Firebase.getUserFriendsRef(currentUser.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (getView() == null) return;
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
                if (documentSnapshot.exists() && getView() != null) {
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (currentUser != null) {
            if (requestsListener != null) {
                Firebase.getUserFriendRequestsRef(currentUser.getUid()).removeEventListener(requestsListener);
            }
            if (friendsListener != null) {
                Firebase.getUserFriendsRef(currentUser.getUid()).removeEventListener(friendsListener);
            }
        }
    }
}
