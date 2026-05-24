package com.example.se114_callingsystem.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.se114_callingsystem.databinding.FragmentHomeBinding;
import com.example.se114_callingsystem.friend.ManageFriendsActivity;
import com.example.se114_callingsystem.model.Server;
import com.example.se114_callingsystem.profile.ProfileActivity;
import com.example.se114_callingsystem.server.CreateServerDialog;
import com.example.se114_callingsystem.server.ServerAdapter;
import com.example.se114_callingsystem.service.MessageNotificationService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    private FragmentHomeBinding binding;
    private ServerAdapter adapter;
    private List<Server> serverList;
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

        fetchServers();
        checkNotificationPermission();
        startMessageNotificationService();

        binding.mcvServerCreate.setOnClickListener(v -> {
            CreateServerDialog dialog = new CreateServerDialog();
            dialog.show(getParentFragmentManager(), "Server_on_create");
        });

        binding.btnManageFriends.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), ManageFriendsActivity.class);
            startActivity(intent);
        });

        binding.btnAddFriend.setOnClickListener(v -> {
            com.example.se114_callingsystem.friend.AddFriendDialog dialog = new com.example.se114_callingsystem.friend.AddFriendDialog();
            dialog.show(getParentFragmentManager(), "Add_friend_dialog");
        });
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
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
