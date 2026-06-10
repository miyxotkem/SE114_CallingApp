package com.example.se114_callingsystem.features.server.viewmodel;

import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.se114_callingsystem.core.model.CallChannel;
import com.example.se114_callingsystem.core.model.ChatChannel;
import com.example.se114_callingsystem.core.model.PostChannel;
import com.example.se114_callingsystem.core.model.Server;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.example.se114_callingsystem.features.server.data.ServerRepository;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.List;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ServerViewModel extends ViewModel {

    private final ServerRepository repository;

    private final MutableLiveData<Server> serverInfo = new MutableLiveData<>();
    private final MutableLiveData<ServerMember> memberRole = new MutableLiveData<>();
    private final MutableLiveData<List<ChatChannel>> chatChannels = new MutableLiveData<>();
    private final MutableLiveData<List<CallChannel>> callChannels = new MutableLiveData<>();
    private final MutableLiveData<List<PostChannel>> postChannels = new MutableLiveData<>();
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLeftOrDeleted = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isUploaded = new MutableLiveData<>(false);

    private ListenerRegistration chatListener;
    private ListenerRegistration callListener;
    private ListenerRegistration postListener;
    private ListenerRegistration roleListener;

    @Inject
    public ServerViewModel(ServerRepository repository) {
        this.repository = repository;
    }

    public LiveData<Server> getServerInfo() {
        return serverInfo;
    }

    public LiveData<ServerMember> getMemberRole() {
        return memberRole;
    }

    public LiveData<List<ChatChannel>> getChatChannels() {
        return chatChannels;
    }

    public LiveData<List<CallChannel>> getCallChannels() {
        return callChannels;
    }

    public LiveData<List<PostChannel>> getPostChannels() {
        return postChannels;
    }

    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }

    public LiveData<Boolean> getIsLeftOrDeleted() {
        return isLeftOrDeleted;
    }

    public LiveData<Boolean> getIsUploaded() {
        return isUploaded;
    }

    public void initServer(String serverId, String userId) {
        // Clear old listeners if any
        clearListeners();

        // 1. Get initial Server Info
        loadServerInfo(serverId);

        // 2. Start realtime database listeners
        chatListener = repository.listenChatChannels(serverId, new ServerRepository.RealtimeCallback<List<ChatChannel>>() {
            @Override
            public void onData(List<ChatChannel> data) {
                chatChannels.setValue(data);
            }

            @Override
            public void onError(Exception e) {
                statusMessage.setValue("Failed to load chat channels: " + e.getMessage());
            }
        });

        callListener = repository.listenCallChannels(serverId, new ServerRepository.RealtimeCallback<List<CallChannel>>() {
            @Override
            public void onData(List<CallChannel> data) {
                callChannels.setValue(data);
            }

            @Override
            public void onError(Exception e) {
                statusMessage.setValue("Failed to load call channels: " + e.getMessage());
            }
        });

        postListener = repository.listenPostChannels(serverId, new ServerRepository.RealtimeCallback<List<PostChannel>>() {
            @Override
            public void onData(List<PostChannel> data) {
                postChannels.setValue(data);
            }

            @Override
            public void onError(Exception e) {
                statusMessage.setValue("Failed to load post channels: " + e.getMessage());
            }
        });

        if (userId != null && !userId.isEmpty()) {
            roleListener = repository.listenUserRole(serverId, userId, new ServerRepository.RealtimeCallback<ServerMember>() {
                @Override
                public void onData(ServerMember data) {
                    memberRole.setValue(data);
                }

                @Override
                public void onError(Exception e) {
                    statusMessage.setValue("Failed to load user role: " + e.getMessage());
                }
            });
        }
    }

    public void loadServerInfo(String serverId) {
        repository.getServerInfo(serverId, new ServerRepository.RepositoryCallback<Server>() {
            @Override
            public void onSuccess(Server result) {
                serverInfo.setValue(result);
            }

            @Override
            public void onFailure(Exception e) {
                statusMessage.setValue("Failed to get server details: " + e.getMessage());
            }
        });
    }

    public void createChannel(String type, String serverId, String name, int orderIndex) {
        String collection = "chat".equals(type) ? "Channels" : ("call".equals(type) ? "CallChannels" : "PostChannels");
        String nameField = "chat".equals(type) ? "chatName" : ("call".equals(type) ? "callName" : "name");

        repository.checkChannelNameExists(collection, serverId, nameField, name, new ServerRepository.RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean exists) {
                if (exists) {
                    statusMessage.setValue("CHANNEL_EXISTS");
                } else {
                    if ("chat".equals(type)) {
                        repository.createChatChannel(new ChatChannel(name, serverId, orderIndex), new ServerRepository.RepositoryCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                statusMessage.setValue("CREATE_SUCCESS");
                            }

                            @Override
                            public void onFailure(Exception e) {
                                statusMessage.setValue("Failed to create chat channel: " + e.getMessage());
                            }
                        });
                    } else if ("call".equals(type)) {
                        repository.createCallChannel(new CallChannel(name, serverId, orderIndex), new ServerRepository.RepositoryCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                statusMessage.setValue("CREATE_SUCCESS");
                            }

                            @Override
                            public void onFailure(Exception e) {
                                statusMessage.setValue("Failed to create call channel: " + e.getMessage());
                            }
                        });
                    } else {
                        repository.createPostChannel(new PostChannel(name, serverId, orderIndex), new ServerRepository.RepositoryCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                statusMessage.setValue("CREATE_SUCCESS");
                            }

                            @Override
                            public void onFailure(Exception e) {
                                statusMessage.setValue("Failed to create post channel: " + e.getMessage());
                            }
                        });
                    }
                }
            }

            @Override
            public void onFailure(Exception e) {
                statusMessage.setValue("Error checking channel name: " + e.getMessage());
            }
        });
    }

    public void renameChannel(String type, String serverId, String channelId, String currentName, String newName) {
        String collection = "chat".equals(type) ? "Channels" : ("call".equals(type) ? "CallChannels" : "PostChannels");
        String nameField = "chat".equals(type) ? "chatName" : ("call".equals(type) ? "callName" : "name");

        if (newName.isEmpty() || newName.equalsIgnoreCase(currentName)) {
            return;
        }

        repository.checkChannelNameExists(collection, serverId, nameField, newName, new ServerRepository.RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean exists) {
                if (exists) {
                    statusMessage.setValue("CHANNEL_EXISTS");
                } else {
                    repository.renameChannel(collection, channelId, nameField, newName, new ServerRepository.RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            statusMessage.setValue("RENAME_SUCCESS");
                        }

                        @Override
                        public void onFailure(Exception e) {
                            statusMessage.setValue("Failed to rename channel: " + e.getMessage());
                        }
                    });
                }
            }

            @Override
            public void onFailure(Exception e) {
                statusMessage.setValue("Error checking channel name: " + e.getMessage());
            }
        });
    }

    public void removeChannel(String type, String channelId) {
        String collection = "chat".equals(type) ? "Channels" : ("call".equals(type) ? "CallChannels" : "PostChannels");
        repository.removeChannel(collection, channelId, new ServerRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                statusMessage.setValue("REMOVE_SUCCESS");
            }

            @Override
            public void onFailure(Exception e) {
                statusMessage.setValue("Failed to remove channel: " + e.getMessage());
            }
        });
    }

    public void updateChannelsOrder(String type, List<?> list) {
        if ("chat".equals(type)) {
            repository.updateChatChannelsOrder((List<ChatChannel>) list, new ServerRepository.RepositoryCallback<Void>() {
                @Override public void onSuccess(Void result) {}
                @Override public void onFailure(Exception e) { statusMessage.setValue("Failed to update chat channel orders"); }
            });
        } else if ("call".equals(type)) {
            repository.updateCallChannelsOrder((List<CallChannel>) list, new ServerRepository.RepositoryCallback<Void>() {
                @Override public void onSuccess(Void result) {}
                @Override public void onFailure(Exception e) { statusMessage.setValue("Failed to update call channel orders"); }
            });
        } else {
            repository.updatePostChannelsOrder((List<PostChannel>) list, new ServerRepository.RepositoryCallback<Void>() {
                @Override public void onSuccess(Void result) {}
                @Override public void onFailure(Exception e) { statusMessage.setValue("Failed to update post channel orders"); }
            });
        }
    }

    public void updateServerDetails(String serverId, String newName, String newPurpose) {
        repository.updateServerDetails(serverId, newName, newPurpose, new ServerRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                statusMessage.setValue("UPDATE_SERVER_SUCCESS");
                loadServerInfo(serverId);
            }

            @Override
            public void onFailure(Exception e) {
                statusMessage.setValue("Failed to update server: " + e.getMessage());
            }
        });
    }

    public void deleteServer(String serverId) {
        repository.deleteServer(serverId, new ServerRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                statusMessage.setValue("DELETE_SERVER_SUCCESS");
                isLeftOrDeleted.setValue(true);
            }

            @Override
            public void onFailure(Exception e) {
                statusMessage.setValue("Failed to delete server: " + e.getMessage());
            }
        });
    }

    public void uploadServerIcon(String serverId, Uri uri) {
        repository.uploadServerIcon(serverId, uri, new ServerRepository.RepositoryCallback<String>() {
            @Override
            public void onSuccess(String downloadUrl) {
                repository.updateServerIconUrl(serverId, downloadUrl, new ServerRepository.RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        statusMessage.setValue("UPLOAD_ICON_SUCCESS");
                        isUploaded.setValue(true);
                        loadServerInfo(serverId);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        statusMessage.setValue("Failed to save server icon url: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                statusMessage.setValue("Failed to upload image: " + e.getMessage());
            }
        });
    }

    public void removeServerAvatar(String serverId) {
        repository.updateServerIconUrl(serverId, null, new ServerRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                statusMessage.setValue("REMOVE_ICON_SUCCESS");
                loadServerInfo(serverId);
            }

            @Override
            public void onFailure(Exception e) {
                statusMessage.setValue("Failed to remove server icon: " + e.getMessage());
            }
        });
    }

    public void leaveServer(String serverId, String userId) {
        repository.checkMembersCount(serverId, new ServerRepository.RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer count) {
                // If the user is the only admin/owner, check this
                repository.getMembersList(serverId, new ServerRepository.RepositoryCallback<List<ServerMember>>() {
                    @Override
                    public void onSuccess(List<ServerMember> members) {
                        boolean isAdminOrOwner = false;
                        ServerMember currentMember = null;
                        for (ServerMember m : members) {
                            if (userId.equals(m.getUserId())) {
                                currentMember = m;
                                break;
                            }
                        }
                        if (currentMember != null) {
                            isAdminOrOwner = "owner".equals(currentMember.getRole()) || "admin".equals(currentMember.getRole());
                        }

                        boolean canLeave = true;
                        if (isAdminOrOwner) {
                            int adminOwnerCount = 0;
                            for (ServerMember m : members) {
                                if ("owner".equals(m.getRole()) || "admin".equals(m.getRole())) {
                                    adminOwnerCount++;
                                }
                            }
                            if (adminOwnerCount <= 1) {
                                canLeave = false;
                            }
                        }

                        if (!canLeave) {
                            statusMessage.setValue("LEAVE_FAILED_LAST_ADMIN");
                        } else {
                            repository.leaveServer(serverId, userId, new ServerRepository.RepositoryCallback<Void>() {
                                @Override
                                public void onSuccess(Void result) {
                                    statusMessage.setValue("LEAVE_SUCCESS");
                                    isLeftOrDeleted.setValue(true);
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    statusMessage.setValue("Failed to leave server: " + e.getMessage());
                                }
                            });
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        statusMessage.setValue("Failed to fetch server members: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                statusMessage.setValue("Failed to check members count: " + e.getMessage());
            }
        });
    }

    public void resetStatus() {
        statusMessage.setValue(null);
    }

    public void resetUploaded() {
        isUploaded.setValue(false);
    }

    private void clearListeners() {
        if (chatListener != null) {
            chatListener.remove();
            chatListener = null;
        }
        if (callListener != null) {
            callListener.remove();
            callListener = null;
        }
        if (postListener != null) {
            postListener.remove();
            postListener = null;
        }
        if (roleListener != null) {
            roleListener.remove();
            roleListener = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        clearListeners();
    }
}
