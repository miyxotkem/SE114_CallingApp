package com.example.se114_callingsystem.features.auth;

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
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.databinding.FragmentAuthRegisterBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class RegisterFragment extends Fragment {

    private static final String TAG = "RegisterFragment";

    private FragmentAuthRegisterBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAuthRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        binding.btnRegister.setOnClickListener(v -> registerUser());
        binding.tvGoToLogin.setOnClickListener(v -> {
            Navigation.findNavController(v).popBackStack();
        });
    }

    private void registerUser() {
        if (binding == null) return;

        String username = binding.etUsername.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String confirmPassword = binding.etConfirmPassword.getText().toString().trim();

        if (username.isEmpty()) {
            binding.etUsername.setError("Username is required");
            binding.etUsername.requestFocus();
            return;
        }

        if (email.isEmpty()) {
            binding.etEmail.setError("Email is required");
            binding.etEmail.requestFocus();
            return;
        }

        if (password.isEmpty() || password.length() < 6) {
            binding.etPassword.setError("Password must be at least 6 characters");
            binding.etPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            binding.etConfirmPassword.setError("Passwords do not match");
            binding.etConfirmPassword.requestFocus();
            return;
        }

        binding.btnRegister.setEnabled(false);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity(), task -> {
                    if (binding == null) return;
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        saveUserToFirestore(user, username);
                    } else {
                        binding.btnRegister.setEnabled(true);
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        Toast.makeText(getContext(), "Registration failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToFirestore(FirebaseUser user, String username) {
        if (user == null) return;

        String uid = user.getUid();
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", uid);
        userData.put("email", user.getEmail());
        userData.put("username", username);
        userData.put("avatar", "");

        db.collection("users").document(uid).set(userData)
                .addOnSuccessListener(aVoid -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Registration successful.", Toast.LENGTH_SHORT).show();
                    }
                    goToHome();
                })
                .addOnFailureListener(e -> {
                    if (binding != null) {
                        binding.btnRegister.setEnabled(true);
                    }
                    Log.e(TAG, "Error saving user to Firestore", e);
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Failed to save user data.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void goToHome() {
        if (binding == null || getView() == null) return;
        Navigation.findNavController(getView()).navigate(R.id.action_register_to_home);
        com.example.se114_callingsystem.core.util.ThemeHelper.applyTheme(requireContext());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

