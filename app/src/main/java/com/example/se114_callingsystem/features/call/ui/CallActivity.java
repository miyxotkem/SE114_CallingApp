package com.example.se114_callingsystem.features.call.ui;

import android.content.res.Configuration;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.se114_callingsystem.R;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class CallActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        if (savedInstanceState == null) {
            String channelName = getIntent().getStringExtra("CALL_CHANNEL_NAME");
            String serverId = getIntent().getStringExtra("SERVER_ID");
            String serverColor = getIntent().getStringExtra("SERVER_COLOR");
            boolean isCaller = getIntent().getBooleanExtra("IS_CALLER", false);
            String callType = getIntent().getStringExtra("CALL_TYPE");

            if ("com.example.se114_callingsystem.ACTION_ANSWER_CALL".equals(getIntent().getAction())) {
                String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null 
                        ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                if (currentUserId != null) {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(currentUserId)
                            .collection("incomingCall")
                            .document("activeCall")
                            .update("status", "answered");
                }
            }

            VoiceCallFragment fragment = new VoiceCallFragment();
            Bundle args = new Bundle();
            args.putString("CALL_CHANNEL_NAME", channelName);
            args.putString("SERVER_ID", serverId);
            args.putString("SERVER_COLOR", serverColor);
            args.putBoolean("IS_CALLER", isCaller);
            args.putString("CALL_TYPE", callType);
            fragment.setArguments(args);

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.call_fragment_container, fragment, "VoiceCallFragment")
                    .commit();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("VoiceCallFragment");
        if (fragment instanceof VoiceCallFragment) {
            ((VoiceCallFragment) fragment).onPictureInPictureModeChanged(isInPictureInPictureMode);
        }
    }
}
