package com.example.se114_callingsystem.features.friend.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.se114_callingsystem.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class UpgradePlanFragment extends Fragment {

    private MaterialCardView cardBasicPlan, cardStandardPlan, cardProPlan;
    private MaterialButton btnSubscribe;
    private TextView tvRenewedDay;
    private ImageView btnBack;

    private String selectedPlan = "Basic";
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_upgrade_plan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String currentPlan = prefs.getString("current_plan", "Basic");
        selectedPlan = currentPlan;

        cardBasicPlan = view.findViewById(R.id.cardBasicPlan);
        cardStandardPlan = view.findViewById(R.id.cardStandardPlan);
        cardProPlan = view.findViewById(R.id.cardProPlan);
        btnSubscribe = view.findViewById(R.id.btnSubscribe);
        tvRenewedDay = view.findViewById(R.id.tvRenewedDay);
        btnBack = view.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());

        // Set up next renew date (1 month from today)
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, 1);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        tvRenewedDay.setText("Your plan renews on " + sdf.format(calendar.getTime()) + ".");

        updateSelectionUI();

        cardBasicPlan.setOnClickListener(v -> {
            selectedPlan = "Basic";
            updateSelectionUI();
        });

        cardStandardPlan.setOnClickListener(v -> {
            selectedPlan = "Standard";
            updateSelectionUI();
        });

        cardProPlan.setOnClickListener(v -> {
            selectedPlan = "Pro";
            updateSelectionUI();
        });

        btnSubscribe.setOnClickListener(v -> {
            if (selectedPlan.equals(currentPlan)) {
                Toast.makeText(getContext(), "You are already on the " + selectedPlan + " plan.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedPlan.equals("Basic")) {
                // Downgrade to Basic is free
                processUpgrade();
            } else {
                showPaymentMethodDialog();
            }
        });
    }

    private void updateSelectionUI() {
        // Reset all strokes
        cardBasicPlan.setStrokeColor(Color.TRANSPARENT);
        cardStandardPlan.setStrokeColor(Color.TRANSPARENT);
        cardProPlan.setStrokeColor(Color.TRANSPARENT);

        int highlightColor = getResources().getColor(R.color.discord_blurple);
        if (selectedPlan.equals("Pro")) {
            highlightColor = Color.parseColor("#FFD700");
        }

        switch (selectedPlan) {
            case "Basic":
                cardBasicPlan.setStrokeColor(highlightColor);
                break;
            case "Standard":
                cardStandardPlan.setStrokeColor(highlightColor);
                break;
            case "Pro":
                cardProPlan.setStrokeColor(highlightColor);
                break;
        }
    }

    private void showPaymentMethodDialog() {
        if (getContext() == null) return;
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_payment_method, null);
        dialog.setContentView(view);

        MaterialCardView cardQr = view.findViewById(R.id.cardQrMethod);
        MaterialCardView cardCredit = view.findViewById(R.id.cardCreditMethod);
        android.widget.ViewSwitcher switcher = view.findViewById(R.id.viewSwitcherPayment);
        com.google.android.material.button.MaterialButton btnConfirm = view.findViewById(R.id.btnConfirmPayment);

        cardQr.setOnClickListener(v -> {
            cardQr.setStrokeColor(getResources().getColor(R.color.discord_blurple));
            cardCredit.setStrokeColor(Color.TRANSPARENT);
            
            ImageView ivQr = view.findViewById(R.id.ivQrIcon);
            TextView tvQr = view.findViewById(R.id.tvQrText);
            if (ivQr != null) ivQr.setColorFilter(getResources().getColor(R.color.white));
            if (tvQr != null) tvQr.setTextColor(getResources().getColor(R.color.white));

            ImageView ivCard = view.findViewById(R.id.ivCardIcon);
            TextView tvCard = view.findViewById(R.id.tvCardText);
            if (ivCard != null) ivCard.setColorFilter(getResources().getColor(R.color.discord_text_muted));
            if (tvCard != null) tvCard.setTextColor(getResources().getColor(R.color.discord_text_muted));

            if (switcher.getDisplayedChild() != 0) {
                switcher.showPrevious();
            }
        });

        cardCredit.setOnClickListener(v -> {
            cardCredit.setStrokeColor(getResources().getColor(R.color.discord_blurple));
            cardQr.setStrokeColor(Color.TRANSPARENT);

            ImageView ivCard = view.findViewById(R.id.ivCardIcon);
            TextView tvCard = view.findViewById(R.id.tvCardText);
            if (ivCard != null) ivCard.setColorFilter(getResources().getColor(R.color.white));
            if (tvCard != null) tvCard.setTextColor(getResources().getColor(R.color.white));

            ImageView ivQr = view.findViewById(R.id.ivQrIcon);
            TextView tvQr = view.findViewById(R.id.tvQrText);
            if (ivQr != null) ivQr.setColorFilter(getResources().getColor(R.color.discord_text_muted));
            if (tvQr != null) tvQr.setTextColor(getResources().getColor(R.color.discord_text_muted));

            if (switcher.getDisplayedChild() != 1) {
                switcher.showNext();
            }
        });

        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            processUpgrade();
        });

        dialog.show();
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) bottomSheet.setBackgroundResource(android.R.color.transparent);
    }

    private void processUpgrade() {
        android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE);
        prefs.edit().putString("current_plan", selectedPlan).apply();
        
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(uid).update("plan", selectedPlan);

        android.widget.Toast.makeText(getContext(), "Plan upgraded to " + selectedPlan + "!", android.widget.Toast.LENGTH_SHORT).show();
        
        // Navigate back to profile
        if (getView() != null) {
            Navigation.findNavController(getView()).popBackStack();
        }
    }
}
