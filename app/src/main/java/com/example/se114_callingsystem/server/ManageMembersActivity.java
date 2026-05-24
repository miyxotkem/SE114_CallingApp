package com.example.se114_callingsystem.server;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.model.ServerMember;
import com.example.se114_callingsystem.util.ThemeHelper;
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
        
        ImageView btnAddMember = findViewById(R.id.btnAddMember);
        btnAddMember.setOnClickListener(v -> {
            AddServerMemberDialog dialog = 
                new AddServerMemberDialog(serverId);
            dialog.show(getSupportFragmentManager(), "Add_server_member");
        });

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
            public void onDemote(ServerMember member) {
                // Update role thành member
                db.collection("servers").document(serverId).collection("members").document(member.getUserId())
                        .update("role", "member")
                        .addOnSuccessListener(a -> {
                            Toast.makeText(ManageMembersActivity.this, "Demoted to Member", Toast.LENGTH_SHORT).show();
                            loadMembers();
                        });
            }

            @Override
            public void onKick(ServerMember member) {
                // Xóa khỏi bảng members
                db.collection("servers").document(serverId).collection("members").document(member.getUserId())
                        .delete()
                        .addOnSuccessListener(a -> {
                            // Cập nhật lại mảng members ở server document để xóa userId này
                            db.collection("servers").document(serverId)
                                .update("members", com.google.firebase.firestore.FieldValue.arrayRemove(member.getUserId()));
                                
                            Toast.makeText(ManageMembersActivity.this, "Member kicked", Toast.LENGTH_SHORT).show();
                            loadMembers();
                        });
            }

            @Override
            public void onSetNickname(ServerMember member) {
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(ManageMembersActivity.this);
                builder.setTitle("Đặt biệt danh cho " + (member.getUserName() != null ? member.getUserName() : "thành viên"));

                final android.widget.EditText input = new android.widget.EditText(ManageMembersActivity.this);
                input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
                input.setText(member.getNickname() != null ? member.getNickname() : "");
                builder.setView(input);

                builder.setPositiveButton("Lưu", (dialog, which) -> {
                    String nickname = input.getText().toString().trim();
                    db.collection("servers").document(serverId).collection("members").document(member.getUserId())
                            .update("nickname", nickname)
                            .addOnSuccessListener(a -> {
                                Toast.makeText(ManageMembersActivity.this, "Đã cập nhật biệt danh", Toast.LENGTH_SHORT).show();
                                loadMembers();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(ManageMembersActivity.this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                });
                builder.setNegativeButton("Hủy", (dialog, which) -> dialog.cancel());
                builder.show();
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
                    String currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? 
                        com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
                    boolean canAddMembers = false;
                    
                    for (DocumentSnapshot doc : snapshots) {
                        ServerMember m = doc.toObject(ServerMember.class);
                        if (m != null) {
                            m.setUserId(doc.getId());
                            memberList.add(m);
                            
                            if (m.getUserId().equals(currentUid)) {
                                if ("owner".equals(m.getRole()) || "admin".equals(m.getRole())) {
                                    canAddMembers = true;
                                }
                            }
                        }
                    }
                    
                    ImageView btnAddMember = findViewById(R.id.btnAddMember);
                    if (canAddMembers) {
                        btnAddMember.setVisibility(android.view.View.VISIBLE);
                    } else {
                        btnAddMember.setVisibility(android.view.View.GONE);
                    }
                    
                    adapter.notifyDataSetChanged();
                });
    }
}
