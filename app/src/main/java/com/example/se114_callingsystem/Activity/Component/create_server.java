package com.example.se114_callingsystem.Activity.Component;

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

import com.example.se114_callingsystem.Model.Server;
import com.example.se114_callingsystem.R;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Random;

public class create_server extends DialogFragment {

    private FirebaseFirestore db = FirebaseFirestore.getInstance();

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
        return inflater.inflate(R.layout.activity_create_server, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewFlipper viewFlipper = view.findViewById(R.id.viewFlipper);
        EditText etName = view.findViewById(R.id.etServerName);
        EditText etPurpose = view.findViewById(R.id.etPurpose);
        Button btnFinish = view.findViewById(R.id.btnFinish);

        // Navigation logic stays the same
        setupNavigation(view, viewFlipper, etName);

        btnFinish.setOnClickListener(v -> {
            btnFinish.setEnabled(false);

            // 1. Get current server count to set the 'order' field automatically
            db.collection("servers").get().addOnSuccessListener(queryDocumentSnapshots -> {
                int currentOrder = queryDocumentSnapshots.size();

                // 2. TỰ ĐỘNG RANDOM MÀU ACCENT TẠI ĐÂY
                String randomAccentColor = palette[new Random().nextInt(palette.length)];

                // 3. Create Server object using your Model
                Server newServer = new Server(
                        etName.getText().toString().trim(),
                        "L2j7rDA0Y0cmsO0XNcaW", // ownerId
                        "default_icon_url",
                        etPurpose.getText().toString().trim(),
                        randomAccentColor // Thay màu cứng thành màu ngẫu nhiên vừa bốc được
                );
                newServer.setOrderIndex(currentOrder);

                // 4. Save to Firestore
                db.collection("servers")
                        .add(newServer)
                        .addOnSuccessListener(documentReference -> {
                            Log.d("Firestore", "Server Created with ID: " + documentReference.getId());
                            if (getActivity() != null) {
                                Toast.makeText(getContext(), "Server Created!", Toast.LENGTH_SHORT).show();
                            }
                            dismiss();
                        })
                        .addOnFailureListener(e -> {
                            btnFinish.setEnabled(true);
                            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            });
        });
    }

    private void setupNavigation(View view, ViewFlipper viewFlipper, EditText etName) {
        view.findViewById(R.id.btnNext1).setOnClickListener(v -> {
            if (etName.getText().toString().trim().isEmpty()) {
                etName.setError("Please enter a server name");
                return;
            }
            viewFlipper.setInAnimation(getContext(), R.anim.slide_in_right);
            viewFlipper.setOutAnimation(getContext(), R.anim.slide_out_left);
            viewFlipper.showNext();
        });

        view.findViewById(R.id.btnBack2).setOnClickListener(v -> {
            viewFlipper.setInAnimation(getContext(), R.anim.slide_in_left);
            viewFlipper.setOutAnimation(getContext(), R.anim.slide_out_right);
            viewFlipper.showPrevious();
        });

        view.findViewById(R.id.btnNext2).setOnClickListener(v -> {
            viewFlipper.setInAnimation(getContext(), R.anim.slide_in_right);
            viewFlipper.setOutAnimation(getContext(), R.anim.slide_out_left);
            viewFlipper.showNext();
        });

        view.findViewById(R.id.btnBack3).setOnClickListener(v -> {
            viewFlipper.setInAnimation(getContext(), R.anim.slide_in_left);
            viewFlipper.setOutAnimation(getContext(), R.anim.slide_out_right);
            viewFlipper.showPrevious();
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