package com.example.se114_callingsystem.core.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import com.example.se114_callingsystem.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

public class BottomSheetUtils {

    public interface OnConfirmListener {
        void onConfirm();
    }

    public static void showConfirmDialog(
            Context context, 
            String title, 
            String message, 
            String confirmText, 
            String confirmColorHex, 
            OnConfirmListener listener) {
        
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.layout_bottom_sheet_confirm, null);
        dialog.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvConfirmTitle);
        TextView tvMessage = view.findViewById(R.id.tvConfirmMessage);
        MaterialButton btnConfirm = view.findViewById(R.id.btnConfirm);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);

        tvTitle.setText(title);
        tvMessage.setText(message);
        
        if (confirmText != null && !confirmText.isEmpty()) {
            btnConfirm.setText(confirmText);
        }

        if (confirmColorHex != null && !confirmColorHex.isEmpty()) {
            try {
                btnConfirm.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(confirmColorHex)));
            } catch (Exception e) {}
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            if (listener != null) {
                listener.onConfirm();
            }
        });

        dialog.show();
        
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundResource(android.R.color.transparent);
        }
    }

    public interface OnInputConfirmListener {
        void onConfirm(String input);
    }

    public static void showInputDialog(
            Context context,
            String title,
            String hint,
            String prefill,
            String confirmText,
            String confirmColorHex,
            OnInputConfirmListener listener) {

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.layout_bottom_sheet_input, null);
        dialog.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvInputTitle);
        com.google.android.material.textfield.TextInputEditText etInput = view.findViewById(R.id.etInput);
        MaterialButton btnConfirm = view.findViewById(R.id.btnConfirm);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);

        tvTitle.setText(title);
        if (hint != null) etInput.setHint(hint);
        if (prefill != null) {
            etInput.setText(prefill);
            etInput.setSelection(etInput.getText().length());
        }

        if (confirmText != null && !confirmText.isEmpty()) {
            btnConfirm.setText(confirmText);
        }

        if (confirmColorHex != null && !confirmColorHex.isEmpty()) {
            try {
                btnConfirm.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(confirmColorHex)));
            } catch (Exception e) {}
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String input = etInput.getText() != null ? etInput.getText().toString().trim() : "";
            dialog.dismiss();
            if (listener != null) {
                listener.onConfirm(input);
            }
        });

        dialog.show();

        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundResource(android.R.color.transparent);
        }
    }

    public interface OnListOptionSelectedListener {
        void onOptionSelected(int index, String option);
    }

    public static void showListDialog(
            Context context,
            String title,
            String[] options,
            OnListOptionSelectedListener listener) {

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.layout_bottom_sheet_list, null);
        dialog.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvListTitle);
        android.widget.LinearLayout container = view.findViewById(R.id.llOptionsContainer);

        if (title != null && !title.isEmpty()) {
            tvTitle.setText(title);
            tvTitle.setVisibility(View.VISIBLE);
        } else {
            tvTitle.setVisibility(View.GONE);
        }

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            final String option = options[i];

            TextView tvOption = new TextView(context);
            tvOption.setText(option);
            tvOption.setTextColor(Color.parseColor("#B5BAC1"));
            tvOption.setTextSize(16f);
            tvOption.setPadding(0, 32, 0, 32);

            android.util.TypedValue outValue = new android.util.TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            tvOption.setBackgroundResource(outValue.resourceId);
            tvOption.setClickable(true);
            tvOption.setFocusable(true);

            tvOption.setOnClickListener(v -> {
                dialog.dismiss();
                if (listener != null) {
                    listener.onOptionSelected(index, option);
                }
            });

            container.addView(tvOption);
        }

        dialog.show();

        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundResource(android.R.color.transparent);
        }
    }
}
