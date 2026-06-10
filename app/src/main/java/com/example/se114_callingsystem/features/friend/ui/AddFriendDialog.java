package com.example.se114_callingsystem.features.friend.ui;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.features.friend.viewmodel.ManageFriendsViewModel;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class AddFriendDialog extends DialogFragment {

    private ManageFriendsViewModel viewModel;
    private Button btnAddFriendConfirm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return inflater.inflate(R.layout.dialog_friend_add, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireParentFragment()).get(ManageFriendsViewModel.class);

        EditText etFriendEmail = view.findViewById(R.id.etFriendEmail);
        btnAddFriendConfirm = view.findViewById(R.id.btnAddFriendConfirm);

        btnAddFriendConfirm.setOnClickListener(v -> {
            String email = etFriendEmail.getText().toString().trim();
            if (email.isEmpty()) {
                etFriendEmail.setError("Vui lòng nhập email");
                return;
            }

            btnAddFriendConfirm.setEnabled(false);
            viewModel.sendFriendRequest(email);
        });

        setupObservers();
    }

    private void setupObservers() {
        viewModel.getOperationStatus().observe(getViewLifecycleOwner(), status -> {
            if (status == null || getContext() == null) return;

            switch (status) {
                case "SEND_REQUEST_SUCCESS":
                    Toast.makeText(getContext(), "Đã gửi lời mời kết bạn!", Toast.LENGTH_SHORT).show();
                    viewModel.resetStatus();
                    dismiss();
                    break;
                case "SEND_REQUEST_SELF":
                    Toast.makeText(getContext(), "Không thể thêm chính mình", Toast.LENGTH_SHORT).show();
                    btnAddFriendConfirm.setEnabled(true);
                    viewModel.resetStatus();
                    break;
                case "SEND_REQUEST_NOT_FOUND":
                    Toast.makeText(getContext(), "Không tìm thấy người dùng", Toast.LENGTH_SHORT).show();
                    btnAddFriendConfirm.setEnabled(true);
                    viewModel.resetStatus();
                    break;
                case "INVALID_USER_DATA":
                    Toast.makeText(getContext(), "Dữ liệu người dùng không hợp lệ", Toast.LENGTH_SHORT).show();
                    btnAddFriendConfirm.setEnabled(true);
                    viewModel.resetStatus();
                    break;
                default:
                    if (status.startsWith("Failed to send request")) {
                        Toast.makeText(getContext(), status, Toast.LENGTH_SHORT).show();
                        btnAddFriendConfirm.setEnabled(true);
                        viewModel.resetStatus();
                    }
                    break;
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }
}
