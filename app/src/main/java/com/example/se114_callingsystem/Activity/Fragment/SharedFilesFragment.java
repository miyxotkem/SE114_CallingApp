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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se114_callingsystem.Activity.Page.ChatInfoActivity;
import com.example.se114_callingsystem.Activity.Page.DocumentViewerActivity;
import com.example.se114_callingsystem.Model.Message;
import com.example.se114_callingsystem.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SharedFilesFragment extends Fragment {

    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private List<Message> fileMessages = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        recyclerView = new RecyclerView(requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setPadding(0, 8, 0, 8);
        recyclerView.setBackgroundColor(getResources().getColor(R.color.bg_primary, null));

        adapter = new FileAdapter();
        recyclerView.setAdapter(adapter);

        return recyclerView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof ChatInfoActivity) {
            fileMessages = ((ChatInfoActivity) getActivity()).getFileMessages();
            adapter.notifyDataSetChanged();
        }
    }

    // ===== INNER ADAPTER =====
    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {

        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shared_file, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Message msg = fileMessages.get(position);
            String fileUrl = msg.getContent();

            // Extract filename from URL
            String extractedName = "Document";
            try {
                extractedName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
            } catch (Exception e) {}
            final String fileName = extractedName;

            holder.tvName.setText(fileName);
            holder.tvDate.setText(dateFormat.format(new Date(msg.getTimestamp())));

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), DocumentViewerActivity.class);
                intent.putExtra("FILE_URL", fileUrl);
                intent.putExtra("FILE_NAME", fileName);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return fileMessages.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvDate;
            VH(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvSharedFileName);
                tvDate = itemView.findViewById(R.id.tvSharedFileDate);
            }
        }
    }
}
