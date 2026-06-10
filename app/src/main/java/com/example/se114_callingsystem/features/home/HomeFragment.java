package com.example.se114_callingsystem.features.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.databinding.FragmentHomeBinding;
import com.example.se114_callingsystem.R;

import com.example.se114_callingsystem.core.model.Server;
import com.example.se114_callingsystem.core.model.User;
import com.example.se114_callingsystem.features.server.CreateServerDialog;
import com.example.se114_callingsystem.features.server.ServerAdapter;
import com.example.se114_callingsystem.core.service.MessageNotificationService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    private FragmentHomeBinding binding;
    private ServerAdapter adapter;
    private List<Server> serverList;
    private List<String> currentServerOrder;
    private FirebaseFirestore db;
    private HomeDMAdapter dmAdapter;
    private List<User> friendList = new java.util.ArrayList<>();
    private com.google.firebase.database.ValueEventListener friendsListener;
    private List<com.google.firebase.firestore.ListenerRegistration> friendProfileListeners = new java.util.ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);



        db = FirebaseFirestore.getInstance();
        serverList = new ArrayList<>();
        adapter = new ServerAdapter(serverList);

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerView.setAdapter(adapter);

        setupDragAndDrop();

        fetchServers();
        checkNotificationPermission();
        startMessageNotificationService();

        binding.mcvServerCreate.setOnClickListener(v -> {
            CreateServerDialog dialog = new CreateServerDialog();
            dialog.show(getParentFragmentManager(), "Server_on_create");
        });

        binding.mcvServerJoin.setOnClickListener(v -> {
            showJoinServerDialog();
        });

        binding.btnManageFriends.setOnClickListener(v -> {
            androidx.navigation.Navigation.findNavController(v).navigate(R.id.nav_friend_manage);
        });

        binding.btnStatus.setOnClickListener(v -> showStatusDialog());

        // Setup Direct Messages list
        binding.rvDirectMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        dmAdapter = new HomeDMAdapter(friendList, this::onFriendClick);
        binding.rvDirectMessages.setAdapter(dmAdapter);

        loadFriends();
        loadUserStatus();
    }
    
    private void showJoinServerDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_join_server, null);
        dialog.setContentView(view);
        
        View parent = (View) view.getParent();
        if (parent != null) {
            parent.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }

        com.google.android.material.textfield.TextInputEditText edtInviteCode = view.findViewById(R.id.edtInviteCode);
        com.google.android.material.button.MaterialButton btnJoin = view.findViewById(R.id.btnJoinServer);

        btnJoin.setOnClickListener(v -> {
            String inviteCode = edtInviteCode.getText() != null ? edtInviteCode.getText().toString().trim() : "";
            if (!inviteCode.isEmpty()) {
                joinServer(inviteCode);
                dialog.dismiss();
            } else {
                edtInviteCode.setError("Vui lòng nhập mã mời");
            }
        });

        dialog.show();
    }
    
    private void joinServer(String serverId) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        String userName = FirebaseAuth.getInstance().getCurrentUser() != null && FirebaseAuth.getInstance().getCurrentUser().getDisplayName() != null ? FirebaseAuth.getInstance().getCurrentUser().getDisplayName() : "New Member";
        if (uid == null) return;
        
        db.collection("servers").document(serverId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                java.util.List<String> members = (java.util.List<String>) doc.get("members");
                if (members != null && members.contains(uid)) {
                    android.widget.Toast.makeText(getContext(), "Bạn đã ở trong server này rồi!", android.widget.Toast.LENGTH_SHORT).show();
                    if (!currentServerOrder.contains(serverId)) {
                        currentServerOrder.add(serverId);
                        db.collection("users").document(uid).update("serverOrder", currentServerOrder);
                    }
                    return;
                }

                // Add user to server members array
                db.collection("servers").document(serverId).update("members", com.google.firebase.firestore.FieldValue.arrayUnion(uid));
                
                // Add user to server members subcollection
                com.example.se114_callingsystem.core.model.ServerMember newMember = new com.example.se114_callingsystem.core.model.ServerMember(uid, userName, "member");
                db.collection("servers").document(serverId).collection("members").document(uid).set(newMember);
                
                // Add server to user's server order
                if (!currentServerOrder.contains(serverId)) {
                    currentServerOrder.add(serverId);
                    db.collection("users").document(uid).update("serverOrder", currentServerOrder);
                }
                
                android.widget.Toast.makeText(getContext(), "Tham gia server thành công!", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                android.widget.Toast.makeText(getContext(), "Mã mời không hợp lệ hoặc Server không tồn tại.", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadUserStatus() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid != null) {
            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users/" + uid + "/status")
                .addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot doc) {
                        if (binding == null) return;
                        String status = doc.getValue(String.class);
                        if (status == null) status = "offline";
                    
                    int colorRes = R.color.discord_green;
                    String displayText = "Online";
                    
                    switch (status.toLowerCase()) {
                        case "idle":
                        case "idling":
                            colorRes = R.color.discord_yellow;
                            displayText = "Idle";
                            break;
                        case "dnd":
                        case "do not disturb":
                            colorRes = R.color.discord_red;
                            displayText = "Do Not Disturb";
                            break;
                        case "offline":
                        case "invisible":
                            colorRes = R.color.discord_text_muted;
                            displayText = "Invisible";
                            break;
                        case "sleeping":
                            colorRes = R.color.discord_blurple;
                            displayText = "Sleeping 💤";
                            break;
                        case "eating":
                            colorRes = R.color.discord_blurple;
                            displayText = "Eating 🍕";
                            break;
                        default:
                            if (!status.equalsIgnoreCase("online")) {
                                colorRes = R.color.discord_blurple;
                                displayText = status;
                            }
                            break;
                    }
                    
                    binding.tvStatusText.setText(displayText);
                    binding.tvStatusText.setTextColor(getResources().getColor(colorRes));
                    binding.statusIndicatorColor.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(colorRes)));
                    }
                    @Override
                    public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {}
            });
        }
    }

    private void showStatusDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_status, null);
        bottomSheetDialog.setContentView(view);
        
        View parent = (View) view.getParent();
        if (parent != null) {
            parent.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }

        view.findViewById(R.id.btnStatusAuto).setOnClickListener(v -> {
            requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE).edit().putString("manual_status", "auto").apply();
            updateUserStatus("online");
            bottomSheetDialog.dismiss();
        });
        view.findViewById(R.id.btnStatusDnd).setOnClickListener(v -> {
            requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE).edit().putString("manual_status", "dnd").apply();
            updateUserStatus("dnd");
            bottomSheetDialog.dismiss();
        });
        view.findViewById(R.id.btnStatusSleeping).setOnClickListener(v -> {
            requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE).edit().putString("manual_status", "sleeping").apply();
            updateUserStatus("sleeping");
            bottomSheetDialog.dismiss();
        });
        view.findViewById(R.id.btnStatusEating).setOnClickListener(v -> {
            requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE).edit().putString("manual_status", "eating").apply();
            updateUserStatus("eating");
            bottomSheetDialog.dismiss();
        });
        view.findViewById(R.id.btnStatusCustom).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showCustomStatusDialog();
        });

        bottomSheetDialog.show();
    }

    private void showCustomStatusDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("Enter custom status (e.g. Coding 💻)");
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Custom Status")
            .setView(input)
            .setPositiveButton("Set", (d, w) -> {
                String s = input.getText().toString().trim();
                if (!s.isEmpty()) {
                    requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE).edit().putString("manual_status", s).apply();
                    updateUserStatus(s);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void updateUserStatus(String status) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid != null) {
            db.collection("users").document(uid).update("status", status)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update status", e));
        }
    }

    private void checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void startMessageNotificationService() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null && getContext() != null) {
            Intent serviceIntent = new Intent(getContext(), MessageNotificationService.class);
            requireContext().startService(serviceIntent);
        }
    }

    private void fetchServers() {
        String currentUserUid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        if (currentUserUid.isEmpty()) return;

        db.collection("users").document(currentUserUid).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                com.example.se114_callingsystem.core.model.User user = documentSnapshot.toObject(com.example.se114_callingsystem.core.model.User.class);
                if (user != null && user.getServerOrder() != null) {
                    currentServerOrder = user.getServerOrder();
                } else {
                    currentServerOrder = new ArrayList<>();
                }
            } else {
                currentServerOrder = new ArrayList<>();
            }

            db.collection("servers")
              .whereArrayContains("members", currentUserUid)
              .addSnapshotListener((value, error) -> {
                if (error != null) {
                Log.e(TAG, "Error fetching servers: " + error.getMessage());
                return;
            }
            if (value != null && binding != null) {
                serverList.clear();
                for (com.google.firebase.firestore.DocumentSnapshot doc : value) {
                    Server server = doc.toObject(Server.class);
                    if (server != null) {
                        serverList.add(server);
                    }
                }
                
                // Sort by currentServerOrder
                serverList.sort((s1, s2) -> {
                    int idx1 = currentServerOrder.indexOf(s1.getServerId());
                    int idx2 = currentServerOrder.indexOf(s2.getServerId());
                    if (idx1 == -1) idx1 = Integer.MAX_VALUE;
                    if (idx2 == -1) idx2 = Integer.MAX_VALUE;
                    return Integer.compare(idx1, idx2);
                });
                
                adapter.notifyDataSetChanged();
            }
        });
        });
    }

    private void setupDragAndDrop() {
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();

                java.util.Collections.swap(serverList, fromPosition, toPosition);
                adapter.notifyItemMoved(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Not supported
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                saveServerOrder();
            }
        });
        itemTouchHelper.attachToRecyclerView(binding.recyclerView);
    }

    private void saveServerOrder() {
        String currentUserUid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        if (currentUserUid.isEmpty()) return;

        List<String> order = new ArrayList<>();
        for (Server s : serverList) {
            order.add(s.getServerId());
        }
        currentServerOrder = order;

        db.collection("users").document(currentUserUid)
            .update("serverOrder", order)
            .addOnFailureListener(e -> Log.e(TAG, "Failed to save server order", e));
    }

    private void loadFriends() {
        String currentUserUid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        if (currentUserUid.isEmpty()) return;

        friendsListener = com.example.se114_callingsystem.core.model.Firebase.getUserFriendsRef(currentUserUid).addValueEventListener(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                if (binding == null) return;
                
                // Clear old snapshot listeners
                for (com.google.firebase.firestore.ListenerRegistration registration : friendProfileListeners) {
                    if (registration != null) {
                        registration.remove();
                    }
                }
                friendProfileListeners.clear();
                friendList.clear();
                dmAdapter.notifyDataSetChanged();
                
                if (snapshot.exists()) {
                    binding.layoutNoDMs.setVisibility(View.GONE);
                    binding.rvDirectMessages.setVisibility(View.VISIBLE);
                    
                    for (com.google.firebase.database.DataSnapshot snap : snapshot.getChildren()) {
                        String friendUid = snap.getKey();
                        if (friendUid != null) {
                            listenToFriendProfile(friendUid);
                        }
                    }
                } else {
                    binding.layoutNoDMs.setVisibility(View.VISIBLE);
                    binding.rvDirectMessages.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                Log.e(TAG, "Error loading friends from Realtime DB", error.toException());
            }
        });
    }

    private void listenToFriendProfile(String friendUid) {
        com.google.firebase.firestore.ListenerRegistration registration = db.collection("users").document(friendUid)
            .addSnapshotListener((documentSnapshot, error) -> {
                if (error != null) {
                    Log.e(TAG, "Error listening to friend: " + friendUid, error);
                    return;
                }
                if (binding == null) return;
                
                if (documentSnapshot != null && documentSnapshot.exists()) {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
                        user.setUserId(documentSnapshot.getId());
                        
                        // Find if this friend already exists in our list
                        int foundIndex = -1;
                        for (int i = 0; i < friendList.size(); i++) {
                            if (friendList.get(i).getUserId() != null && friendList.get(i).getUserId().equals(user.getUserId())) {
                                foundIndex = i;
                                break;
                            }
                        }
                        
                        if (foundIndex != -1) {
                            // Update existing friend's profile details
                            friendList.set(foundIndex, user);
                        } else {
                            // Add new friend to the list
                            friendList.add(user);
                        }
                        
                        dmAdapter.notifyDataSetChanged();
                        
                        // Check if list is empty to toggle visibility
                        if (friendList.isEmpty()) {
                            binding.layoutNoDMs.setVisibility(View.VISIBLE);
                            binding.rvDirectMessages.setVisibility(View.GONE);
                        } else {
                            binding.layoutNoDMs.setVisibility(View.GONE);
                            binding.rvDirectMessages.setVisibility(View.VISIBLE);
                        }
                    }
                }
            });
        friendProfileListeners.add(registration);
    }

    private void onFriendClick(User friend) {
        String myUid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        String friendUid = friend.getUserId();
        if (myUid.isEmpty() || friendUid == null || friendUid.isEmpty()) return;

        String dmRoomId;
        if (myUid.compareTo(friendUid) < 0) {
            dmRoomId = "dm_" + myUid + "_" + friendUid;
        } else {
            dmRoomId = "dm_" + friendUid + "_" + myUid;
        }

        String displayName = friend.getUsername();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = friend.getEmail();
        }

        Bundle args = new Bundle();
        args.putString("CHAT_ID", dmRoomId);
        args.putString("CHAT_NAME", displayName);
        args.putString("SERVER_ID", null);
        args.putString("SERVER_COLOR", "#5865F2");

        if (getView() != null) {
            androidx.navigation.Navigation.findNavController(getView()).navigate(R.id.action_home_to_chat_detail, args);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Remove Realtime DB friend list listener
        String currentUserUid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        if (!currentUserUid.isEmpty() && friendsListener != null) {
            com.example.se114_callingsystem.core.model.Firebase.getUserFriendsRef(currentUserUid).removeEventListener(friendsListener);
        }
        
        // Remove Firestore profile listeners
        for (com.google.firebase.firestore.ListenerRegistration registration : friendProfileListeners) {
            if (registration != null) {
                registration.remove();
            }
        }
        friendProfileListeners.clear();
        
        binding = null;
    }
}

