package com.example.se114_callingsystem.features.chat.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.se114_callingsystem.core.model.Message;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.example.se114_callingsystem.features.chat.data.ChatRepository;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ChatViewModel extends ViewModel {

    private final ChatRepository repository;

    private final MutableLiveData<List<Message>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<ServerMember>> serverMembers = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> typingUsers = new MutableLiveData<>(new ArrayList<>());

    private DatabaseReference messagesRef;
    private ValueEventListener messagesListener;

    private DatabaseReference typingRef;
    private ValueEventListener typingListener;

    private ListenerRegistration membersListener;

    private String currentGroupId;
    private String currentServerId;
    private String currentSenderId;

    @Inject
    public ChatViewModel(ChatRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<Message>> getMessages() {
        return messages;
    }

    public LiveData<List<ServerMember>> getServerMembers() {
        return serverMembers;
    }

    public LiveData<List<String>> getTypingUsers() {
        return typingUsers;
    }

    public void startChatSession(String groupId, String serverId, String senderId) {
        this.currentGroupId = groupId;
        this.currentServerId = serverId;
        this.currentSenderId = senderId;

        // Dọn dẹp listener cũ trước khi lắng nghe nhóm mới
        stopChatSession();

        // 1. Lắng nghe tin nhắn mới thời gian thực
        messagesRef = repository.getMessagesRef(groupId);
        messagesListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<Message> list = new ArrayList<>();
                for (DataSnapshot data : snapshot.getChildren()) {
                    Message m = data.getValue(Message.class);
                    if (m != null) {
                        m.setMessageId(data.getKey());
                        list.add(m);
                    }
                }
                messages.setValue(list);
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        };
        messagesRef.addValueEventListener(messagesListener);

        // 2. Lắng nghe trạng thái gõ chữ (typing indicator)
        typingRef = repository.getTypingRef(groupId);
        typingListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<String> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Boolean isTyping = child.getValue(Boolean.class);
                    if (isTyping != null && isTyping) {
                        String userId = child.getKey();
                        if (userId != null && !userId.equals(currentSenderId)) {
                            list.add(userId);
                        }
                    }
                }
                typingUsers.setValue(list);
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        };
        typingRef.addValueEventListener(typingListener);

        // 3. Lắng nghe danh sách thành viên Server để hỗ trợ tính năng Mention (@)
        if (serverId != null && !serverId.trim().isEmpty()) {
            membersListener = repository.getMembersQuery(serverId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) return;
                    if (snapshots != null) {
                        List<ServerMember> list = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            ServerMember m = doc.toObject(ServerMember.class);
                            if (m != null) {
                                list.add(m);
                            }
                        }
                        serverMembers.setValue(list);
                    }
                });
        } else {
            serverMembers.setValue(new ArrayList<>());
        }
    }

    public void sendMessage(Message message, com.google.android.gms.tasks.OnCompleteListener<Void> listener) {
        if (currentGroupId == null) return;
        repository.sendMessage(currentGroupId, message, listener);
    }

    public void deleteMessage(String messageId) {
        if (currentGroupId == null) return;
        repository.deleteMessage(currentGroupId, messageId);
    }

    public void updateReaction(String messageId, String emoji) {
        if (currentGroupId == null) return;
        repository.updateReaction(currentGroupId, messageId, emoji);
    }

    public void togglePin(String messageId, boolean pinned) {
        if (currentGroupId == null) return;
        repository.togglePin(currentGroupId, messageId, pinned);
    }

    public void setTypingStatus(boolean typing) {
        if (currentGroupId == null || currentSenderId == null) return;
        repository.updateTypingStatus(currentGroupId, currentSenderId, typing);
    }

    public void updateMessage(String messageId, java.util.Map<String, Object> updates) {
        if (currentGroupId == null) return;
        repository.updateMessage(currentGroupId, messageId, updates);
    }

    public void stopChatSession() {
        if (messagesRef != null && messagesListener != null) {
            messagesRef.removeEventListener(messagesListener);
            messagesRef = null;
            messagesListener = null;
        }

        if (typingRef != null && typingListener != null) {
            typingRef.removeEventListener(typingListener);
            typingRef = null;
            typingListener = null;
        }

        if (membersListener != null) {
            membersListener.remove();
            membersListener = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopChatSession();
    }
}
