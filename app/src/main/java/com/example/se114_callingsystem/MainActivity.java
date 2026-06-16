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
import com.example.se114_callingsystem.features.server.ui.ServerAdapter;
import com.example.se114_callingsystem.features.server.ui.ServerFragment;
import com.example.se114_callingsystem.features.server.ui.CreateServerDialog;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private android.os.Handler idleHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable idleRunnable = () -> setAppStatus("idle");
    private String lastSetStatus = "";
    private com.example.se114_callingsystem.core.util.NetworkMonitor networkMonitor;
    private boolean isFirstNetworkCheck = true;
    private com.google.firebase.firestore.ListenerRegistration unreadNotificationsListener;

    private ServerAdapter sidebarAdapter;
    private java.util.List<com.example.se114_callingsystem.core.model.Server> serverList = new java.util.ArrayList<>();
    private java.util.List<String> currentServerOrder = new java.util.ArrayList<>();
    private com.google.firebase.firestore.ListenerRegistration sidebarServersListener;
    private int systemBarsBottom = 0;

    private android.content.BroadcastReceiver localCallReceiver;
    private android.app.Dialog activeCallDialog;
    private android.animation.AnimatorSet answerBtnAnimator;

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
            systemBarsBottom = systemBars.bottom;
            
            // Add padding to bottomNav and sidebar to avoid being cut off by navigation bar
            binding.sidebarScrollView.setPadding(0, 0, 0, systemBarsBottom);
            
            NavHostFragment nhf = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
            if (nhf != null) {
                try {
                    int currentId = nhf.getNavController().getCurrentDestination().getId();
                    updateBottomNavPadding(currentId);
                    updateBottomPadding(nhf.getNavController().getCurrentDestination());
                } catch (Exception e) {}
            }
            
            return insets;
        });

        // Initialize Sidebar RecyclerView
        binding.recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        sidebarAdapter = new ServerAdapter(serverList, null, server -> {
            NavHostFragment nhf = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
            if (nhf != null) {
                NavController navController = nhf.getNavController();
                androidx.fragment.app.Fragment currentFragment = nhf.getChildFragmentManager().getPrimaryNavigationFragment();
                if (currentFragment instanceof ServerFragment) {
                    ((ServerFragment) currentFragment).switchServer(server.getServerId(), server.getServerName());
                    sidebarAdapter.setActiveServerId(server.getServerId());
                    try {
                        androidx.navigation.NavBackStackEntry currentEntry = navController.getCurrentBackStackEntry();
                        if (currentEntry != null) {
                            Bundle currentArgs = currentEntry.getArguments();
                            if (currentArgs != null) {
                                currentArgs.putString("SERVER_ID", server.getServerId());
                                currentArgs.putString("SERVER_NAME", server.getServerName());
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("MainActivity", "Error updating backstack entry arguments", e);
                    }
                } else {
                    Bundle args = new Bundle();
                    args.putString("SERVER_ID", server.getServerId());
                    args.putString("SERVER_NAME", server.getServerName());
                    navController.navigate(R.id.nav_server, args);
                }
            }
        });
        binding.recyclerView.setAdapter(sidebarAdapter);

        // Sidebar actions click listeners
        binding.btnSidebarHome.setOnClickListener(v -> {
            NavHostFragment nhf = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
            if (nhf != null) {
                NavController navController = nhf.getNavController();
                navController.popBackStack(R.id.nav_home, false);
            }
        });

        binding.mcvServerCreate.setOnClickListener(v -> {
            android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            String currentPlan = prefs.getString("current_plan", "Basic");
            int limit = 5;
            if ("Standard".equals(currentPlan)) limit = 15;
            else if ("Pro".equals(currentPlan)) limit = 30;

            int currentServerCount = serverList.size();
            if (currentServerCount >= limit) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Plan Limit Reached")
                    .setMessage("You reached the limit of " + limit + " servers on your " + currentPlan + " plan. Upgrade your plan to create more servers.")
                    .setPositiveButton("Upgrade", (dialog, which) -> {
                        androidx.navigation.fragment.NavHostFragment nhf = (androidx.navigation.fragment.NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                        if (nhf != null) {
                            nhf.getNavController().navigate(R.id.nav_upgrade_plan);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                return;
            }

            CreateServerDialog dialog = new CreateServerDialog();
            dialog.show(getSupportFragmentManager(), "Server_on_create");
        });

        binding.mcvServerJoin.setOnClickListener(v -> showJoinServerDialog());

        setupSidebarDragAndDrop();

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNav, navController);
            
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int id = destination.getId();
                if (id == R.id.nav_home || id == R.id.nav_notifications || id == R.id.nav_profile || id == R.id.nav_server) {
                    binding.bottomNav.setVisibility(View.VISIBLE);
                } else {
                    binding.bottomNav.setVisibility(View.GONE);
                }

                // Global sidebar visibility: show ONLY on Home and Server fragments
                if (id == R.id.nav_home || id == R.id.nav_server) {
                    binding.sidebarScrollView.setVisibility(View.VISIBLE);
                    setupSidebarListeners();
                } else {
                    binding.sidebarScrollView.setVisibility(View.GONE);
                    removeSidebarListeners();
                }

                // Update bottom padding of main container
                updateBottomPadding(destination);
                
                // Update start padding of bottom navigation to offset the sidebar width
                updateBottomNavPadding(id);

                // Active server / Home indicator management
                if (id == R.id.nav_server) {
                    if (arguments != null) {
                        String serverId = arguments.getString("SERVER_ID");
                        if (sidebarAdapter != null) {
                            sidebarAdapter.setActiveServerId(serverId);
                        }
                    }
                    binding.viewHomeIndicator.setVisibility(View.GONE);
                } else if (id == R.id.nav_home || id == R.id.nav_friend_manage || (id == R.id.nav_chat_detail && (arguments == null || arguments.getString("SERVER_ID") == null))) {
                    if (sidebarAdapter != null) {
                        sidebarAdapter.setActiveServerId(null);
                    }
                    binding.viewHomeIndicator.setVisibility(View.VISIBLE);
                } else {
                    // For sub-views, keep current highlight if serverId exists in arguments
                    if (arguments != null && arguments.getString("SERVER_ID") != null) {
                        String serverId = arguments.getString("SERVER_ID");
                        if (sidebarAdapter != null) {
                            sidebarAdapter.setActiveServerId(serverId);
                        }
                        binding.viewHomeIndicator.setVisibility(View.GONE);
                    } else {
                        // Highlight home indicator for app wide utilities
                        if (id == R.id.nav_notifications || id == R.id.nav_profile) {
                            if (sidebarAdapter != null) {
                                sidebarAdapter.setActiveServerId(null);
                            }
                            binding.viewHomeIndicator.setVisibility(View.VISIBLE);
                        }
                    }
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
        registerCallReceiver();
        
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            if (navController.getCurrentDestination() != null) {
                int id = navController.getCurrentDestination().getId();
                if (id == R.id.nav_home || id == R.id.nav_server) {
                    setupSidebarListeners();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        idleHandler.removeCallbacks(idleRunnable);
        setAppStatus("offline");
        removeNotificationBadgeListener();
        removeSidebarListeners();
        unregisterCallReceiver();
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

    private void setupSidebarListeners() {
        String currentUserUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        if (currentUserUid.isEmpty()) {
            removeSidebarListeners();
            return;
        }

        if (sidebarServersListener != null) return; // Already listening

        com.google.firebase.firestore.FirebaseFirestore dbInstance = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        dbInstance.collection("users").document(currentUserUid).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                com.example.se114_callingsystem.core.model.User user = documentSnapshot.toObject(com.example.se114_callingsystem.core.model.User.class);
                if (user != null && user.getServerOrder() != null) {
                    currentServerOrder = user.getServerOrder();
                } else {
                    currentServerOrder = new java.util.ArrayList<>();
                }
            } else {
                currentServerOrder = new java.util.ArrayList<>();
            }

            if (sidebarServersListener == null) {
                sidebarServersListener = dbInstance.collection("servers")
                    .whereArrayContains("members", currentUserUid)
                    .addSnapshotListener((value, error) -> {
                        if (error != null) {
                            android.util.Log.e("MainActivity", "Error fetching servers: " + error.getMessage());
                            return;
                        }
                        if (value != null && binding != null) {
                            serverList.clear();
                            for (com.google.firebase.firestore.DocumentSnapshot doc : value) {
                                com.example.se114_callingsystem.core.model.Server server = doc.toObject(com.example.se114_callingsystem.core.model.Server.class);
                                if (server != null) {
                                    server.setServerId(doc.getId());
                                    serverList.add(server);
                                }
                            }
                            
                            serverList.sort((s1, s2) -> {
                                int idx1 = currentServerOrder.indexOf(s1.getServerId());
                                int idx2 = currentServerOrder.indexOf(s2.getServerId());
                                if (idx1 == -1) idx1 = Integer.MAX_VALUE;
                                if (idx2 == -1) idx2 = Integer.MAX_VALUE;
                                return java.lang.Integer.compare(idx1, idx2);
                            });
                            
                            if (sidebarAdapter != null) {
                                sidebarAdapter.notifyDataSetChanged();
                            }
                        }
                    });
            }
        });
    }

    private void removeSidebarListeners() {
        if (sidebarServersListener != null) {
            sidebarServersListener.remove();
            sidebarServersListener = null;
        }
    }

    private void setupSidebarDragAndDrop() {
        androidx.recyclerview.widget.ItemTouchHelper itemTouchHelper = new androidx.recyclerview.widget.ItemTouchHelper(
            new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(androidx.recyclerview.widget.ItemTouchHelper.UP | androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0) {
                @Override
                public boolean onMove(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView recyclerView, @androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder, @androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder target) {
                    int fromPosition = viewHolder.getAdapterPosition();
                    int toPosition = target.getAdapterPosition();

                    java.util.Collections.swap(serverList, fromPosition, toPosition);
                    sidebarAdapter.notifyItemMoved(fromPosition, toPosition);
                    return true;
                }

                @Override
                public void onSwiped(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder, int direction) {}

                @Override
                public void clearView(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView recyclerView, @androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder) {
                    super.clearView(recyclerView, viewHolder);
                    saveServerOrder();
                }
            }
        );
        itemTouchHelper.attachToRecyclerView(binding.recyclerView);
    }

    private void saveServerOrder() {
        String currentUserUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        if (currentUserUid.isEmpty()) return;

        java.util.List<String> order = new java.util.ArrayList<>();
        for (com.example.se114_callingsystem.core.model.Server s : serverList) {
            order.add(s.getServerId());
        }
        currentServerOrder = order;

        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(currentUserUid)
            .update("serverOrder", order)
            .addOnFailureListener(e -> android.util.Log.e("MainActivity", "Failed to save server order", e));
    }

    private void showJoinServerDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_join_server, null);
        dialog.setContentView(view);
        
        View parent = (View) view.getParent();
        if (parent != null) {
            parent.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }

        com.google.android.material.textfield.TextInputEditText edtInviteCode = view.findViewById(R.id.edtInviteCode);
        com.google.android.material.button.MaterialButton btnJoin = view.findViewById(R.id.btnJoinServer);

        btnJoin.setOnClickListener(v -> {
            String inviteCode = edtInviteCode.getText() != null ? edtInviteCode.getText().toString().trim() : "";
            if (!inviteCode.isEmpty()) {
                joinServer(inviteCode);
                dialog.dismiss();
            } else {
                edtInviteCode.setError("Vui lòng nhập mã mời");
            }
        });

        dialog.show();
    }

    private void joinServer(String serverIdToJoin) {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        String userName = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null && 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getDisplayName() != null ? 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getDisplayName() : "New Member";
        if (uid == null) return;
        
        android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String currentPlan = prefs.getString("current_plan", "Basic");
        int limit = 5;
        if ("Standard".equals(currentPlan)) limit = 15;
        else if ("Pro".equals(currentPlan)) limit = 30;

        int currentServerCount = serverList.size();
        if (currentServerCount >= limit) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Plan Limit Reached")
                .setMessage("You reached the limit of " + limit + " servers on your " + currentPlan + " plan. Upgrade your plan to join more servers.")
                .setPositiveButton("Upgrade", (dialog, which) -> {
                    NavHostFragment nhf = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                    if (nhf != null) {
                        nhf.getNavController().navigate(R.id.nav_upgrade_plan);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }
        
        com.google.firebase.firestore.FirebaseFirestore dbInstance = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        dbInstance.collection("servers").document(serverIdToJoin).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                java.util.List<String> members = (java.util.List<String>) doc.get("members");
                if (members != null && members.contains(uid)) {
                    android.widget.Toast.makeText(this, "Bạn đã ở trong server này rồi!", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

                dbInstance.collection("servers").document(serverIdToJoin).update("members", com.google.firebase.firestore.FieldValue.arrayUnion(uid));
                
                com.example.se114_callingsystem.core.model.ServerMember newMember = new com.example.se114_callingsystem.core.model.ServerMember(uid, userName, "member");
                dbInstance.collection("servers").document(serverIdToJoin).collection("members").document(uid).set(newMember);
                
                if (!currentServerOrder.contains(serverIdToJoin)) {
                    currentServerOrder.add(serverIdToJoin);
                    dbInstance.collection("users").document(uid).update("serverOrder", currentServerOrder);
                }
                
                android.widget.Toast.makeText(this, "Tham gia server thành công!", android.widget.Toast.LENGTH_SHORT).show();
                
                NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
                if (navHostFragment != null) {
                    NavController navController = navHostFragment.getNavController();
                    androidx.fragment.app.Fragment currentFragment = navHostFragment.getChildFragmentManager().getPrimaryNavigationFragment();
                    if (currentFragment instanceof ServerFragment) {
                        ((ServerFragment) currentFragment).switchServer(serverIdToJoin, doc.getString("serverName"));
                        try {
                            androidx.navigation.NavBackStackEntry currentEntry = navController.getCurrentBackStackEntry();
                            if (currentEntry != null) {
                                Bundle currentArgs = currentEntry.getArguments();
                                if (currentArgs != null) {
                                    currentArgs.putString("SERVER_ID", serverIdToJoin);
                                    currentArgs.putString("SERVER_NAME", doc.getString("serverName"));
                                }
                            }
                        } catch (Exception e) {
                            android.util.Log.e("MainActivity", "Error updating backstack entry arguments", e);
                        }
                    } else {
                        Bundle args = new Bundle();
                        args.putString("SERVER_ID", serverIdToJoin);
                        args.putString("SERVER_NAME", doc.getString("serverName"));
                        navController.navigate(R.id.nav_server, args);
                    }
                }
            } else {
                android.widget.Toast.makeText(this, "Mã mời không hợp lệ hoặc Server không tồn tại.", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateBottomPadding(androidx.navigation.NavDestination destination) {
        if (destination == null || binding == null) return;
        int id = destination.getId();
        if (id == R.id.nav_home || id == R.id.nav_notifications || id == R.id.nav_profile || id == R.id.nav_server) {
            binding.mainContainer.setPadding(binding.mainContainer.getPaddingLeft(), binding.mainContainer.getPaddingTop(), binding.mainContainer.getPaddingRight(), 0);
        } else {
            binding.mainContainer.setPadding(binding.mainContainer.getPaddingLeft(), binding.mainContainer.getPaddingTop(), binding.mainContainer.getPaddingRight(), systemBarsBottom);
        }
    }

    private void updateBottomNavPadding(int destinationId) {
        if (binding == null) return;
        int paddingStart = 0;
        if (destinationId == R.id.nav_home || destinationId == R.id.nav_server) {
            float density = getResources().getDisplayMetrics().density;
            paddingStart = (int) (72 * density);
        }
        binding.bottomNav.setPadding(paddingStart, 0, 0, systemBarsBottom);
    }

    private void registerCallReceiver() {
        if (localCallReceiver == null) {
            localCallReceiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context context, android.content.Intent intent) {
                    String action = intent.getAction();
                    if ("com.example.se114_callingsystem.INCOMING_CALL".equals(action)) {
                        String callerId = intent.getStringExtra("CALLER_ID");
                        String callerName = intent.getStringExtra("CALLER_NAME");
                        String channelName = intent.getStringExtra("CALL_CHANNEL_NAME");
                        String callType = intent.getStringExtra("CALL_TYPE");
                        showIncomingCallDialog(callerId, callerName, channelName, callType);
                    } else if ("com.example.se114_callingsystem.DISMISS_CALL_DIALOG".equals(action)) {
                        dismissIncomingCallDialog();
                    }
                }
            };
            android.content.IntentFilter filter = new android.content.IntentFilter();
            filter.addAction("com.example.se114_callingsystem.INCOMING_CALL");
            filter.addAction("com.example.se114_callingsystem.DISMISS_CALL_DIALOG");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                registerReceiver(localCallReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(localCallReceiver, filter);
            }
        }
    }

    private void unregisterCallReceiver() {
        if (localCallReceiver != null) {
            unregisterReceiver(localCallReceiver);
            localCallReceiver = null;
        }
    }

    private void showIncomingCallDialog(String callerId, String callerName, String channelName, String callType) {
        if (activeCallDialog != null && activeCallDialog.isShowing()) {
            return;
        }

        activeCallDialog = new android.app.Dialog(this, R.style.InAppCallBannerDialog);
        android.view.View view = getLayoutInflater().inflate(R.layout.layout_incoming_call_banner, null);
        activeCallDialog.setContentView(view);

        android.view.Window window = activeCallDialog.getWindow();
        if (window != null) {
            window.setGravity(android.view.Gravity.TOP);
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            
            // Set margins
            android.view.WindowManager.LayoutParams lp = window.getAttributes();
            lp.y = (int) (16 * getResources().getDisplayMetrics().density); // Top margin
            window.setAttributes(lp);
        }

        com.google.android.material.imageview.ShapeableImageView ivCallerAvatar = view.findViewById(R.id.ivCallerAvatar);
        android.widget.TextView tvCallerName = view.findViewById(R.id.tvCallerName);
        android.widget.TextView tvCallTypeDesc = view.findViewById(R.id.tvCallTypeDesc);
        com.google.android.material.card.MaterialCardView btnDeclineCall = view.findViewById(R.id.btnDeclineCall);
        com.google.android.material.card.MaterialCardView btnAnswerCall = view.findViewById(R.id.btnAnswerCall);

        tvCallerName.setText(callerName);
        String desc = "voice".equals(callType) ? "Cuộc gọi thoại đến..." : "Cuộc gọi video đến...";
        tvCallTypeDesc.setText(desc);

        if (callerId != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(callerId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && binding != null) {
                        String avatarUrl = documentSnapshot.getString("avatarUrl");
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            com.bumptech.glide.Glide.with(this)
                                .load(avatarUrl)
                                .placeholder(R.drawable.ic_user)
                                .into(ivCallerAvatar);
                        }
                    }
                });
        }

        btnDeclineCall.setOnClickListener(v -> {
            android.content.Intent declineIntent = new android.content.Intent(this, com.example.se114_callingsystem.core.service.MessageNotificationService.class);
            declineIntent.setAction("com.example.se114_callingsystem.ACTION_DECLINE_CALL");
            declineIntent.putExtra("CALL_CHANNEL_NAME", channelName);
            startService(declineIntent);
            dismissIncomingCallDialog();
        });

        btnAnswerCall.setOnClickListener(v -> {
            android.content.Intent answerIntent = new android.content.Intent(this, com.example.se114_callingsystem.core.service.MessageNotificationService.class);
            answerIntent.setAction("com.example.se114_callingsystem.ACTION_ANSWER_CALL");
            answerIntent.putExtra("CALL_CHANNEL_NAME", channelName);
            answerIntent.putExtra("CALL_TYPE", callType);
            startService(answerIntent);
            dismissIncomingCallDialog();
        });

        // Add premium pulsing scale animation to the answer button
        try {
            android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator.ofFloat(btnAnswerCall, "scaleX", 1f, 1.15f, 1f);
            android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator.ofFloat(btnAnswerCall, "scaleY", 1f, 1.15f, 1f);
            scaleX.setDuration(1200);
            scaleY.setDuration(1200);
            scaleX.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            scaleY.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            
            answerBtnAnimator = new android.animation.AnimatorSet();
            answerBtnAnimator.playTogether(scaleX, scaleY);
            answerBtnAnimator.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        activeCallDialog.show();
    }

    private void dismissIncomingCallDialog() {
        if (answerBtnAnimator != null) {
            try {
                answerBtnAnimator.cancel();
            } catch (Exception e) {
                e.printStackTrace();
            }
            answerBtnAnimator = null;
        }
        if (activeCallDialog != null && activeCallDialog.isShowing()) {
            activeCallDialog.dismiss();
        }
        activeCallDialog = null;
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeNotificationBadgeListener();
        removeSidebarListeners();
        unregisterCallReceiver();
        dismissIncomingCallDialog();
        binding = null;
    }
}
