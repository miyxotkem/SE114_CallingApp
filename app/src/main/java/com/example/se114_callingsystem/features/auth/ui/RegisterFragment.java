package com.example.se114_callingsystem.features.auth.ui;

import android.os.Bundle;
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
import com.example.se114_callingsystem.features.auth.viewmodel.RegisterViewModel;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class RegisterFragment extends Fragment {

    private FragmentAuthRegisterBinding binding;
    private RegisterViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAuthRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new androidx.lifecycle.ViewModelProvider(this).get(RegisterViewModel.class);

        binding.btnRegister.setOnClickListener(v -> registerUser());
        binding.tvGoToLogin.setOnClickListener(v -> {
            Navigation.findNavController(v).popBackStack();
        });

        setupObservers();
    }

    private void setupObservers() {
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (binding != null) {
                binding.btnRegister.setEnabled(!isLoading);
                binding.btnRegister.setText(isLoading ? "Registering..." : "Register");
            }
        });

        viewModel.getRegisterResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;

            if ("SUCCESS".equals(result)) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Registration successful.", Toast.LENGTH_SHORT).show();
                }
                goToHome();
            } else {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Registration failed: " + result, Toast.LENGTH_LONG).show();
                }
            }
            viewModel.resetResult();
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

        viewModel.register(email, password, username);
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
