package com.example.se114_callingsystem;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ViewFlipper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class Server_on_create extends DialogFragment {

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

        view.findViewById(R.id.btnNext1).setOnClickListener(v -> {
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

        view.findViewById(R.id.btnFinish).setOnClickListener(v -> dismiss());
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