package com.example.se114_callingsystem.Activity.Page;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.se114_callingsystem.Model.User;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.Util.ThemeHelper;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private EditText etUsername, etBio, etWorkplace, etHobbies, etDob;
    private ImageView ivEditAvatar, ivEditCoverPhoto;
    private MaterialButton btnSaveProfile;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    
    private android.net.Uri avatarUri = null;
    private android.net.Uri coverUri = null;
    private String currentAvatarUrl = "";
    private String currentCoverUrl = "";
    private boolean isPickingAvatar = false;

    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> imagePickerLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    android.net.Uri selectedImageUri = result.getData().getData();
                    if (isPickingAvatar) {
                        avatarUri = selectedImageUri;
                        com.bumptech.glide.Glide.with(this).load(avatarUri).into(ivEditAvatar);
                    } else {
                        coverUri = selectedImageUri;
                        com.bumptech.glide.Glide.with(this).load(coverUri).into(ivEditCoverPhoto);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        etUsername = findViewById(R.id.etUsername);
        etBio = findViewById(R.id.etBio);
        etWorkplace = findViewById(R.id.etWorkplace);
        etHobbies = findViewById(R.id.etHobbies);
        etDob = findViewById(R.id.etDob);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        
        ivEditAvatar = findViewById(R.id.ivEditAvatar);
        ivEditCoverPhoto = findViewById(R.id.ivEditCoverPhoto);

        ivEditAvatar.setOnClickListener(v -> pickImage(true));
        ivEditCoverPhoto.setOnClickListener(v -> pickImage(false));

        loadCurrentData();

        btnSaveProfile.setOnClickListener(v -> saveProfile());
    }
    
    private void pickImage(boolean isAvatar) {
        isPickingAvatar = isAvatar;
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void loadCurrentData() {
        if (currentUser == null) return;
        
        db.collection("users").document(currentUser.getUid()).get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
                        etUsername.setText(user.getUsername());
                        etBio.setText(user.getBio());
                        etWorkplace.setText(user.getWorkplace());
                        etHobbies.setText(user.getHobbies());
                        etDob.setText(user.getDob());
                        
                        currentAvatarUrl = user.getProfilePic() != null ? user.getProfilePic() : "";
                        currentCoverUrl = user.getCoverPic() != null ? user.getCoverPic() : "";
                        
                        if (!currentAvatarUrl.isEmpty()) {
                            com.bumptech.glide.Glide.with(this).load(currentAvatarUrl).placeholder(R.mipmap.ic_launcher).into(ivEditAvatar);
                        }
                        if (!currentCoverUrl.isEmpty()) {
                            com.bumptech.glide.Glide.with(this).load(currentCoverUrl).into(ivEditCoverPhoto);
                        }
                    }
                }
            });
    }

    private void saveProfile() {
        if (currentUser == null) return;
        btnSaveProfile.setEnabled(false);

        uploadImage(avatarUri, "avatar_" + currentUser.getUid(), avatarUrl -> {
            uploadImage(coverUri, "cover_" + currentUser.getUid(), coverUrl -> {
                Map<String, Object> updates = new HashMap<>();
                updates.put("username", etUsername.getText().toString().trim());
                updates.put("bio", etBio.getText().toString().trim());
                updates.put("workplace", etWorkplace.getText().toString().trim());
                updates.put("hobbies", etHobbies.getText().toString().trim());
                updates.put("dob", etDob.getText().toString().trim());
                
                if (avatarUrl != null) updates.put("profilePic", avatarUrl);
                if (coverUrl != null) updates.put("coverPic", coverUrl);

                db.collection("users").document(currentUser.getUid())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(EditProfileActivity.this, "Đã lưu thay đổi", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(EditProfileActivity.this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        btnSaveProfile.setEnabled(true);
                    });
            });
        });
    }

    private void uploadImage(android.net.Uri uri, String fileName, java.util.function.Consumer<String> onComplete) {
        if (uri == null) {
            onComplete.accept(null);
            return;
        }
        
        com.google.firebase.storage.StorageReference ref = com.example.se114_callingsystem.Model.Firebase.getStorageRef().child("profile_images/" + fileName);
        ref.putFile(uri).addOnSuccessListener(taskSnapshot -> {
            ref.getDownloadUrl().addOnSuccessListener(downloadUrl -> {
                onComplete.accept(downloadUrl.toString());
            }).addOnFailureListener(e -> onComplete.accept(null));
        }).addOnFailureListener(e -> onComplete.accept(null));
    }
}
