package com.example.se114_callingsystem.features.server;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.example.se114_callingsystem.R;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChangeColorFragment extends Fragment {

    private String serverId;
    private String selectedColor;

    // UI Preview
    private MaterialCardView topBarPreview;
    private TextView tvSentMessage;
    private Button btnSaveTheme;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_server_change_color, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            serverId = getArguments().getString("SERVER_ID");
            selectedColor = getArguments().getString("CURRENT_COLOR");
        }
        if (selectedColor == null || selectedColor.equalsIgnoreCase("#7289DA")) {
            selectedColor = "#5865F2";
        }

        topBarPreview = view.findViewById(R.id.topBarPreview);
        tvSentMessage = view.findViewById(R.id.tvSentMessage);
        btnSaveTheme = view.findViewById(R.id.btnSaveTheme);

        ImageView btnBack = view.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());

        // Lấy danh sách các nút màu trong GridLayout
        int[] colorIds = {R.id.color1, R.id.color2, R.id.color3, R.id.color4, R.id.color5,
                R.id.color6, R.id.color7, R.id.color8, R.id.color9, R.id.color10};
        String[] colorHex = {"#5865F2", "#23A559", "#DA373C", "#FEE75C", "#EB459E",
                "#00A8FC", "#00D2C4", "#8A2BE2", "#FF5722", "#4E5058"};

        for (int i = 0; i < colorIds.length; i++) {
            final String hex = colorHex[i];
            view.findViewById(colorIds[i]).setOnClickListener(v -> updatePreview(hex));
        }

        updatePreview(selectedColor); // Cập nhật màu hiện tại lên preview

        btnSaveTheme.setOnClickListener(v -> {
            FirebaseFirestore.getInstance().collection("servers").document(serverId)
                    .update("accentColor", selectedColor)
                    .addOnSuccessListener(aVoid -> {
                        if (getContext() != null) {
                            Toast.makeText(requireContext(), "Theme updated!", Toast.LENGTH_SHORT).show();
                        }
                        Navigation.findNavController(v).popBackStack();
                    });
        });
    }

    private void updatePreview(String colorHex) {
        this.selectedColor = colorHex;
        int colorInt = Color.parseColor(colorHex);

        // Đổi màu thanh TopBar preview
        if (topBarPreview != null) topBarPreview.setCardBackgroundColor(colorInt);

        // Đổi màu tin nhắn gửi đi (như Messenger)
        if (tvSentMessage != null) tvSentMessage.setBackgroundTintList(ColorStateList.valueOf(colorInt));

        // Đổi màu nút Save
        if (btnSaveTheme != null) btnSaveTheme.setBackgroundTintList(ColorStateList.valueOf(colorInt));

        // Cập nhật checkmark hoạt động
        int[] checkIds = {R.id.check1, R.id.check2, R.id.check3, R.id.check4, R.id.check5,
                R.id.check6, R.id.check7, R.id.check8, R.id.check9, R.id.check10};
        String[] colorHexes = {"#5865F2", "#23A559", "#DA373C", "#FEE75C", "#EB459E",
                "#00A8FC", "#00D2C4", "#8A2BE2", "#FF5722", "#4E5058"};

        View view = getView();
        if (view != null) {
            for (int i = 0; i < checkIds.length; i++) {
                ImageView checkImg = view.findViewById(checkIds[i]);
                if (checkImg != null) {
                    if (colorHexes[i].equalsIgnoreCase(colorHex)) {
                        checkImg.setVisibility(View.VISIBLE);
                    } else {
                        checkImg.setVisibility(View.GONE);
                    }
                }
            }
        }
    }
}
