package com.example.se114_callingsystem.features.home.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.se114_callingsystem.core.model.User;
import com.example.se114_callingsystem.features.home.data.HomeRepository;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class HomeViewModel extends ViewModel {

    private final HomeRepository repository;

    private final MutableLiveData<List<User>> friendList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> userStatus = new MutableLiveData<>();
    private final MutableLiveData<String> operationStatus = new MutableLiveData<>();

    private ValueEventListener statusListener;
    private ValueEventListener friendsListener;
    private final List<ListenerRegistration> friendProfileListeners = new ArrayList<>();
    private final Map<String, User> friendMap = new HashMap<>();

    @Inject
    public HomeViewModel(HomeRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<User>> getFriendList() {
        return friendList;
    }

    public LiveData<String> getUserStatus() {
        return userStatus;
    }

    public LiveData<String> getOperationStatus() {
        return operationStatus;
    }

    public FirebaseUser getCurrentUser() {
        return repository.getCurrentUser();
    }

    public void initHome() {
        FirebaseUser user = getCurrentUser();
        if (user == null) return;
        
        String uid = user.getUid();

        // 1. Listen to user status in RTDB
        statusListener = repository.listenToUserStatus(uid, new HomeRepository.RealtimeCallback<String>() {
            @Override
            public void onData(String status) {
                userStatus.setValue(status != null ? status : "offline");
            }

            @Override
            public void onError(Exception e) {
                operationStatus.setValue("Failed to get status: " + e.getMessage());
            }
        });

        // 2. Listen to friends list in RTDB
        friendsListener = repository.listenToFriendsList(uid, new HomeRepository.RealtimeCallback<List<String>>() {
            @Override
            public void onData(List<String> friendUids) {
                clearProfileListeners();
                friendMap.clear();
                updateFriendList();

                if (friendUids.isEmpty()) {
                    return;
                }

                for (String friendUid : friendUids) {
                    ListenerRegistration registration = repository.listenToFriendProfile(friendUid, new HomeRepository.RealtimeCallback<User>() {
                        @Override
                        public void onData(User friendProfile) {
                            if (friendProfile != null && friendProfile.getUserId() != null) {
                                friendMap.put(friendProfile.getUserId(), friendProfile);
                                updateFriendList();
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            // Non-fatal error loading profile
                        }
                    });
                    friendProfileListeners.add(registration);
                }
            }

            @Override
            public void onError(Exception e) {
                operationStatus.setValue("Failed to load friends: " + e.getMessage());
            }
        });
    }

    private synchronized void updateFriendList() {
        List<User> list = new ArrayList<>(friendMap.values());
        friendList.setValue(list);
    }

    public void updateUserStatus(String status) {
        FirebaseUser user = getCurrentUser();
        if (user == null) return;

        repository.updateStatus(user.getUid(), status, new HomeRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Success updates local display automatically via RTDB status listener
            }

            @Override
            public void onFailure(Exception e) {
                operationStatus.setValue("Failed to update status: " + e.getMessage());
            }
        });
    }

    public void joinServer(String inviteCode) {
        FirebaseUser user = getCurrentUser();
        if (user == null) return;

        String userName = user.getDisplayName() != null ? user.getDisplayName() : "New Member";
        repository.joinServer(inviteCode, user.getUid(), userName, new HomeRepository.RepositoryCallback<String>() {
            @Override
            public void onSuccess(String result) {
                operationStatus.setValue("JOIN_" + result);
            }

            @Override
            public void onFailure(Exception e) {
                operationStatus.setValue("Join failed: " + e.getMessage());
            }
        });
    }

    public void resetStatus() {
        operationStatus.setValue(null);
    }

    private void clearProfileListeners() {
        for (ListenerRegistration reg : friendProfileListeners) {
            if (reg != null) {
                reg.remove();
            }
        }
        friendProfileListeners.clear();
    }

    private void clearListeners() {
        FirebaseUser user = getCurrentUser();
        if (user != null) {
            repository.removeUserStatusListener(user.getUid(), statusListener);
            repository.removeFriendsListListener(user.getUid(), friendsListener);
        }
        clearProfileListeners();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        clearListeners();
    }
}
