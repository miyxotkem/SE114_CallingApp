package com.example.se114_callingsystem.Activity.Component;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.se114_callingsystem.Model.Firebase;
import com.example.se114_callingsystem.Model.User;
import com.example.se114_callingsystem.Model.ServerMember;
import com.example.se114_callingsystem.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

public class add_server_member extends DialogFragment {

    private String serverId;
    
    public add_server_member(String serverId) {
        this.serverId = serverId;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return inflater.inflate(R.layout.activity_add_server_member, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText etFriendEmail = view.findViewById(R.id.etFriendEmail);
        Button btnAddConfirm = view.findViewById(R.id.btnAddConfirm);

        btnAddConfirm.setOnClickListener(v -> {
            String email = etFriendEmail.getText().toString().trim();
            if (email.isEmpty()) {
                etFriendEmail.setError("Vui lòng nhập email");
                return;
            }

            btnAddConfirm.setEnabled(false);
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) return;

            // Search user by email
            Firebase.getUsersRef().orderByChild("email").equalTo(email).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        for (DataSnapshot userSnap : snapshot.getChildren()) {
                            User friendUser = userSnap.getValue(User.class);
                            if (friendUser != null) {
                                String friendUid = friendUser.getUserId();
                                String friendName = friendUser.getUsername() != null ? friendUser.getUsername() : "Unknown";
                                
                                // Check if they are friend
                                Firebase.getUserFriendsRef(currentUser.getUid()).child(friendUid).addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot friendSnap) {
                                        if (friendSnap.exists() && Boolean.TRUE.equals(friendSnap.getValue(Boolean.class))) {
                                            // Is a friend! Add to server in Firestore.
                                            FirebaseFirestore db = FirebaseFirestore.getInstance();
                                            
                                            // Add to members subcollection
                                            ServerMember newMember = new ServerMember(friendUid, friendName, "member");
                                            db.collection("servers").document(serverId).collection("members").document(friendUid)
                                                .set(newMember)
                                                .addOnSuccessListener(aVoid -> {
                                                    // Also add to the members array list in server document
                                                    db.collection("servers").document(serverId).update("members", FieldValue.arrayUnion(friendUid));
                                                    
                                                    if (getActivity() != null) {
                                                        Toast.makeText(getContext(), "Đã thêm vào server thành công!", Toast.LENGTH_SHORT).show();
                                                    }
                                                    dismiss();
                                                })
                                                .addOnFailureListener(e -> {
                                                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                                    btnAddConfirm.setEnabled(true);
                                                });
                                        } else {
                                            Toast.makeText(getContext(), "Người dùng này không phải là bạn bè của bạn", Toast.LENGTH_SHORT).show();
                                            btnAddConfirm.setEnabled(true);
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        Toast.makeText(getContext(), "Lỗi: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                        btnAddConfirm.setEnabled(true);
                                    }
                                });
                                return;
                            }
                        }
                    } else {
                        Toast.makeText(getContext(), "Không tìm thấy người dùng", Toast.LENGTH_SHORT).show();
                        btnAddConfirm.setEnabled(true);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(getContext(), "Lỗi: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    btnAddConfirm.setEnabled(true);
                }
            });
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }
}
