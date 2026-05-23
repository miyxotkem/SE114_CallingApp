package com.example.se114_callingsystem.server;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.model.Server;
import java.util.List;

public class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.ViewHolder> {
    private List<Server> serverList;

    public ServerAdapter(List<Server> serverList) {
        this.serverList = serverList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_list_item_servers, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Server server = serverList.get(position);
        holder.nameText.setText(server.getServerName());

        holder.itemView.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("SERVER_ID", server.getServerId());
            args.putString("SERVER_NAME", server.getServerName());
            androidx.navigation.Navigation.findNavController(v).navigate(R.id.action_home_to_server, args);
        });
    }

    @Override
    public int getItemCount() { return serverList.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.textServerName);
        }
    }
}
