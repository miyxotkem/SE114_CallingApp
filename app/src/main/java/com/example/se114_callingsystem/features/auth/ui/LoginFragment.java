package com.example.se114_callingsystem.features.auth.ui;

import android.content.Intent;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.databinding.FragmentAuthLoginBinding;
import com.example.se114_callingsystem.features.auth.viewmodel.LoginViewModel;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class LoginFragment extends Fragment {

    private static final String TAG = "LoginFragment";

    private FragmentAuthLoginBinding binding;
    private LoginViewModel viewModel;
    private GoogleSignInClient mGoogleSignInClient;

    @Inject
    FirebaseAuth mAuth;

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        firebaseAuthWithGoogle(account.getIdToken());
                    } catch (ApiException e) {
                        Log.w(TAG, "Google sign in failed", e);
                        Toast.makeText(getContext(), "Google sign in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.w(TAG, "Google sign in result failed or cancelled. ResultCode: " + result.getResultCode());
                    Toast.makeText(getContext(), "Google sign in cancelled or failed.", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAuthLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        // Check if user is already logged in
        if (mAuth.getCurrentUser() != null) {
            goToHome();
            return;
        }

        setupGoogleSignIn();

        binding.btnLogin.setOnClickListener(v -> loginUser());
        binding.tvGoToRegister.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.action_login_to_register);
        });
        binding.btnGoogleLogin.setOnClickListener(v -> signInWithGoogle());

        binding.ivLogo.setOnLongClickListener(v -> {
            showServerConfigDialog();
            return true;
        });

        setupObservers();
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);
    }

    private void setupObservers() {
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (binding != null) {
                binding.btnLogin.setEnabled(!isLoading);
                binding.btnGoogleLogin.setEnabled(!isLoading);
                binding.btnLogin.setText(isLoading ? "Logging in..." : "Login");
            }
        });

        viewModel.getLoginResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;

            if ("SUCCESS".equals(result)) {
                goToHome();
            } else {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Authentication failed: " + result, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void loginUser() {
        if (binding == null) return;
        
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            binding.etEmail.setError("Email is required");
            binding.etEmail.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            binding.etPassword.setError("Password is required");
            binding.etPassword.requestFocus();
            return;
        }

        viewModel.login(email, password);
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        viewModel.loginWithGoogle(credential);
    }

    private void goToHome() {
        if (binding == null || getView() == null) return;
        Navigation.findNavController(getView()).navigate(R.id.action_login_to_home);
        com.example.se114_callingsystem.core.util.ThemeHelper.applyTheme(requireContext());
    }

    private void showServerConfigDialog() {
        if (getContext() == null) return;

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle("Configure Server IP");

        final android.widget.EditText input = new android.widget.EditText(getContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        String currentUrl = com.example.se114_callingsystem.network.ApiClient.getBaseUrl();
        input.setText(currentUrl);

        int paddingPx = (int) (16 * getContext().getResources().getDisplayMetrics().density);
        input.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        
        android.widget.FrameLayout container = new android.widget.FrameLayout(getContext());
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = paddingPx;
        params.rightMargin = paddingPx;
        params.topMargin = paddingPx / 2;
        params.bottomMargin = paddingPx / 2;
        input.setLayoutParams(params);
        container.addView(input);
        
        builder.setView(container);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newUrl = input.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                com.example.se114_callingsystem.network.ApiClient.saveBaseUrl(newUrl);
                Toast.makeText(getContext(), "Server URL updated to: " + newUrl, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "URL cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
