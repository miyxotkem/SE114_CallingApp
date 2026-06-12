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
        dmAdapter = new HomeDMAdapter(friendList, this::onFriendClick);
        binding.rvDirectMessages.setAdapter(dmAdapter);

        setupObservers();
        viewModel.initHome();
    }

    private void setupObservers() {
        viewModel.getFriendList().observe(getViewLifecycleOwner(), list -> {
            if (binding == null || list == null) return;
            friendList.clear();
            friendList.addAll(list);
            dmAdapter.notifyDataSetChanged();

            if (friendList.isEmpty()) {
                binding.layoutNoDMs.setVisibility(View.VISIBLE);
                binding.rvDirectMessages.setVisibility(View.GONE);
            } else {
                binding.layoutNoDMs.setVisibility(View.GONE);
                binding.rvDirectMessages.setVisibility(View.VISIBLE);
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

        view.findViewById(R.id.btnStatusAuto).setOnClickListener(v -> {
            requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE).edit().putString("manual_status", "auto").apply();
            viewModel.updateUserStatus("online");
            bottomSheetDialog.dismiss();
        });
        view.findViewById(R.id.btnStatusDnd).setOnClickListener(v -> {
            requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE).edit().putString("manual_status", "dnd").apply();
            viewModel.updateUserStatus("dnd");
            bottomSheetDialog.dismiss();
        });
        view.findViewById(R.id.btnStatusSleeping).setOnClickListener(v -> {
            requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE).edit().putString("manual_status", "sleeping").apply();
            viewModel.updateUserStatus("sleeping");
            bottomSheetDialog.dismiss();
        });
        view.findViewById(R.id.btnStatusEating).setOnClickListener(v -> {
            requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE).edit().putString("manual_status", "eating").apply();
            viewModel.updateUserStatus("eating");
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
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("Enter custom status (e.g. Coding 💻)");
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Custom Status")
            .setView(input)
            .setPositiveButton("Set", (d, w) -> {
                String s = input.getText().toString().trim();
                if (!s.isEmpty()) {
                    requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE).edit().putString("manual_status", s).apply();
                    viewModel.updateUserStatus(s);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
