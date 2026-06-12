package com.example.se114_callingsystem.features.call.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.example.se114_callingsystem.features.call.data.VoiceCallRepository;
import com.example.se114_callingsystem.network.BackendService;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.List;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class VoiceCallViewModel extends ViewModel {

    private final VoiceCallRepository repository;

    private final MutableLiveData<BackendService.AgoraTokenResponse> agoraToken = new MutableLiveData<>();
    private final MutableLiveData<List<ServerMember>> serverMembers = new MutableLiveData<>();
    private final MutableLiveData<String> operationStatus = new MutableLiveData<>();

    private ListenerRegistration membersListener;

    @Inject
    public VoiceCallViewModel(VoiceCallRepository repository) {
        this.repository = repository;
    }

    public LiveData<BackendService.AgoraTokenResponse> getAgoraToken() { return agoraToken; }
    public LiveData<List<ServerMember>> getServerMembers() { return serverMembers; }
    public LiveData<String> getOperationStatus() { return operationStatus; }

    public FirebaseUser getCurrentUser() {
        return repository.getCurrentUser();
    }

    public String getCurrentUserId() {
        return repository.getCurrentUserId();
    }

    public void initCall(String channelName, int uid) {
        repository.getFirebaseIdToken(new VoiceCallRepository.RepositoryCallback<String>() {
            @Override
            public void onSuccess(String idToken) {
                repository.fetchAgoraToken(idToken, channelName, uid, new VoiceCallRepository.RepositoryCallback<BackendService.AgoraTokenResponse>() {
                    @Override
                    public void onSuccess(BackendService.AgoraTokenResponse result) {
                        agoraToken.setValue(result);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        operationStatus.setValue("TOKEN_FETCH_FAILED: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                operationStatus.setValue("AUTH_TOKEN_FAILED: " + e.getMessage());
            }
        });
    }

    public void loadServerMembers(String serverId) {
        clearMembersListener();
        membersListener = repository.listenToServerMembers(serverId, new VoiceCallRepository.RealtimeCallback<List<ServerMember>>() {
            @Override
            public void onData(List<ServerMember> data) {
                serverMembers.setValue(data);
            }

            @Override
            public void onError(Exception e) {
                operationStatus.setValue("LOAD_MEMBERS_FAILED: " + e.getMessage());
            }
        });
    }

    public void updateVoiceChannel(String channelName) {
        String userId = getCurrentUserId();
        if (userId == null) return;
        repository.updateUserActiveChannel(userId, channelName, new VoiceCallRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {}
            @Override
            public void onFailure(Exception e) {
                operationStatus.setValue("UPDATE_CHANNEL_FAILED: " + e.getMessage());
            }
        });
    }

    public void clearVoiceChannel() {
        String userId = getCurrentUserId();
        if (userId == null) return;
        repository.clearUserActiveChannel(userId, new VoiceCallRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {}
            @Override
            public void onFailure(Exception e) {
                operationStatus.setValue("CLEAR_CHANNEL_FAILED: " + e.getMessage());
            }
        });
    }

    public void resetStatus() {
        operationStatus.setValue(null);
    }

    private void clearMembersListener() {
        if (membersListener != null) {
            membersListener.remove();
            membersListener = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        clearMembersListener();
    }
}
