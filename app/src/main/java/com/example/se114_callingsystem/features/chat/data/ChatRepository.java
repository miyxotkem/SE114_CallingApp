package com.example.se114_callingsystem.features.chat.data;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.example.se114_callingsystem.core.model.Message;
import javax.inject.Inject;

public class ChatRepository {

    private final FirebaseDatabase realtimeDb;
    private final FirebaseFirestore firestore;

    @Inject
    public ChatRepository(FirebaseDatabase realtimeDb, FirebaseFirestore firestore) {
        this.realtimeDb = realtimeDb;
        this.firestore = firestore;
    }

    public DatabaseReference getMessagesRef(String groupId) {
        return realtimeDb.getReference("chats").child(groupId);
    }

    public DatabaseReference getTypingRef(String groupId) {
        return realtimeDb.getReference("chat_typing").child(groupId);
    }

    public DatabaseReference getTypingUserRef(String groupId, String userId) {
        return realtimeDb.getReference("chat_typing").child(groupId).child(userId);
    }

    public Query getMembersQuery(String serverId) {
        return firestore.collection("servers").document(serverId).collection("members");
    }

    public void sendMessage(String groupId, Message message, OnCompleteListener<Void> listener) {
        DatabaseReference messagesRef = getMessagesRef(groupId);
        DatabaseReference newMsgRef;
        if (message.getMessageId() != null && !message.getMessageId().trim().isEmpty()) {
            newMsgRef = messagesRef.child(message.getMessageId());
        } else {
            newMsgRef = messagesRef.push();
            message.setMessageId(newMsgRef.getKey());
        }
        newMsgRef.setValue(message).addOnCompleteListener(listener);
    }

    public void deleteMessage(String groupId, String messageId) {
        if (messageId == null) return;
        getMessagesRef(groupId).child(messageId).child("deleted").setValue(true);
    }

    public void updateReaction(String groupId, String messageId, String emoji) {
        if (messageId == null) return;
        getMessagesRef(groupId).child(messageId).child("reactionEmoji").setValue(emoji);
    }

    public void togglePin(String groupId, String messageId, boolean pinned) {
        if (messageId == null) return;
        getMessagesRef(groupId).child(messageId).child("pinned").setValue(pinned);
    }

    public void updateTypingStatus(String groupId, String userId, boolean typing) {
        DatabaseReference ref = getTypingUserRef(groupId, userId);
        if (typing) {
            ref.setValue(true);
            ref.onDisconnect().removeValue();
        } else {
            ref.removeValue();
        }
    }

    public void updateMessage(String groupId, String messageId, java.util.Map<String, Object> updates) {
        if (messageId == null) return;
        getMessagesRef(groupId).child(messageId).updateChildren(updates);
    }
}
