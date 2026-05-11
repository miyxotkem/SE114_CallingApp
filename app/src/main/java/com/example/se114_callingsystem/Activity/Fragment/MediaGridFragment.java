package com.example.se114_callingsystem.Activity.Fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.se114_callingsystem.Activity.Page.ChatInfoActivity;
import com.example.se114_callingsystem.Activity.Page.ImageViewerActivity;
import com.example.se114_callingsystem.R;

import java.util.ArrayList;
import java.util.List;

public class MediaGridFragment extends Fragment {

    private RecyclerView recyclerView;
    private MediaAdapter adapter;
    private List<String> mediaUrls = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        recyclerView = new RecyclerView(requireContext());
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        recyclerView.setPadding(4, 8, 4, 8);
        recyclerView.setBackgroundColor(getResources().getColor(R.color.bg_primary, null));

        adapter = new MediaAdapter();
        recyclerView.setAdapter(adapter);

        return recyclerView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof ChatInfoActivity) {
            mediaUrls = ((ChatInfoActivity) getActivity()).getMediaUrls();
            adapter.notifyDataSetChanged();
        }
    }

    // ===== INNER ADAPTER =====
    private class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_media_grid, parent, false);
            // Make it square
            int size = parent.getMeasuredWidth() / 3;
            v.setLayoutParams(new ViewGroup.LayoutParams(size, size));
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String url = mediaUrls.get(position);
            Glide.with(holder.iv.getContext())
                    .load(url)
                    .transform(new CenterCrop(), new RoundedCorners(8))
                    .into(holder.iv);

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), ImageViewerActivity.class);
                intent.putExtra("IMAGE_URL", url);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return mediaUrls.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView iv;
            VH(@NonNull View itemView) {
                super(itemView);
                iv = itemView.findViewById(R.id.ivMediaThumb);
            }
        }
    }
}
