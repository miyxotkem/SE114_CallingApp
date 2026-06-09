package com.example.se114_callingsystem;

import android.os.Bundle;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.example.se114_callingsystem.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private android.os.Handler idleHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable idleRunnable = () -> setAppStatus("idle");
    private String lastSetStatus = "";
    private com.example.se114_callingsystem.core.util.NetworkMonitor networkMonitor;
    private boolean isFirstNetworkCheck = true;
    private com.google.firebase.firestore.ListenerRegistration unreadNotificationsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.example.se114_callingsystem.core.util.ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupRealtimePresence();

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0); // Bottom is handled by bottom nav
            return insets;
        });

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNav, navController);
            
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int id = destination.getId();
                if (id == R.id.nav_home || id == R.id.nav_notifications || id == R.id.nav_profile) {
                    binding.bottomNav.setVisibility(View.VISIBLE);
                } else {
                    binding.bottomNav.setVisibility(View.GONE);
                }
            });
        }

        handleNotificationIntent(getIntent());

        networkMonitor = new com.example.se114_callingsystem.core.util.NetworkMonitor(this);
        networkMonitor.getIsConnected().observe(this, this::handleNetworkChange);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    private void handleNotificationIntent(android.content.Intent intent) {
        if (intent != null && intent.hasExtra("CHAT_ID")) {
            String chatId = intent.getStringExtra("CHAT_ID");
            String chatName = intent.getStringExtra("CHAT_NAME");
            String serverColor = intent.getStringExtra("SERVER_COLOR");
            String serverId = intent.getStringExtra("SERVER_ID");

            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment);
            if (navHostFragment != null) {
                NavController navController = navHostFragment.getNavController();
                Bundle args = new Bundle();
                args.putString("CHAT_ID", chatId);
                args.putString("CHAT_NAME", chatName);
                args.putString("SERVER_COLOR", serverColor != null ? serverColor : "#5865F2");
                args.putString("SERVER_ID", serverId);

                navController.navigate(R.id.nav_chat_detail, args);
            }
        }
    }

    private void setupRealtimePresence() {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        com.google.firebase.database.FirebaseDatabase database = com.google.firebase.database.FirebaseDatabase.getInstance();
        com.google.firebase.database.DatabaseReference statusRef = database.getReference("users/" + uid + "/status");
        com.google.firebase.database.DatabaseReference connectedRef = database.getReference(".info/connected");

        connectedRef.addValueEventListener(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                boolean connected = snapshot.getValue(Boolean.class) == Boolean.TRUE;
                if (connected) {
                    statusRef.onDisconnect().setValue("offline");
                    setAppStatus("online");
                }
            }

            @Override
            public void onCancelled(com.google.firebase.database.DatabaseError error) {}
        });
    }

    private void resetIdleTimer() {
        idleHandler.removeCallbacks(idleRunnable);
        setAppStatus("online");
        idleHandler.postDelayed(idleRunnable, 2 * 60 * 1000); // 2 minutes
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        resetIdleTimer();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resetIdleTimer();
        setupNotificationBadgeListener();
    }

    @Override
    protected void onPause() {
        super.onPause();
        idleHandler.removeCallbacks(idleRunnable);
        setAppStatus("offline");
        removeNotificationBadgeListener();
    }

    private void setAppStatus(String appState) {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;
        
        android.content.SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String manual = prefs.getString("manual_status", "auto");
        
        String targetStatus = appState; 
        if (!appState.equals("offline") && !manual.equals("auto")) {
            targetStatus = manual;
        }
        
        if (!targetStatus.equals(lastSetStatus)) {
            lastSetStatus = targetStatus;
            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users/" + uid + "/status").setValue(targetStatus);
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid).update("status", targetStatus);
        }
    }

    private void handleNetworkChange(boolean isConnected) {
        if (isFirstNetworkCheck) {
            isFirstNetworkCheck = false;
            if (!isConnected) {
                showOfflineBanner();
            }
            return;
        }

        if (isConnected) {
            showOnlineBanner();
        } else {
            showOfflineBanner();
        }
    }

    private void showOfflineBanner() {
        if (binding == null || binding.tvNetworkBanner == null) return;
        binding.tvNetworkBanner.setText("Không có kết nối mạng");
        binding.tvNetworkBanner.setBackgroundColor(android.graphics.Color.parseColor("#E25C5C"));
        binding.tvNetworkBanner.setVisibility(View.VISIBLE);
        binding.tvNetworkBanner.setTranslationY(-100f);
        binding.tvNetworkBanner.animate().translationY(0f).setDuration(300).start();
    }

    private void showOnlineBanner() {
        if (binding == null || binding.tvNetworkBanner == null) return;
        binding.tvNetworkBanner.setText("Đã khôi phục kết nối");
        binding.tvNetworkBanner.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"));
        
        binding.tvNetworkBanner.postDelayed(() -> {
            if (binding != null && binding.tvNetworkBanner != null) {
                binding.tvNetworkBanner.animate()
                        .translationY(-100f)
                        .setDuration(300)
                        .withEndAction(() -> {
                            if (binding != null && binding.tvNetworkBanner != null) {
                                binding.tvNetworkBanner.setVisibility(View.GONE);
                            }
                        })
                        .start();
            }
        }, 2000);
    }

    private void setupNotificationBadgeListener() {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) {
            removeNotificationBadgeListener();
            return;
        }

        if (unreadNotificationsListener != null) return; // Already listening

        unreadNotificationsListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("notifications")
                .whereEqualTo("isRead", false)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        android.util.Log.e("MainActivity", "Error listening for unread notifications", error);
                        return;
                    }
                    if (binding == null || binding.bottomNav == null) return;

                    int unreadCount = (value != null) ? value.size() : 0;
                    com.google.android.material.badge.BadgeDrawable badge = binding.bottomNav.getOrCreateBadge(R.id.nav_notifications);
                    if (unreadCount > 0) {
                        badge.setVisible(true);
                        badge.setNumber(unreadCount);
                        badge.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.discord_red));
                        badge.setBadgeTextColor(android.graphics.Color.WHITE);
                    } else {
                        badge.setVisible(false);
                    }
                });
    }

    private void removeNotificationBadgeListener() {
        if (unreadNotificationsListener != null) {
            unreadNotificationsListener.remove();
            unreadNotificationsListener = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeNotificationBadgeListener();
        binding = null;
    }
}
