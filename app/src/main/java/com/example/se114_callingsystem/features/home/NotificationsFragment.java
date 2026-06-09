package com.example.se114_callingsystem.features.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.databinding.FragmentHomeNotificationsBinding;
import com.example.se114_callingsystem.core.model.NotificationItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {

    private static final String TAG = "NotificationsFragment";

    private FragmentHomeNotificationsBinding binding;
    private NotificationAdapter adapter;
    private final List<NotificationItem> notificationList = new ArrayList<>();
    private ListenerRegistration notificationsRegistration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeNotificationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Setup RecyclerView
        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NotificationAdapter(notificationList, this::onNotificationClick);
        binding.rvNotifications.setAdapter(adapter);

        listenToNotifications();
    }

    private void listenToNotifications() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId == null) return;

        notificationsRegistration = FirebaseFirestore.getInstance().collection("users")
                .document(currentUserId)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to notifications", error);
                        return;
                    }
                    if (binding == null) return;

                    notificationList.clear();
                    if (value != null && !value.isEmpty()) {
                        for (com.google.firebase.firestore.DocumentSnapshot doc : value.getDocuments()) {
                            NotificationItem item = doc.toObject(NotificationItem.class);
                            if (item != null) {
                                // Firestore mapping might map boolean "read" to JavaBean "isRead" getter
                                Boolean isReadVal = doc.getBoolean("read");
                                if (isReadVal != null) {
                                    item.setRead(isReadVal);
                                }
                                notificationList.add(item);
                            }
                        }
                    }

                    adapter.notifyDataSetChanged();

                    // Toggle empty state placeholder
                    if (notificationList.isEmpty()) {
                        binding.layoutNoNotifications.setVisibility(View.VISIBLE);
                        binding.rvNotifications.setVisibility(View.GONE);
                    } else {
                        binding.layoutNoNotifications.setVisibility(View.GONE);
                        binding.rvNotifications.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void onNotificationClick(NotificationItem item) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        
        // Mark as read in Firestore
        if (currentUserId != null && item.getNotificationId() != null) {
            FirebaseFirestore.getInstance().collection("users")
                    .document(currentUserId)
                    .collection("notifications")
                    .document(item.getNotificationId())
                    .update("read", true);
        }

        // Navigate based on notification type
        if ("dm".equals(item.getType()) || "mention".equals(item.getType())) {
            Bundle args = new Bundle();
            args.putString("CHAT_ID", item.getTargetId());
            
            String chatName = item.getSenderName();
            if ("mention".equals(item.getType())) {
                String title = item.getTitle();
                if (title != null && title.contains("#")) {
                    chatName = title.substring(title.indexOf("#") + 1);
                } else {
                    chatName = "Channel";
                }
            }
            
            args.putString("CHAT_NAME", chatName);
            args.putString("SERVER_ID", null);
            args.putString("SERVER_COLOR", "#5865F2");

            if (getView() != null) {
                Navigation.findNavController(getView()).navigate(R.id.action_notifications_to_chat_detail, args);
            }
        } else if ("friend_request".equals(item.getType()) || "friend_accepted".equals(item.getType())) {
            if (getView() != null) {
                Navigation.findNavController(getView()).navigate(R.id.action_notifications_to_manage_friends);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (notificationsRegistration != null) {
            notificationsRegistration.remove();
        }
        binding = null;
    }
}
