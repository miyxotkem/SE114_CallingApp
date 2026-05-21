package com.example.se114_callingsystem.Activity.Page;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.Model.User;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.Util.ThemeHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    private ImageView ivCoverPhoto;
    private ShapeableImageView ivAvatar;
    private TextView tvUsername, tvBio, tvWorkplace, tvHobbies, tvDob;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String displayUid;
    private boolean isOwnProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // Check if passing a specific UID to view another user's profile
        displayUid = getIntent().getStringExtra("USER_ID");
        if (displayUid == null && currentUser != null) {
            displayUid = currentUser.getUid();
        }
        
        isOwnProfile = currentUser != null && displayUid.equals(currentUser.getUid());

        initViews();
        loadUserProfile();
    }

    private void initViews() {
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        ivCoverPhoto = findViewById(R.id.ivCoverPhoto);
        ivAvatar = findViewById(R.id.ivAvatar);
        tvUsername = findViewById(R.id.tvUsername);
        tvBio = findViewById(R.id.tvBio);
        tvWorkplace = findViewById(R.id.tvWorkplace);
        tvHobbies = findViewById(R.id.tvHobbies);
        tvDob = findViewById(R.id.tvDob);

        MaterialButton btnEditProfile = findViewById(R.id.btnEditProfile);
        
        if (isOwnProfile) {
            btnEditProfile.setVisibility(View.VISIBLE);
            btnEditProfile.setOnClickListener(v -> {
                startActivity(new Intent(ProfileActivity.this, EditProfileActivity.class));
            });
            
            MaterialButton btnLogout = findViewById(R.id.btnLogout);
            btnLogout.setVisibility(View.VISIBLE);
            btnLogout.setOnClickListener(v -> {
                stopService(new Intent(this, com.example.se114_callingsystem.Activity.Page.MessageNotificationService.class));
                FirebaseAuth.getInstance().signOut();
                com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).signOut();
                Intent intent = new Intent(this, com.example.se114_callingsystem.Activity.Page.LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        } else {
            btnEditProfile.setVisibility(View.GONE);
            findViewById(R.id.btnLogout).setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserProfile(); // Reload data in case it was edited
    }

    private void loadUserProfile() {
        if (displayUid == null) return;

        db.collection("users").document(displayUid).get()
            .addOnSuccessListener(documentSnapshot -> {
                if (isDestroyed() || isFinishing()) return;
                
                if (documentSnapshot.exists()) {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
                        tvUsername.setText(user.getUsername() != null && !user.getUsername().isEmpty() ? user.getUsername() : "User");
                        
                        if (user.getBio() != null && !user.getBio().isEmpty()) {
                            tvBio.setText(user.getBio());
                        } else {
                            tvBio.setText("This user has no bio.");
                        }

                        if (user.getWorkplace() != null && !user.getWorkplace().isEmpty()) {
                            tvWorkplace.setText(user.getWorkplace());
                        } else {
                            tvWorkplace.setText("Chưa cập nhật nơi làm việc/học tập");
                        }

                        if (user.getHobbies() != null && !user.getHobbies().isEmpty()) {
                            tvHobbies.setText("Sở thích: " + user.getHobbies());
                        } else {
                            tvHobbies.setText("Sở thích: Trống");
                        }

                        if (user.getDob() != null && !user.getDob().isEmpty()) {
                            tvDob.setText(user.getDob());
                        } else {
                            tvDob.setText("Chưa cập nhật ngày sinh");
                        }

                        try {
                            // Load avatar if available
                            if (user.getProfilePic() != null && !user.getProfilePic().isEmpty()) {
                                Glide.with(ProfileActivity.this).load(user.getProfilePic()).placeholder(R.mipmap.ic_launcher).into(ivAvatar);
                            }
                            
                            // Load cover if available
                            if (user.getCoverPic() != null && !user.getCoverPic().isEmpty()) {
                                Glide.with(ProfileActivity.this).load(user.getCoverPic()).into(ivCoverPhoto);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Không thể tải thông tin: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
}
