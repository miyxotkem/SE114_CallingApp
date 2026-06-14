package com.example.se114_callingsystem.features.friend.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.databinding.FragmentFriendProfileBinding;
import com.example.se114_callingsystem.core.model.User;
import com.example.se114_callingsystem.core.service.MessageNotificationService;
import com.example.se114_callingsystem.features.friend.viewmodel.ProfileViewModel;
import com.google.firebase.auth.FirebaseUser;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";

    private FragmentFriendProfileBinding binding;
    private ProfileViewModel viewModel;
    private FirebaseUser currentUser;
    private String displayUid;
    private boolean isOwnProfile;

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
        binding = FragmentFriendProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        currentUser = viewModel.getCurrentUser();

        // Check if passing a specific UID to view another user's profile
        if (getArguments() != null) {
            displayUid = getArguments().getString("USER_ID");
        }
        
        if (displayUid == null && currentUser != null) {
            displayUid = currentUser.getUid();
        }
        
        isOwnProfile = currentUser != null && displayUid.equals(currentUser.getUid());

        initViews();
        setupObservers();
        loadUserProfile();
    }

    private void initViews() {
        if (binding == null) return;

        if (isOwnProfile) {
            binding.tvFriendActionsHeader.setVisibility(View.GONE);
            binding.cardSendMessage.setVisibility(View.GONE);

            binding.tvSubscriptionHeader.setVisibility(View.VISIBLE);
            binding.cardUpgradePlan.setVisibility(View.VISIBLE);
            binding.btnUpgradePlan.setOnClickListener(v -> {
                Navigation.findNavController(v).navigate(R.id.action_profile_to_upgrade_plan);
            });

            binding.tvUserSettingsHeader.setVisibility(View.VISIBLE);
            binding.cardEditProfile.setVisibility(View.VISIBLE);
            binding.tvAccountActionsHeader.setVisibility(View.VISIBLE);
            binding.cardLogout.setVisibility(View.VISIBLE);

            View.OnClickListener toEditProfile = v -> {
                Navigation.findNavController(v).navigate(R.id.action_profile_to_edit_profile);
            };
            binding.btnEditProfile.setOnClickListener(toEditProfile);
            
            binding.switchDarkModeProfile.setChecked(com.example.se114_callingsystem.core.util.ThemeHelper.isDarkMode(requireContext()));
            binding.switchDarkModeProfile.setOnCheckedChangeListener((buttonView, isChecked) -> {
                com.example.se114_callingsystem.core.util.ThemeHelper.setDarkMode(requireContext(), isChecked);
            });
            
            binding.btnLogout.setOnClickListener(v -> {
                com.example.se114_callingsystem.core.util.BottomSheetUtils.showConfirmDialog(
                        requireContext(),
                        "Đăng xuất",
                        "Bạn có chắc chắn muốn đăng xuất không?",
                        "Đăng xuất",
                        "#F23F42",
                        () -> {
                            if (getContext() != null) {
                                requireContext().stopService(new Intent(getContext(), MessageNotificationService.class));
                            }
                            if (getActivity() != null) {
                                com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(requireActivity(), 
                                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).signOut();
                            }
                            viewModel.signOut();
                        }
                );
            });
        } else {
            binding.tvSubscriptionHeader.setVisibility(View.GONE);
            binding.cardUpgradePlan.setVisibility(View.GONE);

            binding.tvUserSettingsHeader.setVisibility(View.GONE);
            binding.cardEditProfile.setVisibility(View.GONE);
            binding.tvAccountActionsHeader.setVisibility(View.GONE);
            binding.cardLogout.setVisibility(View.GONE);

            binding.tvFriendActionsHeader.setVisibility(View.VISIBLE);
            binding.cardSendMessage.setVisibility(View.VISIBLE);

            binding.btnSendMessage.setOnClickListener(v -> {
                if (currentUser == null || displayUid == null || displayUid.isEmpty()) return;
                
                String myUid = currentUser.getUid();
                String friendUid = displayUid;

                String dmRoomId;
                if (myUid.compareTo(friendUid) < 0) {
                    dmRoomId = "dm_" + myUid + "_" + friendUid;
                } else {
                    dmRoomId = "dm_" + friendUid + "_" + myUid;
                }

                String displayName = binding.tvUsername.getText().toString();

                Bundle args = new Bundle();
                args.putString("CHAT_ID", dmRoomId);
                args.putString("CHAT_NAME", displayName);
                args.putString("SERVER_ID", null);
                args.putString("SERVER_COLOR", "#5865F2");

                Navigation.findNavController(v).navigate(R.id.action_profile_to_chat_detail, args);
            });
        }
    }

    private void setupObservers() {
        viewModel.getUserProfile().observe(getViewLifecycleOwner(), user -> {
            if (user == null || binding == null || getContext() == null) return;

            String displayName = user.getUsername() != null && !user.getUsername().isEmpty() ? user.getUsername() : "User";
            String plan = user.getPlan();
            if (plan == null) plan = "Basic";

            binding.tvUsername.setText(displayName);

            if ("Pro".equals(plan)) {
                binding.tvAvatarBadge.setText("✨");
                binding.tvAvatarBadge.setVisibility(View.VISIBLE);
                binding.viewBadgeRing.setVisibility(View.VISIBLE);
            } else if ("Standard".equals(plan)) {
                binding.tvAvatarBadge.setText("⭐");
                binding.tvAvatarBadge.setVisibility(View.VISIBLE);
                binding.viewBadgeRing.setVisibility(View.VISIBLE);
            } else {
                binding.tvAvatarBadge.setVisibility(View.GONE);
                binding.viewBadgeRing.setVisibility(View.GONE);
            }
            
            // Apply gold border to ivAvatar
            if (binding.ivAvatar instanceof com.google.android.material.imageview.ShapeableImageView) {
                com.google.android.material.imageview.ShapeableImageView siv = (com.google.android.material.imageview.ShapeableImageView) binding.ivAvatar;
                float density = getResources().getDisplayMetrics().density;
                if ("Pro".equals(plan)) {
                    siv.setStrokeWidth(3f * density);
                    siv.setStrokeColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFD700")));
                    int padding = (int)(3 * density);
                    siv.setPadding(padding, padding, padding, padding);
                } else {
                    siv.setStrokeWidth(6f * density);
                    siv.setStrokeColor(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.discord_dark_deep)));
                    int padding = (int)(2 * density);
                    siv.setPadding(padding, padding, padding, padding);
                }
            }
            
            // Set online/offline status text & indicator color
            String status = user.getStatus();
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

            binding.tvUserStatus.setText(displayText);
            binding.tvUserStatus.setTextColor(getResources().getColor(colorRes));
            binding.viewStatusIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(colorRes)));

            if (user.getBio() != null && !user.getBio().isEmpty()) {
                binding.tvBio.setText(user.getBio());
            } else {
                binding.tvBio.setText("This user has no bio.");
            }

            if (user.getWorkplace() != null && !user.getWorkplace().isEmpty()) {
                binding.tvWorkplace.setText(user.getWorkplace());
            } else {
                binding.tvWorkplace.setText("Chưa cập nhật nơi làm việc/học tập");
            }

            if (user.getHobbies() != null && !user.getHobbies().isEmpty()) {
                binding.tvHobbies.setText(user.getHobbies());
            } else {
                binding.tvHobbies.setText("Trống");
            }

            if (user.getDob() != null && !user.getDob().isEmpty()) {
                binding.tvDob.setText(user.getDob());
            } else {
                binding.tvDob.setText("Chưa cập nhật ngày sinh");
            }

            try {
                if (user.getProfilePic() != null && !user.getProfilePic().isEmpty()) {
                    Glide.with(this).load(user.getProfilePic()).placeholder(R.mipmap.ic_launcher).into(binding.ivAvatar);
                } else {
                    binding.ivAvatar.setImageResource(R.drawable.ic_user);
                }
                if (user.getCoverPic() != null && !user.getCoverPic().isEmpty()) {
                    Glide.with(this).load(user.getCoverPic()).into(binding.ivCoverPhoto);
                } else {
                    binding.ivCoverPhoto.setImageResource(0);
                    binding.ivCoverPhoto.setBackgroundColor(getResources().getColor(R.color.discord_blurple));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        viewModel.getStatusMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.equals("SUCCESS") && !message.equals("OFFLINE_SUCCESS")) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                }
                viewModel.resetStatus();
            }
        });

        viewModel.getIsSignedOut().observe(getViewLifecycleOwner(), signedOut -> {
            if (signedOut != null && signedOut && getView() != null) {
                // Navigate back to Login Fragment and clear the backstack
                Navigation.findNavController(getView()).navigate(R.id.action_profile_to_login);
                com.example.se114_callingsystem.core.util.ThemeHelper.applyTheme(requireContext());
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserProfile(); // Reload data in case it was edited
        if (isOwnProfile && binding != null && getContext() != null) {
            android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE);
            String currentPlan = prefs.getString("current_plan", "Basic");
            binding.tvCurrentPlan.setText("Current: " + currentPlan + " Plan");
        }
    }

    private void loadUserProfile() {
        if (displayUid == null) return;
        viewModel.loadProfile(displayUid);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
