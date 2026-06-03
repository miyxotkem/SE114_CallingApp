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

        loadUserStatus();
    }
    
    private void showJoinServerDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("Nhập mã mời (Server ID)");
        
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Tham gia Server")
            .setMessage("Nhập mã mời bạn nhận được từ bạn bè:")
            .setView(input)
            .setPositiveButton("Tham gia", (dialog, which) -> {
                String inviteCode = input.getText().toString().trim();
                if (!inviteCode.isEmpty()) {
                    joinServer(inviteCode);
                }
            })
            .setNegativeButton("Hủy", null)
            .show();
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
            db.collection("users").document(uid).addSnapshotListener((doc, error) -> {
                if (doc != null && doc.exists() && binding != null) {
                    String status = doc.getString("status");
                    if (status == null) status = "online";
                    
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

        view.findViewById(R.id.btnStatusOnline).setOnClickListener(v -> {
            updateUserStatus("online");
            bottomSheetDialog.dismiss();
        });
        view.findViewById(R.id.btnStatusIdle).setOnClickListener(v -> {
            updateUserStatus("idle");
            bottomSheetDialog.dismiss();
        });
        view.findViewById(R.id.btnStatusDnd).setOnClickListener(v -> {
            updateUserStatus("dnd");
            bottomSheetDialog.dismiss();
        });
        view.findViewById(R.id.btnStatusInvisible).setOnClickListener(v -> {
            updateUserStatus("offline");
            bottomSheetDialog.dismiss();
        });
        view.findViewById(R.id.btnStatusSleeping).setOnClickListener(v -> {
            updateUserStatus("sleeping");
            bottomSheetDialog.dismiss();
        });
        view.findViewById(R.id.btnStatusEating).setOnClickListener(v -> {
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
        
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Custom Status")
            .setView(input)
            .setPositiveButton("Save", (dialog, which) -> {
                String text = input.getText().toString().trim();
                if (!text.isEmpty()) {
                    updateUserStatus(text);
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

