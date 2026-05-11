package com.example.se114_callingsystem.Activity.Page;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.Util.ThemeHelper;
import com.example.se114_callingsystem.Model.ServerMember;
import com.example.se114_callingsystem.Adapter.ServerMemberAdapter;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class ManageMembersActivity extends AppCompatActivity {

    private String serverId;
    private RecyclerView rvMembers;
    private ServerMemberAdapter adapter;
    private List<ServerMember> memberList = new ArrayList<>();
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_members);

        serverId = getIntent().getStringExtra("SERVER_ID");
        db = FirebaseFirestore.getInstance();

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        rvMembers = findViewById(R.id.rvMembers);
        rvMembers.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ServerMemberAdapter(memberList, this, new ServerMemberAdapter.OnMemberActionListener() {
            @Override
            public void onPromote(ServerMember member) {
                // Update role thành admin
                db.collection("servers").document(serverId).collection("members").document(member.getUserId())
                        .update("role", "admin")
                        .addOnSuccessListener(a -> {
                            Toast.makeText(ManageMembersActivity.this, "Promoted to Admin", Toast.LENGTH_SHORT).show();
                            loadMembers();
                        });
            }

            @Override
            public void onKick(ServerMember member) {
                // Xóa khỏi bảng members
                db.collection("servers").document(serverId).collection("members").document(member.getUserId())
                        .delete()
                        .addOnSuccessListener(a -> {
                            Toast.makeText(ManageMembersActivity.this, "Member kicked", Toast.LENGTH_SHORT).show();
                            loadMembers();
                        });
            }
        });
        rvMembers.setAdapter(adapter);

        loadMembers();
    }

    private void loadMembers() {
        // Giả định bạn lưu thành viên ở collection "members" bên trong "servers"
        db.collection("servers").document(serverId).collection("members").get()
                .addOnSuccessListener(snapshots -> {
                    memberList.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        ServerMember m = doc.toObject(ServerMember.class);
                        if (m != null) {
                            m.setUserId(doc.getId());
                            memberList.add(m);
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
    }
}