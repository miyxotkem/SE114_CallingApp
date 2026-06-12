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
import android.app.DatePickerDialog;
import java.util.Calendar;

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
    private String selectedGifUrl = null;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null && binding != null) {
                    Uri selectedImageUri = result.getData().getData();
                    
                    if (selectedImageUri != null) {
                        String mimeType = requireContext().getContentResolver().getType(selectedImageUri);
                        String path = selectedImageUri.getPath();
                        boolean isGif = (mimeType != null && mimeType.contains("gif")) || (path != null && path.toLowerCase().endsWith(".gif"));
                        
                        if (isGif && isPickingAvatar) {
                            android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE);
                            String currentPlan = prefs.getString("current_plan", "Basic");
                            if ("Basic".equals(currentPlan)) {
                                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Premium Feature")
                                    .setMessage("Animated avatars (GIFs) require the Standard or Pro plan. Upgrade now to unlock this feature!")
                                    .setPositiveButton("Upgrade", (dialog, which) -> {
                                        Navigation.findNavController(binding.getRoot()).navigate(R.id.action_edit_profile_to_upgrade_plan);
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                                return;
                            }
                        }
                    }

                    if (isPickingAvatar) {
                        avatarUri = selectedImageUri;
                        selectedGifUrl = null;
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

        binding.ivEditAvatar.setOnClickListener(v -> showAvatarOptionsDialog());
        binding.ivEditCoverPhoto.setOnClickListener(v -> pickImage(false));

        binding.etDob.setOnClickListener(v -> showDatePicker());

        binding.btnSaveProfile.setOnClickListener(v -> saveProfile());

        setupProgressDialog();
        setupObservers();
        loadCurrentData();
    }
    
    private void showAvatarOptionsDialog() {
        if (getContext() == null) return;
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_avatar_options, null);
        dialog.setContentView(view);
        
        View parent = (View) view.getParent();
        if (parent != null) {
            parent.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }

        view.findViewById(R.id.btnGallery).setOnClickListener(v -> {
            dialog.dismiss();
            pickImage(true);
        });

        view.findViewById(R.id.btnGif).setOnClickListener(v -> {
            dialog.dismiss();
            checkAndOpenGifPicker();
        });

        dialog.show();
    }
    
    private void checkAndOpenGifPicker() {
        android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE);
        String currentPlan = prefs.getString("current_plan", "Basic");
        if ("Basic".equals(currentPlan)) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Premium Feature")
                .setMessage("Animated avatars (GIFs) require the Standard or Pro plan. Upgrade now to unlock this feature!")
                .setPositiveButton("Upgrade", (d, which) -> {
                    Navigation.findNavController(binding.getRoot()).navigate(R.id.action_edit_profile_to_upgrade_plan);
                })
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }

        showGifPickerBottomSheet();
    }
    
    private void showGifPickerBottomSheet() {
        if (getContext() == null) return;
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_gif_picker, null);
        dialog.setContentView(view);
        
        View parent = (View) view.getParent();
        if (parent != null) {
            parent.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }

        androidx.recyclerview.widget.RecyclerView rvGifs = view.findViewById(R.id.rvGifs);
        rvGifs.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(getContext(), 3));
        
        java.util.List<String> gifUrls = java.util.Arrays.asList(
            "https://media.tenor.com/7sH2Z4NOf4cAAAAC/discord-discord-logo.gif",
            "https://media.tenor.com/QeNhy2UoZ5cAAAAC/cat-jam.gif",
            "https://media.tenor.com/41I-iMCSlqsAAAAC/pepe-dance.gif",
            "https://media.tenor.com/P4zB0eLgBkkAAAAC/anime-dance.gif",
            "https://media.tenor.com/s5Eee1sXkU8AAAAC/nyan-cat.gif",
            "https://media.tenor.com/aKFaZBrZFAkAAAAC/rick-roll-rick-astley.gif",
            "https://media.tenor.com/uR1dD518-eQAAAAC/doge-dance.gif",
            "https://media.tenor.com/XqWU8Q64OgcAAAAC/hacker-typing.gif",
            "https://media.tenor.com/5u0vj2_0z-cAAAAC/pikachu-dance.gif"
        );

        rvGifs.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                android.widget.ImageView iv = new android.widget.ImageView(parent.getContext());
                int size = (parent.getResources().getDisplayMetrics().widthPixels - 64) / 3;
                androidx.recyclerview.widget.RecyclerView.LayoutParams params = new androidx.recyclerview.widget.RecyclerView.LayoutParams(size, size);
                params.setMargins(8, 8, 8, 8);
                
                com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(parent.getContext());
                card.setLayoutParams(params);
                card.setRadius(24f);
                card.setCardElevation(0);
                card.setStrokeWidth(0);
                card.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
                
                iv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                card.addView(iv);
                
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(card) {};
            }

            @Override
            public void onBindViewHolder(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                String url = gifUrls.get(position);
                android.widget.ImageView iv = (android.widget.ImageView) ((ViewGroup) holder.itemView).getChildAt(0);
                Glide.with(requireContext()).load(url).into(iv);
                holder.itemView.setOnClickListener(v -> {
                    selectedGifUrl = url;
                    avatarUri = null; // Clear local uri
                    Glide.with(EditProfileFragment.this).load(url).into(binding.ivEditAvatar);
                    dialog.dismiss();
                });
            }

            @Override
            public int getItemCount() { return gifUrls.size(); }
        });

        dialog.show();
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        String currentDob = binding.etDob.getText() != null ? binding.etDob.getText().toString() : "";
        if (!currentDob.isEmpty()) {
            try {
                String[] parts = currentDob.split("/");
                if (parts.length == 3) {
                    int day = Integer.parseInt(parts[0]);
                    int month = Integer.parseInt(parts[1]) - 1;
                    int year = Integer.parseInt(parts[2]);
                    calendar.set(year, month, day);
                }
            } catch (Exception e) {
                // Use default date
            }
        }
        
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(requireContext(), 
            (view, selectedYear, selectedMonth, selectedDay) -> {
                String date = String.format("%02d/%02d/%04d", selectedDay, selectedMonth + 1, selectedYear);
                binding.etDob.setText(date);
            }, year, month, day);
        datePickerDialog.show();
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
            
            String plan = user.getPlan();
            if (plan == null) plan = "Basic";
            if (binding.ivEditAvatar instanceof com.google.android.material.imageview.ShapeableImageView) {
                com.google.android.material.imageview.ShapeableImageView siv = (com.google.android.material.imageview.ShapeableImageView) binding.ivEditAvatar;
                float density = getResources().getDisplayMetrics().density;
                if ("Pro".equals(plan)) {
                    siv.setStrokeWidth(3f * density);
                    siv.setStrokeColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFD700")));
                    int padding = (int)(3 * density);
                    siv.setPadding(padding, padding, padding, padding);
                } else {
                    siv.setStrokeWidth(4f * density);
                    siv.setStrokeColor(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.discord_dark_deep)));
                    int padding = (int)(2 * density);
                    siv.setPadding(padding, padding, padding, padding);
                }
            }
            
            if ("Pro".equals(plan)) {
                if (binding.tvAvatarBadge != null) {
                    binding.tvAvatarBadge.setText("✨");
                    binding.tvAvatarBadge.setVisibility(View.VISIBLE);
                }
                if (binding.viewBadgeRing != null) {
                    binding.viewBadgeRing.setVisibility(View.VISIBLE);
                }
            } else if ("Standard".equals(plan)) {
                if (binding.tvAvatarBadge != null) {
                    binding.tvAvatarBadge.setText("⭐");
                    binding.tvAvatarBadge.setVisibility(View.VISIBLE);
                }
                if (binding.viewBadgeRing != null) {
                    binding.viewBadgeRing.setVisibility(View.VISIBLE);
                }
            } else {
                if (binding.tvAvatarBadge != null) binding.tvAvatarBadge.setVisibility(View.GONE);
                if (binding.viewBadgeRing != null) binding.viewBadgeRing.setVisibility(View.GONE);
            }

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
        
        if (selectedGifUrl != null) {
            updates.put("profilePic", selectedGifUrl);
        }

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
