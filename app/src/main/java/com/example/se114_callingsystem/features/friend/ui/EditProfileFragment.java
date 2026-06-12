package com.example.se114_callingsystem.features.friend.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.databinding.FragmentFriendEditProfileBinding;
import com.example.se114_callingsystem.features.friend.viewmodel.ProfileViewModel;
import com.google.firebase.auth.FirebaseUser;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.HashMap;
import java.util.Map;

@AndroidEntryPoint
public class EditProfileFragment extends Fragment {

    private static final String TAG = "EditProfileFragment";

    private FragmentFriendEditProfileBinding binding;
    private ProfileViewModel viewModel;
    private FirebaseUser currentUser;
    
    private Uri avatarUri = null;
    private Uri coverUri = null;
    private String currentAvatarUrl = "";
    private String currentCoverUrl = "";
    private boolean isPickingAvatar = false;
    private android.app.ProgressDialog progressDialog;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null && binding != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (isPickingAvatar) {
                        avatarUri = selectedImageUri;
                        Glide.with(this).load(avatarUri).into(binding.ivEditAvatar);
                    } else {
                        coverUri = selectedImageUri;
                        Glide.with(this).load(coverUri).into(binding.ivEditCoverPhoto);
                    }
                }
            }
    );

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFriendEditProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        currentUser = viewModel.getCurrentUser();

        binding.btnBack.setOnClickListener(v -> {
            Navigation.findNavController(v).popBackStack();
        });

        binding.ivEditAvatar.setOnClickListener(v -> pickImage(true));
        binding.ivEditCoverPhoto.setOnClickListener(v -> pickImage(false));

        binding.btnSaveProfile.setOnClickListener(v -> saveProfile());

        setupProgressDialog();
        setupObservers();
        loadCurrentData();
    }

    private void setupProgressDialog() {
        progressDialog = new android.app.ProgressDialog(requireContext());
        progressDialog.setMessage("Đang lưu thay đổi...");
        progressDialog.setCancelable(false);
    }

    private void setupObservers() {
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (binding == null) return;
            if (isLoading) {
                binding.btnSaveProfile.setEnabled(false);
                if (!progressDialog.isShowing()) {
                    progressDialog.show();
                }
            } else {
                binding.btnSaveProfile.setEnabled(true);
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            }
        });

        viewModel.getUserProfile().observe(getViewLifecycleOwner(), user -> {
            if (user == null || binding == null || getContext() == null) return;

            binding.etUsername.setText(user.getUsername());
            binding.etBio.setText(user.getBio());
            binding.etWorkplace.setText(user.getWorkplace());
            binding.etHobbies.setText(user.getHobbies());
            binding.etDob.setText(user.getDob());
            
            currentAvatarUrl = user.getProfilePic() != null ? user.getProfilePic() : "";
            currentCoverUrl = user.getCoverPic() != null ? user.getCoverPic() : "";
            
            if (!currentAvatarUrl.isEmpty()) {
                Glide.with(this).load(currentAvatarUrl).placeholder(R.mipmap.ic_launcher).into(binding.ivEditAvatar);
            } else {
                binding.ivEditAvatar.setImageResource(R.drawable.ic_user);
            }
            if (!currentCoverUrl.isEmpty()) {
                Glide.with(this).load(currentCoverUrl).into(binding.ivEditCoverPhoto);
            } else {
                binding.ivEditCoverPhoto.setImageResource(0);
                binding.ivEditCoverPhoto.setBackgroundColor(getResources().getColor(R.color.discord_blurple));
            }
        });

        viewModel.getStatusMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null) return;

            if ("SUCCESS".equals(message)) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Changes saved successfully.", Toast.LENGTH_SHORT).show();
                }
                if (getView() != null) {
                    Navigation.findNavController(getView()).popBackStack();
                }
            } else if ("OFFLINE_SUCCESS".equals(message)) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Đã lưu thay đổi ngoại tuyến. Sẽ đồng bộ khi có mạng.", Toast.LENGTH_SHORT).show();
                }
                if (getView() != null) {
                    Navigation.findNavController(getView()).popBackStack();
                }
            } else {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Error: " + message, Toast.LENGTH_SHORT).show();
                }
            }
            viewModel.resetStatus();
        });
    }

    private void pickImage(boolean isAvatar) {
        if (!isAvatar) {
            android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE);
            String currentPlan = prefs.getString("current_plan", "Basic");
            if ("Basic".equals(currentPlan)) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Premium Feature")
                    .setMessage("Changing your cover photo requires the Standard plan. Upgrade now to unlock this feature!")
                    .setPositiveButton("Upgrade", (dialog, which) -> {
                        Navigation.findNavController(binding.getRoot()).navigate(R.id.action_edit_profile_to_upgrade_plan);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                return;
            }
        }
        
        isPickingAvatar = isAvatar;
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void loadCurrentData() {
        if (currentUser == null) return;
        viewModel.loadProfile(currentUser.getUid());
    }

    private void saveProfile() {
        if (currentUser == null || binding == null) return;

        // Check network connection if picking new image
        if ((avatarUri != null || coverUri != null) && 
                !com.example.se114_callingsystem.core.util.NetworkMonitor.isNetworkAvailable(requireContext())) {
            Toast.makeText(getContext(), "Không có kết nối mạng. Không thể tải lên hình ảnh mới.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("username", binding.etUsername.getText().toString().trim());
        updates.put("bio", binding.etBio.getText().toString().trim());
        updates.put("workplace", binding.etWorkplace.getText().toString().trim());
        updates.put("hobbies", binding.etHobbies.getText().toString().trim());
        updates.put("dob", binding.etDob.getText().toString().trim());

        if (!com.example.se114_callingsystem.core.util.NetworkMonitor.isNetworkAvailable(requireContext())) {
            viewModel.saveProfileOfflineOnly(currentUser.getUid(), updates);
        } else {
            viewModel.saveProfile(currentUser.getUid(), updates, avatarUri, coverUri);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
