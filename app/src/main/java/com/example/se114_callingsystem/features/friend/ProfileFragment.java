package com.example.se114_callingsystem.features.friend;

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
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.databinding.FragmentFriendProfileBinding;
import com.example.se114_callingsystem.core.model.User;
import com.example.se114_callingsystem.core.service.MessageNotificationService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";

    private FragmentFriendProfileBinding binding;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String displayUid;
    private boolean isOwnProfile;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFriendProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // Check if passing a specific UID to view another user's profile
        if (getArguments() != null) {
            displayUid = getArguments().getString("USER_ID");
        }
        
        if (displayUid == null && currentUser != null) {
            displayUid = currentUser.getUid();
        }
        
        isOwnProfile = currentUser != null && displayUid.equals(currentUser.getUid());

        initViews();
        loadUserProfile();
    }

    private void initViews() {
        if (binding == null) return;

        if (isOwnProfile) {
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
                if (getContext() != null) {
                    requireContext().stopService(new Intent(getContext(), MessageNotificationService.class));
                }
                FirebaseAuth.getInstance().signOut();
                if (getActivity() != null) {
                    com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(requireActivity(), 
                        com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).signOut();
                }
                
                // Navigate back to Login Fragment and clear the backstack
                Navigation.findNavController(v).navigate(R.id.action_profile_to_login);
                com.example.se114_callingsystem.core.util.ThemeHelper.applyTheme(requireContext());
            });
        } else {
            binding.tvUserSettingsHeader.setVisibility(View.GONE);
            binding.cardEditProfile.setVisibility(View.GONE);
            binding.tvAccountActionsHeader.setVisibility(View.GONE);
            binding.cardLogout.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserProfile(); // Reload data in case it was edited
    }

    private void loadUserProfile() {
        if (displayUid == null || binding == null) return;

        db.collection("users").document(displayUid).get()
            .addOnSuccessListener(documentSnapshot -> {
                if (binding == null || getContext() == null) return;
                
                if (documentSnapshot.exists()) {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
                        binding.tvUsername.setText(user.getUsername() != null && !user.getUsername().isEmpty() ? user.getUsername() : "User");
                        
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
                    }
                }
            })
            .addOnFailureListener(e -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Không thể tải thông tin: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

