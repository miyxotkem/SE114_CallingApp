package com.example.se114_callingsystem.Activity.Page;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.Util.ThemeHelper;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChangeColorActivity extends AppCompatActivity {

    private String serverId;
    private String selectedColor;

    // UI Preview
    private MaterialCardView topBarPreview;
    private TextView tvSentMessage;
    private Button btnSaveTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_color);

        serverId = getIntent().getStringExtra("SERVER_ID");
        selectedColor = getIntent().getStringExtra("CURRENT_COLOR");
        if (selectedColor == null) selectedColor = "#7289DA";

        topBarPreview = findViewById(R.id.topBarPreview);
        tvSentMessage = findViewById(R.id.tvSentMessage);
        btnSaveTheme = findViewById(R.id.btnSaveTheme);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Lấy danh sách các nút màu trong GridLayout
        int[] colorIds = {R.id.color1, R.id.color2, R.id.color3, R.id.color4, R.id.color5,
                R.id.color6, R.id.color7, R.id.color8, R.id.color9, R.id.color10};
        String[] colorHex = {"#7289DA", "#F44336", "#E91E63", "#9C27B0", "#673AB7",
                "#2196F3", "#00BCD4", "#4CAF50", "#FF9800", "#795548"};

        for (int i = 0; i < colorIds.length; i++) {
            final String hex = colorHex[i];
            findViewById(colorIds[i]).setOnClickListener(v -> updatePreview(hex));
        }

        updatePreview(selectedColor); // Cập nhật màu hiện tại lên preview

        btnSaveTheme.setOnClickListener(v -> {
            FirebaseFirestore.getInstance().collection("servers").document(serverId)
                    .update("accentColor", selectedColor)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Theme updated!", Toast.LENGTH_SHORT).show();
                        finish();
                    });
        });
    }

    private void updatePreview(String colorHex) {
        this.selectedColor = colorHex;
        int colorInt = Color.parseColor(colorHex);

        // Đổi màu thanh TopBar preview
        topBarPreview.setCardBackgroundColor(colorInt);

        // Đổi màu tin nhắn gửi đi (như Messenger)
        tvSentMessage.setBackgroundTintList(ColorStateList.valueOf(colorInt));

        // Đổi màu nút Save
        btnSaveTheme.setBackgroundTintList(ColorStateList.valueOf(colorInt));
    }
}