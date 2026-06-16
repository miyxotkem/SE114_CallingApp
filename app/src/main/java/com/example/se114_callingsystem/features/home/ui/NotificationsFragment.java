package com.example.se114_callingsystem.features.home.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.databinding.FragmentHomeNotificationsBinding;
import com.example.se114_callingsystem.core.model.NotificationItem;
import com.example.se114_callingsystem.features.home.viewmodel.NotificationsViewModel;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.List;

@AndroidEntryPoint
public class NotificationsFragment extends Fragment {

    private static final String TAG = "NotificationsFragment";

    private FragmentHomeNotificationsBinding binding;
    private NotificationsViewModel viewModel;
    private NotificationAdapter adapter;
    private final List<NotificationItem> notificationList = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        com.google.android.material.transition.MaterialSharedAxis enterTransition = 
            new com.google.android.material.transition.MaterialSharedAxis(
                com.google.android.material.transition.MaterialSharedAxis.X, /* forward= */ true);
        com.google.android.material.transition.MaterialSharedAxis returnTransition = 
            new com.google.android.material.transition.MaterialSharedAxis(
                com.google.android.material.transition.MaterialSharedAxis.X, /* forward= */ false);
        
        setEnterTransition(enterTransition);
        setReturnTransition(returnTransition);
        
        com.google.android.material.transition.MaterialSharedAxis exitTransition = 
            new com.google.android.material.transition.MaterialSharedAxis(
                com.google.android.material.transition.MaterialSharedAxis.X, /* forward= */ true);
        com.google.android.material.transition.MaterialSharedAxis reenterTransition = 
            new com.google.android.material.transition.MaterialSharedAxis(
                com.google.android.material.transition.MaterialSharedAxis.X, /* forward= */ false);
        
        setExitTransition(exitTransition);
        setReenterTransition(reenterTransition);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeNotificationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(NotificationsViewModel.class);

        // Setup RecyclerView
        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NotificationAdapter(notificationList, this::onNotificationClick);
        binding.rvNotifications.setAdapter(adapter);

        // Setup swipe to dismiss
        androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback swipeCallback = 
                new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.LEFT | androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, 
                                  @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder, 
                                  @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position >= 0 && position < notificationList.size()) {
                    NotificationItem item = notificationList.get(position);
                    viewModel.deleteNotification(item.getNotificationId());

                    com.google.android.material.snackbar.Snackbar.make(binding.rvNotifications, getString(R.string.notification_deleted), com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                            .setAction(getString(R.string.undo), v -> {
                                viewModel.restoreNotification(item);
                            })
                            .setActionTextColor(android.graphics.Color.YELLOW)
                            .show();
                }
            }
        };
        new androidx.recyclerview.widget.ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvNotifications);

        // Setup filter chips
        binding.chipGroupFilters.setOnCheckedChangeListener((group, checkedId) -> {
            applyFilter();
        });

        // Setup clear all button
        binding.btnClearAll.setOnClickListener(v -> {
            List<NotificationItem> currentNotifs = viewModel.getNotifications().getValue();
            if (currentNotifs == null || currentNotifs.isEmpty()) {
                Toast.makeText(getContext(), getString(R.string.no_notifications_to_clear), Toast.LENGTH_SHORT).show();
                return;
            }

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.clear_all_notifications_title))
                    .setMessage(getString(R.string.clear_all_notifications_message))
                    .setPositiveButton(getString(R.string.clear_all), (dialog, which) -> {
                        List<String> ids = new ArrayList<>();
                        for (NotificationItem item : currentNotifs) {
                            if (item.getNotificationId() != null) {
                                ids.add(item.getNotificationId());
                            }
                        }
                        viewModel.clearAllNotifications(ids);
                    })
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show();
        });

        setupObservers();
        viewModel.initNotifications();
        viewModel.autoClearOldNotifications();
    }

    private void setupObservers() {
        viewModel.getNotifications().observe(getViewLifecycleOwner(), list -> {
            applyFilter();
        });

        viewModel.getStatusMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null || getContext() == null) return;
            String localizedMessage = message;
            if ("Đã xóa tất cả thông báo".equals(message)) {
                localizedMessage = getString(R.string.cleared_all_notifications_toast);
            } else if (message.startsWith("Đã tự động dọn dẹp ") && message.endsWith(" thông báo cũ")) {
                try {
                    String countStr = message.replace("Đã tự động dọn dẹp ", "").replace(" thông báo cũ", "").trim();
                    int count = Integer.parseInt(countStr);
                    localizedMessage = getString(R.string.auto_cleaned_notifications, count);
                } catch (Exception e) {}
            }
            Toast.makeText(getContext(), localizedMessage, Toast.LENGTH_SHORT).show();
            viewModel.resetStatus();
        });
    }

    private void applyFilter() {
        if (binding == null || viewModel == null) return;
        List<NotificationItem> fullList = viewModel.getNotifications().getValue();
        if (fullList == null) {
            fullList = new ArrayList<>();
        }

        int checkedId = binding.chipGroupFilters.getCheckedChipId();
        List<NotificationItem> filteredList = new ArrayList<>();

        for (NotificationItem item : fullList) {
            String type = item.getType();
            if (!"friend_request".equals(type) &&
                !"friend_accepted".equals(type) &&
                !"mention".equals(type) &&
                !"new_post".equals(type) &&
                !"post_reply".equals(type)) {
                continue;
            }

            if (checkedId == R.id.chipAll) {
                filteredList.add(item);
            } else if (checkedId == R.id.chipUnread) {
                if (!item.isRead()) {
                    filteredList.add(item);
                }
            } else {
                filteredList.add(item);
            }
        }

        notificationList.clear();
        notificationList.addAll(filteredList);
        adapter.notifyDataSetChanged();

        if (notificationList.isEmpty()) {
            binding.layoutNoNotifications.setVisibility(View.VISIBLE);
            binding.rvNotifications.setVisibility(View.GONE);
        } else {
            binding.layoutNoNotifications.setVisibility(View.GONE);
            binding.rvNotifications.setVisibility(View.VISIBLE);
        }
    }

    private void onNotificationClick(NotificationItem item) {
        if (item.getNotificationId() != null) {
            viewModel.markAsRead(item.getNotificationId());
        }

        // Navigate or show dialog based on type
        if ("missed_call".equals(item.getType())) {
            showMissedCallActionDialog(item);
        } else if ("reminder_alert".equals(item.getType())) {
            Bundle args = new Bundle();
            args.putString("CHAT_ID", item.getTargetId());
            args.putString("CHAT_NAME", item.getSenderName() != null ? item.getSenderName() : "Reminder");
            args.putString("SERVER_ID", null);
            args.putString("SERVER_COLOR", "#5865F2");

            if (getView() != null) {
                Navigation.findNavController(getView()).navigate(R.id.action_notifications_to_chat_detail, args);
            }
        } else if ("new_post".equals(item.getType()) || "post_reply".equals(item.getType())) {
            Bundle args = new Bundle();
            args.putString("POST_ID", item.getTargetId());
            args.putString("POST_AUTHOR_ID", item.getSenderId());
            args.putString("SERVER_ID", null);
            args.putString("SERVER_COLOR", "#5865F2");

            if (getView() != null) {
                Navigation.findNavController(getView()).navigate(R.id.action_notifications_to_post_comment, args);
            }
        } else if ("dm".equals(item.getType()) || "mention".equals(item.getType())) {
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

    private void showMissedCallActionDialog(NotificationItem item) {
        if (getContext() == null) return;

        String friendUid = item.getSenderId();
        String currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null 
                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        if (friendUid == null || friendUid.isEmpty() || currentUid.isEmpty()) return;

        String chatRoomId = currentUid.compareTo(friendUid) < 0 
                ? "dm_" + currentUid + "_" + friendUid 
                : "dm_" + friendUid + "_" + currentUid;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.missed_call_dialog_title))
                .setMessage(getString(R.string.callback_confirm_message, item.getSenderName()))
                .setPositiveButton(getString(R.string.callback), (dialog, which) -> {
                    Toast.makeText(getContext(), getString(R.string.preparing_call), Toast.LENGTH_SHORT).show();
                    com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(currentUid)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                String currentUserName = "User";
                                if (documentSnapshot.exists()) {
                                    currentUserName = documentSnapshot.getString("username");
                                    if (currentUserName == null || currentUserName.isEmpty()) {
                                        currentUserName = "User";
                                    }
                                }
                                startCallBack(currentUid, currentUserName, friendUid, chatRoomId);
                            })
                            .addOnFailureListener(e -> {
                                startCallBack(currentUid, "User", friendUid, chatRoomId);
                            });
                })
                .setNegativeButton(getString(R.string.message), (dialog, which) -> {
                    Bundle args = new Bundle();
                    args.putString("CHAT_ID", chatRoomId);
                    args.putString("CHAT_NAME", item.getSenderName());
                    args.putString("SERVER_ID", null);
                    args.putString("SERVER_COLOR", "#5865F2");

                    if (getView() != null) {
                        Navigation.findNavController(getView()).navigate(R.id.action_notifications_to_chat_detail, args);
                    }
                })
                .setNeutralButton(getString(R.string.close), null)
                .show();
    }

    private void startCallBack(String currentUid, String currentUserName, String otherUid, String chatRoomId) {
        if (getContext() == null) return;
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        java.util.Map<String, Object> callMap = new java.util.HashMap<>();
        callMap.put("callerId", currentUid);
        callMap.put("callerName", currentUserName);
        callMap.put("channelName", chatRoomId);
        callMap.put("callType", "voice");
        callMap.put("status", "ringing");
        callMap.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(otherUid).collection("incomingCall").document("activeCall")
                .set(callMap)
                .addOnSuccessListener(aVoid -> {
                    if (getContext() != null) {
                        android.content.Intent intent = new android.content.Intent(requireContext(), com.example.se114_callingsystem.features.call.ui.CallActivity.class);
                        intent.putExtra("CALL_CHANNEL_NAME", chatRoomId);
                        intent.putExtra("SERVER_ID", (String) null);
                        intent.putExtra("SERVER_COLOR", "#5865F2");
                        intent.putExtra("IS_CALLER", true);
                        intent.putExtra("CALL_TYPE", "voice");
                        startActivity(intent);
                    }
                })
                .addOnFailureListener(e -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), getString(R.string.call_init_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
