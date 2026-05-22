package com.example.se114_callingsystem.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.friend.ManageFriendsActivity;
import com.example.se114_callingsystem.model.Server;
import com.example.se114_callingsystem.profile.ProfileActivity;
import com.example.se114_callingsystem.server.CreateServerDialog;
import com.example.se114_callingsystem.server.ServerAdapter;
import com.example.se114_callingsystem.service.MessageNotificationService;
import com.example.se114_callingsystem.util.ThemeHelper;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class HomePageActivity extends AppCompatActivity {

    private ServerAdapter adapter;
    private List<Server> serverList;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_page);

        db = FirebaseFirestore.getInstance();
        serverList = new ArrayList<>();
        adapter = new ServerAdapter(serverList);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        fetchServers();

        checkNotificationPermission();
        startMessageNotificationService();

        MaterialCardView cardServerCreate = findViewById(R.id.mcvServerCreate);
        cardServerCreate.setOnClickListener(v -> {
            CreateServerDialog dialog = new CreateServerDialog();
            dialog.show(getSupportFragmentManager(), "Server_on_create");
        });

        MaterialCardView btnManageFriends = findViewById(R.id.btnManageFriends);
        if (btnManageFriends != null) {
            btnManageFriends.setOnClickListener(v -> {
                Intent intent = new Intent(this, ManageFriendsActivity.class);
                startActivity(intent);
            });
        }

        MaterialCardView btnProfile = findViewById(R.id.btnProfile);
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> {
                Intent intent = new Intent(this, ProfileActivity.class);
                startActivity(intent);
            });
        }
    }

    private void checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void startMessageNotificationService() {
        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
            Intent serviceIntent = new Intent(this, MessageNotificationService.class);
            startService(serviceIntent);
        }
    }

    private void fetchServers() {
        String currentUserUid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        if (currentUserUid.isEmpty()) return;

        db.collection("servers")
          .whereArrayContains("members", currentUserUid)
          .addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.e("Firestore", "Error: " + error.getMessage());
                return;
            }
            if (value != null) {
                serverList.clear();
                for (com.google.firebase.firestore.DocumentSnapshot doc : value) {
                    Server server = doc.toObject(Server.class);
                    serverList.add(server);
                }
                adapter.notifyDataSetChanged();
            }
        });
    }
}
