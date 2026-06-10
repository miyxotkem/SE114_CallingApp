package com.example.se114_callingsystem.features.auth.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.se114_callingsystem.features.auth.data.LoginRepository;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseUser;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class LoginViewModel extends ViewModel {

    private final LoginRepository repository;
    private final MutableLiveData<String> loginResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    @Inject
    public LoginViewModel(LoginRepository repository) {
        this.repository = repository;
    }

    public LiveData<String> getLoginResult() {
        return loginResult;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public void login(String email, String password) {
        isLoading.setValue(true);
        repository.loginWithEmail(email, password, new LoginRepository.LoginCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                isLoading.setValue(false);
                loginResult.setValue("SUCCESS");
            }

            @Override
            public void onFailure(Exception exception) {
                isLoading.setValue(false);
                loginResult.setValue(exception != null ? exception.getMessage() : "Unknown Error");
            }
        });
    }

    public void loginWithGoogle(AuthCredential credential) {
        isLoading.setValue(true);
        repository.loginWithGoogle(credential, new LoginRepository.LoginCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                repository.checkAndSaveUserToFirestore(user, new LoginRepository.FirestoreCallback() {
                    @Override
                    public void onSuccess() {
                        isLoading.setValue(false);
                        loginResult.setValue("SUCCESS");
                    }

                    @Override
                    public void onFailure(Exception exception) {
                        isLoading.setValue(false);
                        loginResult.setValue(exception != null ? exception.getMessage() : "Failed to save user info to Firestore");
                    }
                });
            }

            @Override
            public void onFailure(Exception exception) {
                isLoading.setValue(false);
                loginResult.setValue(exception != null ? exception.getMessage() : "Google Authentication Failed");
            }
        });
    }
}
