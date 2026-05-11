package com.example.se114_callingsystem.Activity.Page;

import android.os.Bundle;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.Util.ThemeHelper;
import com.github.chrisbanes.photoview.PhotoView;

public class ImageViewerActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        PhotoView photoView = findViewById(R.id.photoView);
        ImageButton btnBack = findViewById(R.id.btnBackFromImage);

        // Get the URL passed from the adapter
        String imageUrl = getIntent().getStringExtra("IMAGE_URL");

        // Load the full-res image
        Glide.with(this)
                .load(imageUrl)
                .into(photoView);

        // Make the back button work
        btnBack.setOnClickListener(v -> finish());
    }
}