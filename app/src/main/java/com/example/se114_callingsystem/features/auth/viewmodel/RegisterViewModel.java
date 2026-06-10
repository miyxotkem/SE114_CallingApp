package com.example.se114_callingsystem.features.auth.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.se114_callingsystem.features.auth.data.RegisterRepository;
import com.google.firebase.auth.FirebaseUser;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class RegisterViewModel extends ViewModel {

    private final RegisterRepository repository;
    private final MutableLiveData<String> registerResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    @Inject
    public RegisterViewModel(RegisterRepository repository) {
        this.repository = repository;
    }

    public LiveData<String> getRegisterResult() {
        return registerResult;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public void register(String email, String password, String username) {
        isLoading.setValue(true);
        repository.registerUser(email, password, username, new RegisterRepository.RegisterCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                isLoading.setValue(false);
                registerResult.setValue("SUCCESS");
            }

            @Override
            public void onFailure(Exception exception) {
                isLoading.setValue(false);
                if (exception != null && exception.getMessage() != null) {
                    registerResult.setValue(exception.getMessage());
                } else {
                    registerResult.setValue("Registration failed due to an unknown error.");
                }
            }
        });
    }

    public void resetResult() {
        registerResult.setValue(null);
    }
}
