package com.example.se114_callingsystem.core.viewer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.R;
import com.github.chrisbanes.photoview.PhotoView;

public class ImageViewerFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_core_image_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        PhotoView photoView = view.findViewById(R.id.photoView);
        ImageButton btnBack = view.findViewById(R.id.btnBackFromImage);

        String imageUrl = null;
        if (getArguments() != null) {
            imageUrl = getArguments().getString("IMAGE_URL");
        }

        // Load the full-res image
        Glide.with(this)
                .load(imageUrl)
                .into(photoView);

        // Make the back button work
        btnBack.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());
    }
}
