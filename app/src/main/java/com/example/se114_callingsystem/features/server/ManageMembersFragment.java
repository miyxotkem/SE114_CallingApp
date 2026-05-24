package com.example.se114_callingsystem.features.server;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.example.se114_callingsystem.core.util.ThemeHelper;
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
        setContentView(R.layout.activity_server_manage_members);

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
                // Update role thÃ nh admin
                db.collection("servers").document(serverId).collection("members").document(member.getUserId())
                        .update("role", "admin")
                        .addOnSuccessListener(a -> {
                            Toast.makeText(ManageMembersActivity.this, "Promoted to Admin", Toast.LENGTH_SHORT).show();
                            loadMembers();
                        });
            }

            @Override
            public void onDemote(ServerMember member) {
                // Update role thÃ nh member
                db.collection("servers").document(serverId).collection("members").document(member.getUserId())
                        .update("role", "member")
                        .addOnSuccessListener(a -> {
                            Toast.makeText(ManageMembersActivity.this, "Demoted to Member", Toast.LENGTH_SHORT).show();
                            loadMembers();
                        });
            }

            @Override
            public void onKick(ServerMember member) {
                // XÃ³a khá»i báº£ng members
                db.collection("servers").document(serverId).collection("members").document(member.getUserId())
                        .delete()
                        .addOnSuccessListener(a -> {
                            // Cáº­p nháº­t láº¡i máº£ng members á»Ÿ server document Ä‘á»ƒ xÃ³a userId nÃ y
                            db.collection("servers").document(serverId)
                                .update("members", com.google.firebase.firestore.FieldValue.arrayRemove(member.getUserId()));
                                
                            Toast.makeText(ManageMembersActivity.this, "Member kicked", Toast.LENGTH_SHORT).show();
                            loadMembers();
                        });
            }

            @Override
            public void onSetNickname(ServerMember member) {
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(ManageMembersActivity.this);
                builder.setTitle("Äáº·t biá»‡t danh cho " + (member.getUserName() != null ? member.getUserName() : "thÃ nh viÃªn"));

                final android.widget.EditText input = new android.widget.EditText(ManageMembersActivity.this);
                input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
                input.setText(member.getNickname() != null ? member.getNickname() : "");
                builder.setView(input);

                builder.setPositiveButton("LÆ°u", (dialog, which) -> {
                    String nickname = input.getText().toString().trim();
                    db.collection("servers").document(serverId).collection("members").document(member.getUserId())
                            .update("nickname", nickname)
                            .addOnSuccessListener(a -> {
                                Toast.makeText(ManageMembersActivity.this, "ÄÃ£ cáº­p nháº­t biá»‡t danh", Toast.LENGTH_SHORT).show();
                                loadMembers();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(ManageMembersActivity.this, "Lá»—i: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                });
                builder.setNegativeButton("Há»§y", (dialog, which) -> dialog.cancel());
                builder.show();
            }
        });
        rvMembers.setAdapter(adapter);

        loadMembers();
    }

    private void loadMembers() {
        // Giáº£ Ä‘á»‹nh báº¡n lÆ°u thÃ nh viÃªn á»Ÿ collection "members" bÃªn trong "servers"
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

