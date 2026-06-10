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

        setupObservers();
        viewModel.initNotifications();
    }

    private void setupObservers() {
        viewModel.getNotifications().observe(getViewLifecycleOwner(), list -> {
            if (binding == null || list == null) return;

            notificationList.clear();
            notificationList.addAll(list);
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

        viewModel.getStatusMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null || getContext() == null) return;
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            viewModel.resetStatus();
        });
    }

    private void onNotificationClick(NotificationItem item) {
        if (item.getNotificationId() != null) {
            viewModel.markAsRead(item.getNotificationId());
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
        binding = null;
    }
}
