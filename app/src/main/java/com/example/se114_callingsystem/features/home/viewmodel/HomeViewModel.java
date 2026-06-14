package com.example.se114_callingsystem.features.home.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.se114_callingsystem.core.model.Firebase;
import com.example.se114_callingsystem.core.model.Message;
import com.example.se114_callingsystem.core.model.User;
import com.example.se114_callingsystem.features.home.data.HomeRepository;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
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
    private final MutableLiveData<Map<String, Integer>> unreadCounts = new MutableLiveData<>(new HashMap<>());

    private ValueEventListener statusListener;
    private ValueEventListener friendsListener;
    private ListenerRegistration userProfileListener;
    private ListenerRegistration unreadNotificationsListener;
    private final List<ListenerRegistration> friendProfileListeners = new ArrayList<>();
    
    private final Map<String, User> friendMap = new HashMap<>();
    private final Map<String, Long> friendLastMsgMap = new HashMap<>();
    private final Map<String, ValueEventListener> messageListeners = new HashMap<>();
    private final List<String> pinnedDMs = new ArrayList<>();

    @Inject
    public HomeViewModel(HomeRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<User>> getFriendList() {
        return friendList;
    }

    public java.util.Map<String, User> getFriendMap() {
        return friendMap;
    }

    public LiveData<String> getUserStatus() {
        return userStatus;
    }

    public LiveData<String> getOperationStatus() {
        return operationStatus;
    }

    public LiveData<Map<String, Integer>> getUnreadCounts() {
        return unreadCounts;
    }

    public FirebaseUser getCurrentUser() {
        return repository.getCurrentUser();
    }

    public boolean isUserPinned(String friendUid) {
        return pinnedDMs.contains(friendUid);
    }

    public void togglePin(String friendUid) {
        if (pinnedDMs.contains(friendUid)) {
            pinnedDMs.remove(friendUid);
        } else {
            pinnedDMs.add(friendUid);
        }
        FirebaseUser user = getCurrentUser();
        if (user != null) {
            repository.updatePinnedDMs(user.getUid(), new ArrayList<>(pinnedDMs));
        }
        updateFriendList();
    }

    public void updatePinnedOrder(List<User> newList) {
        List<String> newPinned = new ArrayList<>();
        for (User u : newList) {
            if (pinnedDMs.contains(u.getUserId())) {
                newPinned.add(u.getUserId());
            }
        }
        pinnedDMs.clear();
        pinnedDMs.addAll(newPinned);
        
        FirebaseUser user = getCurrentUser();
        if (user != null) {
            repository.updatePinnedDMs(user.getUid(), new ArrayList<>(pinnedDMs));
        }
        updateFriendList();
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

        // 1b. Listen to unread notifications count
        unreadNotificationsListener = repository.listenToUnreadNotifications(uid, new HomeRepository.RealtimeCallback<Map<String, Integer>>() {
            @Override
            public void onData(Map<String, Integer> counts) {
                unreadCounts.setValue(counts);
            }
            @Override
            public void onError(Exception e) {}
        });

        // 2. Listen to pinned DMs from user profile
        userProfileListener = repository.listenToCurrentUserProfile(uid, new HomeRepository.RealtimeCallback<DocumentSnapshot>() {
            @Override
            public void onData(DocumentSnapshot doc) {
                List<String> pinned = (List<String>) doc.get("pinnedDMs");
                pinnedDMs.clear();
                if (pinned != null) {
                    pinnedDMs.addAll(pinned);
                }
                updateFriendList();
            }
            @Override
            public void onError(Exception e) {}
        });

        // 3. Listen to friends list in RTDB
        friendsListener = repository.listenToFriendsList(uid, new HomeRepository.RealtimeCallback<List<String>>() {
            @Override
            public void onData(List<String> friendUids) {
                clearProfileListeners();
                clearMessageListeners();
                friendMap.clear();
                updateFriendList();

                if (friendUids.isEmpty()) {
                    return;
                }

                for (String friendUid : friendUids) {
                    // Profile listener
                    ListenerRegistration registration = repository.listenToFriendProfile(friendUid, new HomeRepository.RealtimeCallback<User>() {
                        @Override
                        public void onData(User friendProfile) {
                            if (friendProfile != null && friendProfile.getUserId() != null) {
                                friendMap.put(friendProfile.getUserId(), friendProfile);
                                updateFriendList();
                            }
                        }
                        @Override
                        public void onError(Exception e) {}
                    });
                    friendProfileListeners.add(registration);

                    // Last message listener
                    String dmRoomId = uid.compareTo(friendUid) < 0 ? "dm_" + uid + "_" + friendUid : "dm_" + friendUid + "_" + uid;
                    DatabaseReference msgRef = Firebase.getMessagesRefByRoom(dmRoomId);
                    ValueEventListener msgListener = msgRef.orderByChild("timestamp").limitToLast(1).addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            long timestamp = 0;
                            if (snapshot.exists()) {
                                for (DataSnapshot child : snapshot.getChildren()) {
                                    Message msg = child.getValue(Message.class);
                                    if (msg != null) timestamp = msg.getTimestamp();
                                }
                            }
                            friendLastMsgMap.put(friendUid, timestamp);
                            updateFriendList();
                        }
                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });
                    messageListeners.put(dmRoomId, msgListener);
                }
            }

            @Override
            public void onError(Exception e) {
                operationStatus.setValue("Failed to load friends: " + e.getMessage());
            }
        });
    }

    private synchronized void updateFriendList() {
        List<User> list = new ArrayList<>();
        for (User u : friendMap.values()) {
            String friendUid = u.getUserId();
            long timestamp = friendLastMsgMap.containsKey(friendUid) ? friendLastMsgMap.get(friendUid) : 0;
            boolean isPinned = pinnedDMs.contains(friendUid);
            if (timestamp > 0 || isPinned) {
                list.add(u);
            }
        }
        
        list.sort((u1, u2) -> {
            String id1 = u1.getUserId();
            String id2 = u2.getUserId();
            boolean pin1 = pinnedDMs.contains(id1);
            boolean pin2 = pinnedDMs.contains(id2);
            
            if (pin1 && pin2) {
                return Integer.compare(pinnedDMs.indexOf(id1), pinnedDMs.indexOf(id2));
            } else if (pin1) {
                return -1;
            } else if (pin2) {
                return 1;
            } else {
                long t1 = friendLastMsgMap.containsKey(id1) ? friendLastMsgMap.get(id1) : 0;
                long t2 = friendLastMsgMap.containsKey(id2) ? friendLastMsgMap.get(id2) : 0;
                return Long.compare(t2, t1); // Descending
            }
        });
        
        friendList.setValue(list);
    }

    public void updateUserStatus(String status) {
        FirebaseUser user = getCurrentUser();
        if (user == null) return;
        repository.updateStatus(user.getUid(), status, new HomeRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {}
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
            if (reg != null) reg.remove();
        }
        friendProfileListeners.clear();
    }
    
    private void clearMessageListeners() {
        for (Map.Entry<String, ValueEventListener> entry : messageListeners.entrySet()) {
            Firebase.getMessagesRefByRoom(entry.getKey()).removeEventListener(entry.getValue());
        }
        messageListeners.clear();
    }

    private void clearListeners() {
        FirebaseUser user = getCurrentUser();
        if (user != null) {
            repository.removeUserStatusListener(user.getUid(), statusListener);
            repository.removeFriendsListListener(user.getUid(), friendsListener);
        }
        if (userProfileListener != null) {
            userProfileListener.remove();
        }
        if (unreadNotificationsListener != null) {
            unreadNotificationsListener.remove();
            unreadNotificationsListener = null;
        }
        clearProfileListeners();
        clearMessageListeners();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        clearListeners();
    }
}
