package com.example.se114_callingsystem.features.home.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.se114_callingsystem.core.model.NotificationItem;
import com.example.se114_callingsystem.features.home.data.NotificationsRepository;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class NotificationsViewModel extends ViewModel {

    private final NotificationsRepository repository;

    private final MutableLiveData<List<NotificationItem>> notifications = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>();
    private ListenerRegistration notificationsRegistration;

    @Inject
    public NotificationsViewModel(NotificationsRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<NotificationItem>> getNotifications() {
        return notifications;
    }

    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }

    public FirebaseUser getCurrentUser() {
        return repository.getCurrentUser();
    }

    public void initNotifications() {
        FirebaseUser user = getCurrentUser();
        if (user == null) return;

        if (notificationsRegistration != null) {
            notificationsRegistration.remove();
        }

        notificationsRegistration = repository.listenToNotifications(user.getUid(), new NotificationsRepository.RealtimeCallback<List<NotificationItem>>() {
            @Override
            public void onData(List<NotificationItem> data) {
                notifications.setValue(data);
            }

            @Override
            public void onError(Exception e) {
                statusMessage.setValue("Failed to load notifications: " + e.getMessage());
            }
        });
    }

    public void markAsRead(String notificationId) {
        FirebaseUser user = getCurrentUser();
        if (user == null || notificationId == null) return;

        repository.markAsRead(user.getUid(), notificationId, new NotificationsRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Success automatically updates list via Firestore snapshot listener
            }

            @Override
            public void onFailure(Exception e) {
                statusMessage.setValue("Failed to mark notification as read: " + e.getMessage());
            }
        });
    }

    public void resetStatus() {
        statusMessage.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (notificationsRegistration != null) {
            notificationsRegistration.remove();
            notificationsRegistration = null;
        }
    }
}
