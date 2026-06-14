package com.example.se114_callingsystem.features.home.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.se114_callingsystem.databinding.FragmentHomeBinding;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.User;
import com.example.se114_callingsystem.core.service.MessageNotificationService;
import com.example.se114_callingsystem.features.home.viewmodel.HomeViewModel;
import com.google.firebase.auth.FirebaseAuth;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.List;

@AndroidEntryPoint
public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private HomeDMAdapter dmAdapter;
    private final List<User> friendList = new ArrayList<>();
    private final List<User> originalFriendList = new ArrayList<>();
    private String currentSearchQuery = "";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        com.google.android.material.transition.MaterialSharedAxis enterTransition = 
            new com.google.android.material.transition.MaterialSharedAxis(
                com.google.android.material.transition.MaterialSharedAxis.X, /* forward= */ true);
        com.google.android.material.transition.MaterialSharedAxis returnTransition = 
            new com.google.android.material.transition.MaterialSharedAxis(
                com.google.android.material.transition.MaterialSharedAxis.X, /* forward= */ false);
        
        setEnterTransition(enterTransition);
        setReturnTransition(returnTransition);
        
        com.google.android.material.transition.MaterialSharedAxis exitTransition = 
            new com.google.android.material.transition.MaterialSharedAxis(
                com.google.android.material.transition.MaterialSharedAxis.X, /* forward= */ true);
        com.google.android.material.transition.MaterialSharedAxis reenterTransition = 
            new com.google.android.material.transition.MaterialSharedAxis(
                com.google.android.material.transition.MaterialSharedAxis.X, /* forward= */ false);
        
        setExitTransition(exitTransition);
        setReenterTransition(reenterTransition);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        checkNotificationPermission();
        startMessageNotificationService();

        binding.btnManageFriends.setOnClickListener(v -> {
            androidx.navigation.Navigation.findNavController(v).navigate(R.id.nav_friend_manage);
        });

        binding.btnStatus.setOnClickListener(v -> showStatusDialog());

        // Setup Direct Messages list
        binding.rvDirectMessages.setLayoutManager(new LinearLayoutManager(getContext()));
        dmAdapter = new HomeDMAdapter(friendList, viewModel, this::onFriendClick);
        binding.rvDirectMessages.setAdapter(dmAdapter);
        setupDMDrapAndDrop();

        binding.etSearchDMs.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performFilter(s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        binding.btnClearSearch.setOnClickListener(v -> {
            binding.etSearchDMs.setText("");
        });

        setupObservers();
        viewModel.initHome();
    }

    private void setupObservers() {
        viewModel.getFriendList().observe(getViewLifecycleOwner(), list -> {
            if (binding == null || list == null) return;
            originalFriendList.clear();
            originalFriendList.addAll(list);
            performFilter(currentSearchQuery);
        });

        viewModel.getUnreadCounts().observe(getViewLifecycleOwner(), counts -> {
            if (binding == null || counts == null) return;
            if (dmAdapter != null) {
                dmAdapter.setUnreadCounts(counts);
            }
        });

        viewModel.getUserStatus().observe(getViewLifecycleOwner(), status -> {
            if (binding == null || status == null || getContext() == null) return;

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
        });

        viewModel.getOperationStatus().observe(getViewLifecycleOwner(), status -> {
            if (status == null || getContext() == null) return;

            if (status.startsWith("JOIN_")) {
                String result = status.substring(5);
                switch (result) {
                    case "SUCCESS":
                        Toast.makeText(getContext(), "Tham gia server thành công!", Toast.LENGTH_SHORT).show();
                        break;
                    case "ALREADY_IN_SERVER":
                        Toast.makeText(getContext(), "Bạn đã ở trong server này rồi!", Toast.LENGTH_SHORT).show();
                        break;
                    case "INVALID_CODE":
                        Toast.makeText(getContext(), "Mã mời không hợp lệ hoặc Server không tồn tại.", Toast.LENGTH_SHORT).show();
                        break;
                }
            } else {
                Toast.makeText(getContext(), status, Toast.LENGTH_SHORT).show();
            }
            viewModel.resetStatus();
        });
    }

    private void setupDMDrapAndDrop() {
        androidx.recyclerview.widget.ItemTouchHelper touchHelper = new androidx.recyclerview.widget.ItemTouchHelper(new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                androidx.recyclerview.widget.ItemTouchHelper.UP | androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0) {
            
            @Override
            public int getMovementFlags(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder) {
                int position = viewHolder.getAdapterPosition();
                if (position >= 0 && position < friendList.size()) {
                    User user = friendList.get(position);
                    if (viewModel.isUserPinned(user.getUserId())) {
                        return makeMovementFlags(androidx.recyclerview.widget.ItemTouchHelper.UP | androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0);
                    }
                }
                return makeMovementFlags(0, 0);
            }

            @Override
            public boolean onMove(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder, @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                
                if (toPosition >= 0 && toPosition < friendList.size()) {
                    User targetUser = friendList.get(toPosition);
                    // Only allow moving within pinned items
                    if (!viewModel.isUserPinned(targetUser.getUserId())) {
                        return false;
                    }
                }

                java.util.Collections.swap(friendList, fromPosition, toPosition);
                dmAdapter.notifyItemMoved(fromPosition, toPosition);
                return true;
            }

            @Override
            public void clearView(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewModel.updatePinnedOrder(friendList);
            }

            @Override
            public void onSwiped(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder, int direction) {}
        });
        touchHelper.attachToRecyclerView(binding.rvDirectMessages);
    }
    
    private void showJoinServerDialog() {
        if (getContext() == null) return;
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
                viewModel.joinServer(inviteCode);
                dialog.dismiss();
            } else {
                edtInviteCode.setError("Vui lòng nhập mã mời");
            }
        });

        dialog.show();
    }

    private void showStatusDialog() {
        if (getContext() == null) return;
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_status, null);
        bottomSheetDialog.setContentView(view);
        
        View parent = (View) view.getParent();
        if (parent != null) {
            parent.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }

        view.findViewById(R.id.btnStatusOnline).setOnClickListener(v -> {
            requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE).edit().putString("manual_status", "online").apply();
            viewModel.updateUserStatus("online");
            bottomSheetDialog.dismiss();
        });
        view.findViewById(R.id.btnStatusIdle).setOnClickListener(v -> {
            requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE).edit().putString("manual_status", "idle").apply();
            viewModel.updateUserStatus("idle");
            bottomSheetDialog.dismiss();
        });
        view.findViewById(R.id.btnStatusDnd).setOnClickListener(v -> {
            requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE).edit().putString("manual_status", "dnd").apply();
            viewModel.updateUserStatus("dnd");
            bottomSheetDialog.dismiss();
        });
        view.findViewById(R.id.btnStatusInvisible).setOnClickListener(v -> {
            requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE).edit().putString("manual_status", "invisible").apply();
            viewModel.updateUserStatus("invisible");
            bottomSheetDialog.dismiss();
        });
        view.findViewById(R.id.btnStatusCustom).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showCustomStatusDialog();
        });

        bottomSheetDialog.show();
    }

    private void showCustomStatusDialog() {
        if (getContext() == null) return;
        android.app.Dialog dialog = new android.app.Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_custom_status);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        
        android.widget.EditText edtStatus = dialog.findViewById(R.id.edtCustomStatus);
        android.view.View btnCancel = dialog.findViewById(R.id.btnCancel);
        android.view.View btnSave = dialog.findViewById(R.id.btnSave);
        
        android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE);
        String currentPlan = prefs.getString("current_plan", "Basic");
        
        if ("Basic".equals(currentPlan)) {
            edtStatus.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(30) });
            edtStatus.setHint("What's on your mind? (Max 30 chars)");
        } else {
            edtStatus.setHint("What's on your mind? (Unlimited & Emojis supported!)");
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnSave.setOnClickListener(v -> {
            String s = edtStatus.getText().toString().trim();
            if (!s.isEmpty()) {
                requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE).edit().putString("manual_status", s).apply();
                viewModel.updateUserStatus(s);
            }
            dialog.dismiss();
        });

        dialog.show();
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

    private void onFriendClick(User friend, View itemView) {
        if (viewModel.getCurrentUser() == null) return;
        String myUid = viewModel.getCurrentUser().getUid();
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

        String transitionName = "chat_transform_" + dmRoomId;
        itemView.setTransitionName(transitionName);
        args.putString("TRANSITION_NAME", transitionName);

        androidx.navigation.fragment.FragmentNavigator.Extras extras = 
            new androidx.navigation.fragment.FragmentNavigator.Extras.Builder()
                .addSharedElement(itemView, transitionName)
                .build();

        if (getView() != null) {
            androidx.navigation.Navigation.findNavController(getView()).navigate(
                R.id.action_home_to_chat_detail, args, null, extras);
        }
    }

    private void performFilter(String query) {
        currentSearchQuery = query;
        friendList.clear();
        if (query == null || query.trim().isEmpty()) {
            friendList.addAll(originalFriendList);
            if (binding != null) {
                binding.btnClearSearch.setVisibility(View.GONE);
                binding.tvNoDMsText.setText("No direct messages yet");
            }
        } else {
            String lowerQuery = query.toLowerCase().trim();
            if (binding != null) binding.btnClearSearch.setVisibility(View.VISIBLE);

            if (viewModel != null && viewModel.getFriendMap() != null) {
                for (User u : viewModel.getFriendMap().values()) {
                    String displayName = u.getUsername();
                    if (displayName == null || displayName.trim().isEmpty()) {
                        displayName = u.getEmail();
                    }
                    if (displayName != null && displayName.toLowerCase().contains(lowerQuery)) {
                        friendList.add(u);
                    }
                }
            }

            friendList.sort((u1, u2) -> {
                boolean pin1 = viewModel != null && viewModel.isUserPinned(u1.getUserId());
                boolean pin2 = viewModel != null && viewModel.isUserPinned(u2.getUserId());
                if (pin1 && !pin2) return -1;
                if (!pin1 && pin2) return 1;

                String name1 = u1.getUsername() != null ? u1.getUsername() : "";
                String name2 = u2.getUsername() != null ? u2.getUsername() : "";
                return name1.compareToIgnoreCase(name2);
            });
        }

        if (dmAdapter != null) {
            dmAdapter.notifyDataSetChanged();
        }

        if (binding != null) {
            if (friendList.isEmpty()) {
                binding.layoutNoDMs.setVisibility(View.VISIBLE);
                binding.rvDirectMessages.setVisibility(View.GONE);
                binding.tvNoDMsText.setText("No friends or conversations match your search");
            } else {
                binding.layoutNoDMs.setVisibility(View.GONE);
                binding.rvDirectMessages.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
