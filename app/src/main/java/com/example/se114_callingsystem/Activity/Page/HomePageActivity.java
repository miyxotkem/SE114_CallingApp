package com.example.se114_callingsystem.Activity.Page;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;

import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.Util.ThemeHelper;
import com.example.se114_callingsystem.Model.Server;
import com.example.se114_callingsystem.Adapter.ServerAdapter;
import com.example.se114_callingsystem.Activity.Component.create_server;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import android.content.Intent;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

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
            create_server dialog = new create_server();
            dialog.show(getSupportFragmentManager(), "Server_on_create");
        });

        MaterialCardView btnManageFriends = findViewById(R.id.btnManageFriends);
        if (btnManageFriends != null) {
            btnManageFriends.setOnClickListener(v -> {
                Intent intent = new Intent(this, com.example.se114_callingsystem.Activity.Page.ManageFriendsActivity.class);
                startActivity(intent);
            });
        }

        MaterialCardView btnLogout = findViewById(R.id.btnLogout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                stopService(new Intent(this, MessageNotificationService.class));
                FirebaseAuth.getInstance().signOut();
                GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
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
        db.collection("servers").addSnapshotListener((value, error) -> {
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