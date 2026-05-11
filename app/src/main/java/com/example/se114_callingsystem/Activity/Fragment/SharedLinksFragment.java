package com.example.se114_callingsystem.Activity.Fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se114_callingsystem.Activity.Page.ChatInfoActivity;
import com.example.se114_callingsystem.R;

import java.util.ArrayList;
import java.util.List;

public class SharedLinksFragment extends Fragment {

    private RecyclerView recyclerView;
    private LinkAdapter adapter;
    private List<String[]> linkItems = new ArrayList<>(); // [url, contextText]

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        recyclerView = new RecyclerView(requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setPadding(0, 8, 0, 8);
        recyclerView.setBackgroundColor(getResources().getColor(R.color.bg_primary, null));

        adapter = new LinkAdapter();
        recyclerView.setAdapter(adapter);

        return recyclerView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof ChatInfoActivity) {
            linkItems = ((ChatInfoActivity) getActivity()).getLinkItems();
            adapter.notifyDataSetChanged();
        }
    }

    // ===== INNER ADAPTER =====
    private class LinkAdapter extends RecyclerView.Adapter<LinkAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shared_link, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String[] item = linkItems.get(position);
            String url = item[0];
            String context = item[1];

            holder.tvUrl.setText(url);
            holder.tvContext.setText(context);

            holder.itemView.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    // Invalid URL
                }
            });
        }

        @Override
        public int getItemCount() {
            return linkItems.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvUrl, tvContext;
            VH(@NonNull View itemView) {
                super(itemView);
                tvUrl = itemView.findViewById(R.id.tvLinkUrl);
                tvContext = itemView.findViewById(R.id.tvLinkContext);
            }
        }
    }
}
