package com.example.se114_callingsystem.features.friend;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.databinding.FragmentFriendEditProfileBinding;
import com.example.se114_callingsystem.core.model.Firebase;
import com.example.se114_callingsystem.core.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import java.util.HashMap;
import java.util.Map;

public class EditProfileFragment extends Fragment {

    private static final String TAG = "EditProfileFragment";

    private FragmentFriendEditProfileBinding binding;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    
    private Uri avatarUri = null;
    private Uri coverUri = null;
    private String currentAvatarUrl = "";
    private String currentCoverUrl = "";
    private boolean isPickingAvatar = false;

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
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        db = FirebaseFirestore.getInstance();
        initCloudinary();
    }

    private void initCloudinary() {
        Map config = new HashMap();
        config.put("cloud_name", "dxoukp0yb");
        config.put("api_key", "359217744855482");
        config.put("api_secret", "eTG0UvW_hdsHm4hl0r2XJCvidR0");
        try {
            MediaManager.init(requireContext(), config);
        } catch (IllegalStateException e) {}
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

        binding.btnBack.setOnClickListener(v -> {
            Navigation.findNavController(v).popBackStack();
        });

        binding.ivEditAvatar.setOnClickListener(v -> pickImage(true));
        binding.ivEditCoverPhoto.setOnClickListener(v -> pickImage(false));

        loadCurrentData();

        binding.btnSaveProfile.setOnClickListener(v -> saveProfile());
    }

    private void pickImage(boolean isAvatar) {
        isPickingAvatar = isAvatar;
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void loadCurrentData() {
        if (currentUser == null || binding == null) return;
        
        db.collection("users").document(currentUser.getUid()).get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists() && binding != null && getContext() != null) {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
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
                    }
                }
            });
    }

    private void saveProfile() {
        if (currentUser == null || binding == null) return;
        binding.btnSaveProfile.setEnabled(false);

        android.app.ProgressDialog pd = new android.app.ProgressDialog(requireContext());
        pd.setMessage("Đang lưu thay đổi...");
        pd.setCancelable(false);
        pd.show();

        uploadImage(avatarUri, "avatar_" + currentUser.getUid(), avatarUrl -> {
            uploadImage(coverUri, "cover_" + currentUser.getUid(), coverUrl -> {
                if (binding == null) {
                    pd.dismiss();
                    return;
                }
                
                Map<String, Object> updates = new HashMap<>();
                updates.put("username", binding.etUsername.getText().toString().trim());
                updates.put("bio", binding.etBio.getText().toString().trim());
                updates.put("workplace", binding.etWorkplace.getText().toString().trim());
                updates.put("hobbies", binding.etHobbies.getText().toString().trim());
                updates.put("dob", binding.etDob.getText().toString().trim());
                
                if (avatarUrl != null) updates.put("profilePic", avatarUrl);
                if (coverUrl != null) updates.put("coverPic", coverUrl);

                db.collection("users").document(currentUser.getUid())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        pd.dismiss();
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Changes saved successfully.", Toast.LENGTH_SHORT).show();
                        }
                        if (getView() != null) {
                            Navigation.findNavController(getView()).popBackStack();
                        }
                    })
                    .addOnFailureListener(e -> {
                        pd.dismiss();
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                        if (binding != null) {
                            binding.btnSaveProfile.setEnabled(true);
                        }
                    });
            });
        });
    }

    private void uploadImage(Uri uri, String fileName, java.util.function.Consumer<String> onComplete) {
        if (uri == null) {
            onComplete.accept(null);
            return;
        }
        
        MediaManager.get().upload(uri).option("resource_type", "auto").callback(new UploadCallback() {
            @Override public void onStart(String requestId) {}
            @Override public void onProgress(String requestId, long bytes, long totalBytes) {}
            @Override public void onSuccess(String requestId, Map resultData) {
                String secureUrl = (String) resultData.get("secure_url");
                onComplete.accept(secureUrl);
            }
            @Override public void onError(String requestId, ErrorInfo error) {
                Log.e(TAG, "Cloudinary upload error: " + error.getDescription());
                onComplete.accept(null);
            }
            @Override public void onReschedule(String requestId, ErrorInfo error) {}
        }).dispatch();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

