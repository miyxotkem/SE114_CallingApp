package com.example.se114_callingsystem.features.server.ui;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ViewFlipper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.Server;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Random;

public class CreateServerDialog extends DialogFragment {

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String generatedServerId;

    // BẢNG 10 MÀU XỊN XÒ ĐỂ RANDOM (Giống hệt ở bảng chọn màu)
    private final String[] palette = {
            "#7289DA", "#F44336", "#E91E63", "#9C27B0", "#673AB7",
            "#2196F3", "#00BCD4", "#4CAF50", "#FF9800", "#795548"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return inflater.inflate(R.layout.dialog_server_create, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewFlipper viewFlipper = view.findViewById(R.id.viewFlipper);
        EditText etName = view.findViewById(R.id.etServerName);
        EditText etPurpose = view.findViewById(R.id.etPurpose);
        Button btnFinish = view.findViewById(R.id.btnFinish);

        // Generate ID early for invite code
        generatedServerId = db.collection("servers").document().getId();

        // Navigation logic stays the same
        setupNavigation(view, viewFlipper, etName);
        
        android.widget.TextView tvInviteCode = view.findViewById(R.id.tvInviteCode);
        Button btnCopyInvite = view.findViewById(R.id.btnCopyInvite);
        if (tvInviteCode != null) tvInviteCode.setText(generatedServerId);
        if (btnCopyInvite != null) {
            btnCopyInvite.setOnClickListener(v -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Mã mời Server", generatedServerId);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "Đã copy mã mời!", Toast.LENGTH_SHORT).show();
            });
        }

        btnFinish.setOnClickListener(v -> {
            btnFinish.setEnabled(false);

            // 1. Get current server count to set the 'order' field automatically
            db.collection("servers").get().addOnSuccessListener(queryDocumentSnapshots -> {
                int currentOrder = queryDocumentSnapshots.size();

                // 2. TỰ ĐỘNG RANDOM MÀU ACCENT TẠI ĐÂY
                String randomAccentColor = palette[new Random().nextInt(palette.length)];

                // 3. Create Server object using your Model
                String ownerId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? 
                        com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : "unknown_owner";
                        
                Server newServer = new Server(
                        etName.getText().toString().trim(),
                        ownerId, // ownerId
                        "",
                        etPurpose.getText().toString().trim(),
                        randomAccentColor // Thay màu cứng thành màu ngẫu nhiên vừa bốc được
                );
                newServer.setOrderIndex(currentOrder);

                // 4. Save to Firestore
                db.collection("servers").document(generatedServerId)
                        .set(newServer)
                        .addOnSuccessListener(aVoid -> {
                            Log.d("Firestore", "Server Created with ID: " + generatedServerId);
                            
                            // Add owner to members subcollection
                            String ownerName = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null && com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getDisplayName() != null ? 
                                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getDisplayName() : "Owner";
                            com.example.se114_callingsystem.core.model.ServerMember ownerMember = new com.example.se114_callingsystem.core.model.ServerMember(ownerId, ownerName, "owner");
                            db.collection("servers").document(generatedServerId).collection("members").document(ownerId)
                                .set(ownerMember)
                                .addOnSuccessListener(ignored -> {
                                    if (getActivity() != null) {
                                        Toast.makeText(getContext(), "Server Created!", Toast.LENGTH_SHORT).show();
                                    }
                                    dismiss();
                                });
                        })
                        .addOnFailureListener(e -> {
                            btnFinish.setEnabled(true);
                            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            });
        });
    }

    private void setupNavigation(View view, ViewFlipper viewFlipper, EditText etName) {
        updateStepIndicator(view, 1);

        view.findViewById(R.id.btnNext1).setOnClickListener(v -> {
            if (etName.getText().toString().trim().isEmpty()) {
                etName.setError("Please enter a server name");
                return;
            }
            viewFlipper.setInAnimation(getContext(), R.anim.slide_in_right);
            viewFlipper.setOutAnimation(getContext(), R.anim.slide_out_left);
            viewFlipper.showNext();
            updateStepIndicator(view, 2);
        });

        view.findViewById(R.id.btnBack2).setOnClickListener(v -> {
            viewFlipper.setInAnimation(getContext(), R.anim.slide_in_left);
            viewFlipper.setOutAnimation(getContext(), R.anim.slide_out_right);
            viewFlipper.showPrevious();
            updateStepIndicator(view, 1);
        });

        view.findViewById(R.id.btnNext2).setOnClickListener(v -> {
            viewFlipper.setInAnimation(getContext(), R.anim.slide_in_right);
            viewFlipper.setOutAnimation(getContext(), R.anim.slide_out_left);
            viewFlipper.showNext();
            updateStepIndicator(view, 3);
        });

        view.findViewById(R.id.btnBack3).setOnClickListener(v -> {
            viewFlipper.setInAnimation(getContext(), R.anim.slide_in_left);
            viewFlipper.setOutAnimation(getContext(), R.anim.slide_out_right);
            viewFlipper.showPrevious();
            updateStepIndicator(view, 2);
        });
    }

    private void updateStepIndicator(View view, int step) {
        View step1 = view.findViewById(R.id.step1Indicator);
        View step2 = view.findViewById(R.id.step2Indicator);
        View step3 = view.findViewById(R.id.step3Indicator);
        if (step1 == null || step2 == null || step3 == null) return;
        
        step1.setBackgroundResource(step >= 1 ? R.color.discord_blurple : R.color.discord_divider);
        step2.setBackgroundResource(step >= 2 ? R.color.discord_blurple : R.color.discord_divider);
        step3.setBackgroundResource(step >= 3 ? R.color.discord_blurple : R.color.discord_divider);
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
