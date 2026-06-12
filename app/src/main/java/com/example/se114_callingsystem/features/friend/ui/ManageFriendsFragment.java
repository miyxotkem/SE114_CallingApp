package com.example.se114_callingsystem.features.friend.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.User;
import com.example.se114_callingsystem.features.friend.viewmodel.ManageFriendsViewModel;
import com.google.firebase.auth.FirebaseUser;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.List;

@AndroidEntryPoint
public class ManageFriendsFragment extends Fragment {

    private RecyclerView rvFriendRequests, rvFriends;
    private FriendListAdapter requestAdapter, friendListAdapter;
    
    // Lists bound to adapters
    private final List<User> requestList = new ArrayList<>();
    private final List<User> friendList = new ArrayList<>();
    
    private String currentSearchQuery = "";
    private FirebaseUser currentUser;
    private ManageFriendsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friend_manage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ManageFriendsViewModel.class);
        currentUser = viewModel.getCurrentUser();
        if (currentUser == null) {
            Navigation.findNavController(view).popBackStack();
            return;
        }

        ImageView btnBack = view.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());
        
        ImageView btnAddFriendTop = view.findViewById(R.id.btnAddFriendTop);
        btnAddFriendTop.setOnClickListener(v -> {
            AddFriendDialog dialog = new AddFriendDialog();
            dialog.show(getChildFragmentManager(), "Add_friend");
        });

        // Set up empty state button
        View btnEmptyAddFriend = view.findViewById(R.id.btnEmptyAddFriend);
        if (btnEmptyAddFriend != null) {
            btnEmptyAddFriend.setOnClickListener(v -> {
                AddFriendDialog dialog = new AddFriendDialog();
                dialog.show(getChildFragmentManager(), "Add_friend");
            });
        }

        // Search Bar Setup
        EditText etSearch = view.findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                viewModel.filterLists(currentSearchQuery);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        rvFriendRequests = view.findViewById(R.id.rvFriendRequests);
        rvFriendRequests.setLayoutManager(new LinearLayoutManager(getContext()));
        
        rvFriends = view.findViewById(R.id.rvFriends);
        rvFriends.setLayoutManager(new LinearLayoutManager(getContext()));

        setupAdapters();
        setupObservers();
        viewModel.initFriends();
    }

    private void setupObservers() {
        viewModel.getFriendRequests().observe(getViewLifecycleOwner(), list -> {
            if (bindingNullCheck()) return;
            requestList.clear();
            requestList.addAll(list);
            requestAdapter.notifyDataSetChanged();
            updateRequestVisibility();
        });

        viewModel.getFriends().observe(getViewLifecycleOwner(), list -> {
            if (bindingNullCheck()) return;
            friendList.clear();
            friendList.addAll(list);
            friendListAdapter.notifyDataSetChanged();
            updateFriendVisibility();
        });

        viewModel.getOperationStatus().observe(getViewLifecycleOwner(), status -> {
            if (status == null || getContext() == null) return;

            switch (status) {
                case "ACCEPT_SUCCESS":
                    Toast.makeText(getContext(), "Đã chấp nhận kết bạn", Toast.LENGTH_SHORT).show();
                    break;
                case "REJECT_SUCCESS":
                    Toast.makeText(getContext(), "Đã từ chối lời mời", Toast.LENGTH_SHORT).show();
                    break;
                case "REMOVE_SUCCESS":
                    Toast.makeText(getContext(), "Đã xóa bạn bè", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    if (status.startsWith("Failed to")) {
                        Toast.makeText(getContext(), status, Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
            viewModel.resetStatus();
        });
    }

    private boolean bindingNullCheck() {
        return getView() == null;
    }

    private void setupAdapters() {
        requestAdapter = new FriendListAdapter(requestList, true, new FriendListAdapter.OnFriendActionListener() {
            @Override
            public void onAccept(User user) {
                if (user.getUserId() != null) {
                    android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE);
                    String currentPlan = prefs.getString("current_plan", "Basic");
                    int limit = 25;
                    if ("Standard".equals(currentPlan)) limit = 100;
                    else if ("Pro".equals(currentPlan)) limit = Integer.MAX_VALUE;

                    int currentFriends = friendList.size();
                    if (currentFriends >= limit) {
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Plan Limit Reached")
                            .setMessage("You reached the limit of " + (limit == Integer.MAX_VALUE ? "unlimited" : limit) + " friends on your " + currentPlan + " plan. Upgrade your plan to accept this request.")
                            .setPositiveButton("Upgrade", (dialog, which) -> {
                                Navigation.findNavController(getView()).navigate(R.id.action_friend_manage_to_upgrade_plan);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                        return;
                    }
                    viewModel.acceptRequest(user.getUserId());
                }
            }

            @Override
            public void onReject(User user) {
                if (user.getUserId() != null) {
                    viewModel.rejectRequest(user.getUserId());
                }
            }

            @Override
            public void onRemove(User user) {}

            @Override
            public void onMessage(User user) {}
        });
        rvFriendRequests.setAdapter(requestAdapter);

        friendListAdapter = new FriendListAdapter(friendList, false, new FriendListAdapter.OnFriendActionListener() {
            @Override
            public void onAccept(User user) {}

            @Override
            public void onReject(User user) {}

            @Override
            public void onRemove(User user) {
                if (user.getUserId() != null) {
                    viewModel.removeFriend(user.getUserId());
                }
            }

            @Override
            public void onMessage(User user) {
                if (currentUser == null) return;
                String myUid = currentUser.getUid();
                String friendUid = user.getUserId();
                if (friendUid == null || friendUid.isEmpty()) return;

                String dmRoomId;
                if (myUid.compareTo(friendUid) < 0) {
                    dmRoomId = "dm_" + myUid + "_" + friendUid;
                } else {
                    dmRoomId = "dm_" + friendUid + "_" + myUid;
                }

                String displayName = user.getUsername();
                if (displayName == null || displayName.trim().isEmpty()) {
                    displayName = user.getEmail();
                }

                Bundle args = new Bundle();
                args.putString("CHAT_ID", dmRoomId);
                args.putString("CHAT_NAME", displayName);
                args.putString("SERVER_ID", null);
                args.putString("SERVER_COLOR", "#5865F2");

                if (getView() != null) {
                    Navigation.findNavController(getView()).navigate(R.id.action_friend_manage_to_chat_detail, args);
                }
            }
        });
        rvFriends.setAdapter(friendListAdapter);
    }

    private void updateRequestVisibility() {
        View view = getView();
        if (view == null) return;
        
        TextView tvFriendRequestsHeader = view.findViewById(R.id.tvFriendRequestsHeader);
        View rvFriendRequests = view.findViewById(R.id.rvFriendRequests);
        
        if (requestList.isEmpty()) {
            tvFriendRequestsHeader.setVisibility(View.GONE);
            rvFriendRequests.setVisibility(View.GONE);
        } else {
            tvFriendRequestsHeader.setVisibility(View.VISIBLE);
            tvFriendRequestsHeader.setText("Lời mời kết bạn (" + requestList.size() + ")");
            rvFriendRequests.setVisibility(View.VISIBLE);
        }
    }

    private void updateFriendVisibility() {
        View view = getView();
        if (view == null) return;

        TextView tvFriendsHeader = view.findViewById(R.id.tvFriendsHeader);
        View rvFriends = view.findViewById(R.id.rvFriends);
        View llEmptyFriends = view.findViewById(R.id.llEmptyFriends);
        
        if (friendList.isEmpty()) {
            tvFriendsHeader.setVisibility(View.GONE);
            rvFriends.setVisibility(View.GONE);
            
            // Show empty state layout only if search text is empty
            if (currentSearchQuery.isEmpty()) {
                llEmptyFriends.setVisibility(View.VISIBLE);
            } else {
                llEmptyFriends.setVisibility(View.GONE);
            }
        } else {
            tvFriendsHeader.setVisibility(View.VISIBLE);
            tvFriendsHeader.setText("Tất cả bạn bè (" + friendList.size() + ")");
            rvFriends.setVisibility(View.VISIBLE);
            llEmptyFriends.setVisibility(View.GONE);
        }
    }
}
