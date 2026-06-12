package com.example.se114_callingsystem.features.friend.viewmodel;

import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.se114_callingsystem.core.model.User;
import com.example.se114_callingsystem.features.friend.data.ProfileRepository;
import com.google.firebase.auth.FirebaseUser;
import java.util.Map;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ProfileViewModel extends ViewModel {

    private final ProfileRepository repository;
    
    private final MutableLiveData<User> userProfile = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isSignedOut = new MutableLiveData<>(false);

    @Inject
    public ProfileViewModel(ProfileRepository repository) {
        this.repository = repository;
    }

    public LiveData<User> getUserProfile() {
        return userProfile;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }

    public LiveData<Boolean> getIsSignedOut() {
        return isSignedOut;
    }

    public FirebaseUser getCurrentUser() {
        return repository.getCurrentUser();
    }

    public void loadProfile(String userId) {
        if (userId == null || userId.isEmpty()) return;
        
        isLoading.setValue(true);
        repository.loadUserProfile(userId, new ProfileRepository.ProfileCallback<User>() {
            @Override
            public void onSuccess(User result) {
                isLoading.setValue(false);
                userProfile.setValue(result);
            }

            @Override
            public void onFailure(Exception exception) {
                isLoading.setValue(false);
                statusMessage.setValue("Failed to load profile: " + exception.getMessage());
            }
        });
    }

    public void saveProfile(String userId, Map<String, Object> updates, Uri avatarUri, Uri coverUri) {
        isLoading.setValue(true);
        
        // Step 1: Upload avatar if needed
        repository.uploadImage(avatarUri, "avatar_" + userId, new ProfileRepository.ProfileCallback<String>() {
            @Override
            public void onSuccess(String avatarUrl) {
                if (avatarUrl != null) {
                    updates.put("profilePic", avatarUrl);
                }
                
                // Step 2: Upload cover if needed
                repository.uploadImage(coverUri, "cover_" + userId, new ProfileRepository.ProfileCallback<String>() {
                    @Override
                    public void onSuccess(String coverUrl) {
                        if (coverUrl != null) {
                            updates.put("coverPic", coverUrl);
                        }
                        
                        // Step 3: Save to Firestore
                        repository.updateUserProfile(userId, updates, new ProfileRepository.ProfileCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                isLoading.setValue(false);
                                statusMessage.setValue("SUCCESS");
                            }

                            @Override
                            public void onFailure(Exception exception) {
                                isLoading.setValue(false);
                                statusMessage.setValue("Failed to save changes: " + exception.getMessage());
                            }
                        });
                    }

                    @Override
                    public void onFailure(Exception exception) {
                        isLoading.setValue(false);
                        statusMessage.setValue("Failed to upload cover photo: " + exception.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(Exception exception) {
                isLoading.setValue(false);
                statusMessage.setValue("Failed to upload avatar: " + exception.getMessage());
            }
        });
    }

    public void saveProfileOfflineOnly(String userId, Map<String, Object> updates) {
        isLoading.setValue(true);
        repository.updateUserProfile(userId, updates, new ProfileRepository.ProfileCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                isLoading.setValue(false);
                statusMessage.setValue("OFFLINE_SUCCESS");
            }

            @Override
            public void onFailure(Exception exception) {
                isLoading.setValue(false);
                statusMessage.setValue("Failed to save offline changes: " + exception.getMessage());
            }
        });
    }

    public void signOut() {
        repository.signOut();
        isSignedOut.setValue(true);
    }

    public void resetStatus() {
        statusMessage.setValue(null);
    }
}
