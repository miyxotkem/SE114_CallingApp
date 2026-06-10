package com.example.se114_callingsystem.features.friend.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.se114_callingsystem.core.model.User;
import com.example.se114_callingsystem.features.friend.data.ManageFriendsRepository;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ManageFriendsViewModel extends ViewModel {

    private final ManageFriendsRepository repository;

    private final MutableLiveData<List<User>> friendRequests = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<User>> friends = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> operationStatus = new MutableLiveData<>();

    private ValueEventListener requestsListener;
    private ValueEventListener friendsListener;

    private final List<User> rawRequests = new ArrayList<>();
    private final List<User> rawFriends = new ArrayList<>();
    private final Map<String, User> requestsMap = new HashMap<>();
    private final Map<String, User> friendsMap = new HashMap<>();
    private String currentQuery = "";

    @Inject
    public ManageFriendsViewModel(ManageFriendsRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<User>> getFriendRequests() {
        return friendRequests;
    }

    public LiveData<List<User>> getFriends() {
        return friends;
    }

    public LiveData<String> getOperationStatus() {
        return operationStatus;
    }

    public FirebaseUser getCurrentUser() {
        return repository.getCurrentUser();
    }

    public void initFriends() {
        FirebaseUser user = getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();

        // 1. Listen to friend requests in RTDB
        requestsListener = repository.listenToFriendRequests(uid, new ManageFriendsRepository.RealtimeCallback<List<String>>() {
            @Override
            public void onData(List<String> uids) {
                requestsMap.clear();
                if (uids.isEmpty()) {
                    updateRequestsList();
                    return;
                }

                for (String senderUid : uids) {
                    repository.loadUserProfile(senderUid, new ManageFriendsRepository.RepositoryCallback<User>() {
                        @Override
                        public void onSuccess(User friend) {
                            if (friend != null && friend.getUserId() != null) {
                                requestsMap.put(friend.getUserId(), friend);
                                updateRequestsList();
                            }
                        }

                        @Override
                        public void onFailure(Exception e) {}
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                operationStatus.setValue("Failed to load friend requests: " + e.getMessage());
            }
        });

        // 2. Listen to friends in RTDB
        friendsListener = repository.listenToFriendsList(uid, new ManageFriendsRepository.RealtimeCallback<List<String>>() {
            @Override
            public void onData(List<String> uids) {
                friendsMap.clear();
                if (uids.isEmpty()) {
                    updateFriendsList();
                    return;
                }

                for (String friendUid : uids) {
                    repository.loadUserProfile(friendUid, new ManageFriendsRepository.RepositoryCallback<User>() {
                        @Override
                        public void onSuccess(User friend) {
                            if (friend != null && friend.getUserId() != null) {
                                friendsMap.put(friend.getUserId(), friend);
                                updateFriendsList();
                            }
                        }

                        @Override
                        public void onFailure(Exception e) {}
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                operationStatus.setValue("Failed to load friends: " + e.getMessage());
            }
        });
    }

    private synchronized void updateRequestsList() {
        rawRequests.clear();
        rawRequests.addAll(requestsMap.values());
        filterLists(currentQuery);
    }

    private synchronized void updateFriendsList() {
        rawFriends.clear();
        rawFriends.addAll(friendsMap.values());
        filterLists(currentQuery);
    }

    public void filterLists(String query) {
        currentQuery = query.toLowerCase().trim();

        // Filter requests
        List<User> filteredRequests = new ArrayList<>();
        if (currentQuery.isEmpty()) {
            filteredRequests.addAll(rawRequests);
        } else {
            for (User u : rawRequests) {
                String name = u.getUsername() != null ? u.getUsername().toLowerCase() : "";
                String email = u.getEmail() != null ? u.getEmail().toLowerCase() : "";
                if (name.contains(currentQuery) || email.contains(currentQuery)) {
                    filteredRequests.add(u);
                }
            }
        }
        friendRequests.setValue(filteredRequests);

        // Filter friends
        List<User> filteredFriends = new ArrayList<>();
        if (currentQuery.isEmpty()) {
            filteredFriends.addAll(rawFriends);
        } else {
            for (User u : rawFriends) {
                String name = u.getUsername() != null ? u.getUsername().toLowerCase() : "";
                String email = u.getEmail() != null ? u.getEmail().toLowerCase() : "";
                if (name.contains(currentQuery) || email.contains(currentQuery)) {
                    filteredFriends.add(u);
                }
            }
        }
        friends.setValue(filteredFriends);
    }

    public void acceptRequest(String friendUid) {
        FirebaseUser user = getCurrentUser();
        if (user == null) return;

        repository.acceptFriendRequest(user.getUid(), friendUid, new ManageFriendsRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                operationStatus.setValue("ACCEPT_SUCCESS");
            }

            @Override
            public void onFailure(Exception e) {
                operationStatus.setValue("Failed to accept request: " + e.getMessage());
            }
        });
    }

    public void rejectRequest(String friendUid) {
        FirebaseUser user = getCurrentUser();
        if (user == null) return;

        repository.rejectFriendRequest(user.getUid(), friendUid, new ManageFriendsRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                operationStatus.setValue("REJECT_SUCCESS");
            }

            @Override
            public void onFailure(Exception e) {
                operationStatus.setValue("Failed to reject request: " + e.getMessage());
            }
        });
    }

    public void removeFriend(String friendUid) {
        FirebaseUser user = getCurrentUser();
        if (user == null) return;

        repository.removeFriend(user.getUid(), friendUid, new ManageFriendsRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                operationStatus.setValue("REMOVE_SUCCESS");
            }

            @Override
            public void onFailure(Exception e) {
                operationStatus.setValue("Failed to remove friend: " + e.getMessage());
            }
        });
    }

    public void sendFriendRequest(String email) {
        FirebaseUser user = getCurrentUser();
        if (user == null) return;

        repository.sendFriendRequest(user.getUid(), user.getEmail(), email, new ManageFriendsRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                operationStatus.setValue("SEND_REQUEST_SUCCESS");
            }

            @Override
            public void onFailure(Exception e) {
                if ("SELF_REQUEST".equals(e.getMessage())) {
                    operationStatus.setValue("SEND_REQUEST_SELF");
                } else if ("USER_NOT_FOUND".equals(e.getMessage())) {
                    operationStatus.setValue("SEND_REQUEST_NOT_FOUND");
                } else {
                    operationStatus.setValue("Failed to send request: " + e.getMessage());
                }
            }
        });
    }

    public void resetStatus() {
        operationStatus.setValue(null);
    }

    private void clearListeners() {
        FirebaseUser user = getCurrentUser();
        if (user != null) {
            repository.removeFriendRequestsListener(user.getUid(), requestsListener);
            repository.removeFriendsListListener(user.getUid(), friendsListener);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        clearListeners();
    }
}
