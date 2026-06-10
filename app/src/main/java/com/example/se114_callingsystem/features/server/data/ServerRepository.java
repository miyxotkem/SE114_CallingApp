package com.example.se114_callingsystem.features.server.data;

import android.net.Uri;
import com.example.se114_callingsystem.core.model.CallChannel;
import com.example.se114_callingsystem.core.model.ChatChannel;
import com.example.se114_callingsystem.core.model.PostChannel;
import com.example.se114_callingsystem.core.model.Server;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

public class ServerRepository {

    private final FirebaseFirestore db;
    private final FirebaseStorage storage;

    @Inject
    public ServerRepository(FirebaseFirestore db, FirebaseStorage storage) {
        this.db = db;
        this.storage = storage;
    }

    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onFailure(Exception e);
    }

    public interface RealtimeCallback<T> {
        void onData(T data);
        void onError(Exception e);
    }

    public void getServerInfo(String serverId, RepositoryCallback<Server> callback) {
        db.collection("servers").document(serverId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Server server = doc.toObject(Server.class);
                        callback.onSuccess(server);
                    } else {
                        callback.onFailure(new Exception("Server not found"));
                    }
                })
                .addOnFailureListener(callback::onFailure);
    }

    public ListenerRegistration listenChatChannels(String serverId, RealtimeCallback<List<ChatChannel>> callback) {
        return db.collection("Channels")
                .whereEqualTo("serverId", serverId)
                .orderBy("orderIndex", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        callback.onError(e);
                        return;
                    }
                    List<ChatChannel> chatList = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots) {
                            ChatChannel c = doc.toObject(ChatChannel.class);
                            if (c != null) {
                                c.setChatId(doc.getId());
                                chatList.add(c);
                            }
                        }
                    }
                    callback.onData(chatList);
                });
    }

    public ListenerRegistration listenCallChannels(String serverId, RealtimeCallback<List<CallChannel>> callback) {
        return db.collection("CallChannels")
                .whereEqualTo("serverId", serverId)
                .orderBy("orderIndex", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        callback.onError(e);
                        return;
                    }
                    List<CallChannel> callList = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots) {
                            CallChannel c = doc.toObject(CallChannel.class);
                            if (c != null) {
                                c.setCallId(doc.getId());
                                callList.add(c);
                            }
                        }
                    }
                    callback.onData(callList);
                });
    }

    public ListenerRegistration listenPostChannels(String serverId, RealtimeCallback<List<PostChannel>> callback) {
        return db.collection("PostChannels")
                .whereEqualTo("serverId", serverId)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        callback.onError(e);
                        return;
                    }
                    List<PostChannel> postList = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots) {
                            PostChannel c = doc.toObject(PostChannel.class);
                            if (c != null) {
                                c.setId(doc.getId());
                                postList.add(c);
                            }
                        }
                    }
                    Collections.sort(postList, (a, b) -> Integer.compare(a.getOrderIndex(), b.getOrderIndex()));
                    callback.onData(postList);
                });
    }

    public ListenerRegistration listenUserRole(String serverId, String userId, RealtimeCallback<ServerMember> callback) {
        return db.collection("servers").document(serverId).collection("members").document(userId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null) {
                        callback.onError(e);
                        return;
                    }
                    if (doc != null && doc.exists()) {
                        ServerMember m = doc.toObject(ServerMember.class);
                        callback.onData(m);
                    } else {
                        callback.onData(null);
                    }
                });
    }

    public void updateChatChannelsOrder(List<ChatChannel> channels, RepositoryCallback<Void> callback) {
        WriteBatch batch = db.batch();
        for (int i = 0; i < channels.size(); i++) {
            batch.update(db.collection("Channels").document(channels.get(i).getChatId()), "orderIndex", i);
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void updateCallChannelsOrder(List<CallChannel> channels, RepositoryCallback<Void> callback) {
        WriteBatch batch = db.batch();
        for (int i = 0; i < channels.size(); i++) {
            batch.update(db.collection("CallChannels").document(channels.get(i).getCallId()), "orderIndex", i);
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void updatePostChannelsOrder(List<PostChannel> channels, RepositoryCallback<Void> callback) {
        WriteBatch batch = db.batch();
        for (int i = 0; i < channels.size(); i++) {
            batch.update(db.collection("PostChannels").document(channels.get(i).getId()), "orderIndex", i);
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void checkChannelNameExists(String collection, String serverId, String nameField, String name, RepositoryCallback<Boolean> callback) {
        db.collection(collection)
                .whereEqualTo("serverId", serverId)
                .whereEqualTo(nameField, name)
                .get()
                .addOnSuccessListener(snaps -> callback.onSuccess(!snaps.isEmpty()))
                .addOnFailureListener(callback::onFailure);
    }

    public void createChatChannel(ChatChannel channel, RepositoryCallback<Void> callback) {
        db.collection("Channels").add(channel)
                .addOnSuccessListener(doc -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void createCallChannel(CallChannel channel, RepositoryCallback<Void> callback) {
        db.collection("CallChannels").add(channel)
                .addOnSuccessListener(doc -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void createPostChannel(PostChannel channel, RepositoryCallback<Void> callback) {
        db.collection("PostChannels").add(channel)
                .addOnSuccessListener(doc -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void renameChannel(String collection, String channelId, String nameField, String newName, RepositoryCallback<Void> callback) {
        db.collection(collection).document(channelId).update(nameField, newName)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void removeChannel(String collection, String channelId, RepositoryCallback<Void> callback) {
        db.collection(collection).document(channelId).delete()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void updateServerDetails(String serverId, String newName, String newPurpose, RepositoryCallback<Void> callback) {
        db.collection("servers").document(serverId)
                .update("serverName", newName, "purpose", newPurpose)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void deleteServer(String serverId, RepositoryCallback<Void> callback) {
        db.collection("servers").document(serverId).delete()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void uploadServerIcon(String serverId, Uri uri, RepositoryCallback<String> callback) {
        StorageReference storageRef = storage.getReference().child("server_icons/" + serverId + "_" + System.currentTimeMillis() + ".jpg");
        storageRef.putFile(uri)
                .addOnSuccessListener(taskSnapshot -> {
                    storageRef.getDownloadUrl()
                            .addOnSuccessListener(downloadUri -> callback.onSuccess(downloadUri.toString()))
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void updateServerIconUrl(String serverId, String iconUrl, RepositoryCallback<Void> callback) {
        db.collection("servers").document(serverId).update("iconUrl", iconUrl)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void checkMembersCount(String serverId, RepositoryCallback<Integer> callback) {
        db.collection("servers").document(serverId).collection("members").get()
                .addOnSuccessListener(snaps -> callback.onSuccess(snaps.size()))
                .addOnFailureListener(callback::onFailure);
    }

    public void getMembersList(String serverId, RepositoryCallback<List<ServerMember>> callback) {
        db.collection("servers").document(serverId).collection("members").get()
                .addOnSuccessListener(snaps -> {
                    List<ServerMember> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snaps) {
                        ServerMember m = doc.toObject(ServerMember.class);
                        if (m != null) list.add(m);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void leaveServer(String serverId, String userId, RepositoryCallback<Void> callback) {
        // 1. Remove serverId from user's serverOrder list
        db.collection("users").document(userId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                List<String> order = (List<String>) doc.get("serverOrder");
                if (order != null && order.contains(serverId)) {
                    order.remove(serverId);
                    db.collection("users").document(userId).update("serverOrder", order);
                }
            }
        });

        // 2. Remove from servers members array
        db.collection("servers").document(serverId).update("members", FieldValue.arrayRemove(userId));

        // 3. Remove from members subcollection
        db.collection("servers").document(serverId).collection("members").document(userId).delete()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
}
