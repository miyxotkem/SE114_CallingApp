package com.example.se114_callingsystem.features.friend;

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
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.Firebase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

public class AddFriendDialog extends DialogFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return inflater.inflate(R.layout.dialog_friend_add, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText etFriendEmail = view.findViewById(R.id.etFriendEmail);
        Button btnAddFriendConfirm = view.findViewById(R.id.btnAddFriendConfirm);

        btnAddFriendConfirm.setOnClickListener(v -> {
            String email = etFriendEmail.getText().toString().trim();
            if (email.isEmpty()) {
                etFriendEmail.setError("Vui lòng nhập email");
                return;
            }

            btnAddFriendConfirm.setEnabled(false);
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) return;
            
            if (email.equals(currentUser.getEmail())) {
                Toast.makeText(getContext(), "Không thể thêm chính mình", Toast.LENGTH_SHORT).show();
                btnAddFriendConfirm.setEnabled(true);
                return;
            }

            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (com.google.firebase.firestore.DocumentSnapshot userSnap : queryDocumentSnapshots) {
                            String friendUid = userSnap.getString("uid");
                            if (friendUid != null) {
                                // Send a friend request
                                Firebase.getUserFriendRequestsRef(friendUid).child(currentUser.getUid()).setValue(true);
                                sendFriendNotification(friendUid, "friend_request");
                                
                                if (getActivity() != null) {
                                    Toast.makeText(getContext(), "Đã gửi lời mời kết bạn!", Toast.LENGTH_SHORT).show();
                                }
                                dismiss();
                                return;
                            }
                        }
                    } else {
                        Toast.makeText(getContext(), "Không tìm thấy người dùng", Toast.LENGTH_SHORT).show();
                        btnAddFriendConfirm.setEnabled(true);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnAddFriendConfirm.setEnabled(true);
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

    private void sendFriendNotification(String targetUid, String type) {
        String myUid = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        if (myUid.isEmpty() || targetUid == null || targetUid.isEmpty()) return;

        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(myUid).get()
            .addOnSuccessListener(doc -> {
                String myName = "Ai đó";
                if (doc.exists()) {
                    String name = doc.getString("username");
                    if (name != null && !name.isEmpty()) {
                        myName = name;
                    } else {
                        String email = doc.getString("email");
                        if (email != null && !email.isEmpty()) {
                            myName = email;
                        }
                    }
                }
                
                String title;
                String content;
                if ("friend_request".equals(type)) {
                    title = "Lời mời kết bạn";
                    content = myName + " đã gửi cho bạn một lời mời kết bạn.";
                } else {
                    title = "Chấp nhận kết bạn";
                    content = myName + " đã đồng ý lời mời kết bạn của bạn.";
                }

                java.util.Map<String, Object> notif = new java.util.HashMap<>();
                String notifId = String.valueOf(System.currentTimeMillis());
                notif.put("notificationId", notifId);
                notif.put("title", title);
                notif.put("content", content);
                notif.put("type", type);
                notif.put("senderId", myUid);
                notif.put("senderName", myName);
                notif.put("targetId", myUid); // Redirect to Friends list
                notif.put("timestamp", System.currentTimeMillis());
                notif.put("isRead", false);

                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users")
                    .document(targetUid)
                    .collection("notifications")
                    .document(notifId)
                    .set(notif);
            });
    }
}

