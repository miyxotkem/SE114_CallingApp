package com.example.se114_callingsystem;

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

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;

public class Server_on_create extends DialogFragment {

    FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return inflater.inflate(R.layout.activity_server_on_create, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewFlipper viewFlipper = view.findViewById(R.id.viewFlipper);
        EditText etName = view.findViewById(R.id.etServerName);
        EditText etPurpose = view.findViewById(R.id.etPurpose); // Found the missing field
        Button btnFinish = view.findViewById(R.id.btnFinish);

        view.findViewById(R.id.btnNext1).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
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

        btnFinish.setOnClickListener(v -> {
            btnFinish.setEnabled(false); // Prevent double clicks

            HashMap<String, Object> serverData = new HashMap<>();
            serverData.put("serverName", etName.getText().toString().trim());
            serverData.put("purpose", etPurpose.getText().toString().trim()); // Saving purpose
            serverData.put("ownerId", "L2j7rDA0Y0cmsO0XNcaW");
            serverData.put("iconUrl", "default_icon_url");

            db.collection("servers")
                .add(serverData)
                .addOnSuccessListener(documentReference -> {
                    Log.d("Firestore", "Server Created with ID: " + documentReference.getId());
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    btnFinish.setEnabled(true);
                    Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
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