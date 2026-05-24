package com.example.se114_callingsystem.features.server;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class ManageMembersFragment extends Fragment {

    private String serverId;
    private RecyclerView rvMembers;
    private ServerMemberAdapter adapter;
    private List<ServerMember> memberList = new ArrayList<>();
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_server_manage_members, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            serverId = getArguments().getString("SERVER_ID");
        }
        db = FirebaseFirestore.getInstance();

        ImageView btnBack = view.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());

        rvMembers = view.findViewById(R.id.rvMembers);
        rvMembers.setLayoutManager(new LinearLayoutManager(getContext()));
        
        ImageView btnAddMember = view.findViewById(R.id.btnAddMember);
        btnAddMember.setOnClickListener(v -> {
            AddServerMemberDialog dialog = new AddServerMemberDialog(serverId);
            dialog.show(getParentFragmentManager(), "Add_server_member");
        });

        adapter = new ServerMemberAdapter(memberList, requireContext(), new ServerMemberAdapter.OnMemberActionListener() {
            @Override
            public void onPromote(ServerMember member) {
                // Update role thành admin
                db.collection("servers").document(serverId).collection("members").document(member.getUserId())
                        .update("role", "admin")
                        .addOnSuccessListener(a -> {
                            if (getContext() != null) {
                                Toast.makeText(requireContext(), "Promoted to Admin", Toast.LENGTH_SHORT).show();
                            }
                            loadMembers();
                        });
            }

            @Override
            public void onDemote(ServerMember member) {
                // Update role thành member
                db.collection("servers").document(serverId).collection("members").document(member.getUserId())
                        .update("role", "member")
                        .addOnSuccessListener(a -> {
                            if (getContext() != null) {
                                Toast.makeText(requireContext(), "Demoted to Member", Toast.LENGTH_SHORT).show();
                            }
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
                                
                            if (getContext() != null) {
                                Toast.makeText(requireContext(), "Member kicked", Toast.LENGTH_SHORT).show();
                            }
                            loadMembers();
                        });
            }

            @Override
            public void onSetNickname(ServerMember member) {
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
                builder.setTitle("Đặt biệt danh cho " + (member.getUserName() != null ? member.getUserName() : "thành viên"));

                final android.widget.EditText input = new android.widget.EditText(requireContext());
                input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
                input.setText(member.getNickname() != null ? member.getNickname() : "");
                builder.setView(input);

                builder.setPositiveButton("Lưu", (dialog, which) -> {
                    String nickname = input.getText().toString().trim();
                    db.collection("servers").document(serverId).collection("members").document(member.getUserId())
                            .update("nickname", nickname)
                            .addOnSuccessListener(a -> {
                                if (getContext() != null) {
                                    Toast.makeText(requireContext(), "Đã cập nhật biệt danh", Toast.LENGTH_SHORT).show();
                                }
                                loadMembers();
                            })
                            .addOnFailureListener(e -> {
                                if (getContext() != null) {
                                    Toast.makeText(requireContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
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
        if (serverId == null) return;
        db.collection("servers").document(serverId).collection("members").get()
                .addOnSuccessListener(snapshots -> {
                    if (getView() == null) return;
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
                    
                    ImageView btnAddMember = getView().findViewById(R.id.btnAddMember);
                    if (btnAddMember != null) {
                        if (canAddMembers) {
                            btnAddMember.setVisibility(android.view.View.VISIBLE);
                        } else {
                            btnAddMember.setVisibility(android.view.View.GONE);
                        }
                    }
                    
                    adapter.notifyDataSetChanged();
                });
    }
}
