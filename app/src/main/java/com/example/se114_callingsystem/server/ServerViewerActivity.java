package com.example.se114_callingsystem.server;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.call.CallAdapter;
import com.example.se114_callingsystem.chat.ChatZoneAdapter;
import com.example.se114_callingsystem.model.CallChannel;
import com.example.se114_callingsystem.model.ChatChannel;
import com.example.se114_callingsystem.model.PostChannel;
import com.example.se114_callingsystem.model.Server;
import com.example.se114_callingsystem.util.ThemeHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ServerViewerActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String serverId;
    private TextView tvServerName;
    private ImageView btnServerSettings;
    private String currentAccentColor = "#5865F2"; // Chuyển màu mặc định sang Blurple (Discord) cho chuẩn bài

    // Chat Channel Variables
    private RecyclerView rvChatChannels;
    private ChatZoneAdapter chatAdapter;
    private List<ChatChannel> chatList = new ArrayList<>();
    private boolean isChatExpanded = true;

    // Call Channel Variables
    private RecyclerView rvCallChannels;
    private CallAdapter callAdapter;
    private List<CallChannel> callList = new ArrayList<>();
    private boolean isCallExpanded = true;

    // Post Channel Variables
    private RecyclerView rvPostChannels;
    private PostChannelAdapter postAdapter;
    private List<PostChannel> postList = new ArrayList<>();
    private boolean isPostExpanded = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_viewer);

        serverId = getIntent().getStringExtra("SERVER_ID");
        String serverName = getIntent().getStringExtra("SERVER_NAME");

        if (serverId == null) {
            Toast.makeText(this, "Error: Server ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvServerName = findViewById(R.id.tvServerName);
        if (serverName != null) tvServerName.setText(serverName);

        db = FirebaseFirestore.getInstance();

        initViews();
        setupChatRecyclerView();
        setupCallRecyclerView();
        setupPostRecyclerView();

        loadChatData();
        loadCallData();
        loadPostData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadServerInfo();
    }

    private void loadServerInfo() {
        db.collection("servers").document(serverId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                Server server = doc.toObject(Server.class);
                if (server != null && server.getAccentColor() != null && !server.getAccentColor().isEmpty()) {
                    currentAccentColor = server.getAccentColor();
                    applyAccentColor();
                }
            }
        });
    }

    private void applyAccentColor() {
        try {
            int color = Color.parseColor(currentAccentColor);

            // 1. Phủ màu lên toàn bộ Hero Banner
            View serverBanner = findViewById(R.id.serverBanner);
            if (serverBanner != null) {
                serverBanner.setBackgroundColor(color);
            }

            // 2. Nhuộm màu cho chữ cái bên trong Avatar
            TextView tvAvatarLetter = findViewById(R.id.tvAvatarLetter);
            if (tvAvatarLetter != null) {
                tvAvatarLetter.setTextColor(color);
                if (tvServerName.getText().length() > 0) {
                    // Tự động lấy chữ cái đầu của Server
                    tvAvatarLetter.setText(String.valueOf(tvServerName.getText().charAt(0)).toUpperCase());
                }
            }

            // 3. Nhuộm sáng các dải Neon (Neon Strips) ở tiêu đề Kênh
            View chatNeonStrip = findViewById(R.id.chatNeonStrip);
            View callNeonStrip = findViewById(R.id.callNeonStrip);
            View postNeonStrip = findViewById(R.id.postNeonStrip);
            if (chatNeonStrip != null) chatNeonStrip.setBackgroundColor(color);
            if (callNeonStrip != null) callNeonStrip.setBackgroundColor(color);
            if (postNeonStrip != null) postNeonStrip.setBackgroundColor(color);

            // 4. Đồng bộ màu xuống RecyclerView
            if (chatAdapter != null) chatAdapter.setServerColor(currentAccentColor);
            if (callAdapter != null) callAdapter.setServerColor(currentAccentColor);
            if (postAdapter != null) postAdapter.setServerColor(currentAccentColor);

            // 5. Đổi màu thanh Trạng thái (Status Bar) tiệp màu với Banner
            getWindow().setStatusBarColor(color);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initViews() {
        ImageView btnBack = findViewById(R.id.btnBack);
        ImageView btnAddChat = findViewById(R.id.btnAddChannel);
        ImageView btnAddCall = findViewById(R.id.btnAddCallChannel);
        ImageView btnAddPost = findViewById(R.id.btnAddPostChannel);
        btnServerSettings = findViewById(R.id.btnServerSettings);

        btnBack.setOnClickListener(v -> finish());
        btnAddChat.setOnClickListener(v -> showAddChannelDialog("chat"));
        btnAddCall.setOnClickListener(v -> showAddChannelDialog("call"));
        if (btnAddPost != null) btnAddPost.setOnClickListener(v -> showAddChannelDialog("post"));

        if (btnServerSettings != null) {
            btnServerSettings.setOnClickListener(v -> showServerSettingsDialog());
        }

        ImageView btnExpandChat = findViewById(R.id.expandChatZone);
        rvChatChannels = findViewById(R.id.rvChatChannels);
        btnExpandChat.setOnClickListener(v -> {
            isChatExpanded = !isChatExpanded;
            toggleVisibility(rvChatChannels, btnExpandChat, isChatExpanded);
        });

        ImageView btnExpandCall = findViewById(R.id.expandCallZone);
        rvCallChannels = findViewById(R.id.rvCallChannels);
        btnExpandCall.setOnClickListener(v -> {
            isCallExpanded = !isCallExpanded;
            toggleVisibility(rvCallChannels, btnExpandCall, isCallExpanded);
        });

        ImageView btnExpandPost = findViewById(R.id.expandPostZone);
        rvPostChannels = findViewById(R.id.rvPostChannels);
        if (btnExpandPost != null) {
            btnExpandPost.setOnClickListener(v -> {
                isPostExpanded = !isPostExpanded;
                toggleVisibility(rvPostChannels, btnExpandPost, isPostExpanded);
            });
        }

        // Đổi góc xoay mặc định của mũi tên mở rộng
        btnExpandChat.setRotation(90f);
        btnExpandCall.setRotation(90f);
        if (btnExpandPost != null) btnExpandPost.setRotation(90f);
    }

    private void toggleVisibility(View view, View icon, boolean expanded) {
        android.transition.TransitionManager.beginDelayedTransition((ViewGroup) view.getParent());
        view.setVisibility(expanded ? View.VISIBLE : View.GONE);
        icon.animate().rotation(expanded ? 90 : 0).setDuration(200).start();
    }

    private void showServerSettingsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.activity_bottom_sheet_server_settings, null);
        dialog.setContentView(view);

        EditText etServerNameSettings = view.findViewById(R.id.etServerNameSettings);
        MaterialButton btnRename = view.findViewById(R.id.btnRenameServer);
        MaterialButton btnDelete = view.findViewById(R.id.btnDeleteServer);
        MaterialButton btnManageMembers = view.findViewById(R.id.btnManageMembers);
        MaterialButton btnChangeColor = view.findViewById(R.id.btnChangeColor);

        // NHUỘM MÀU ACCENT CHO BOTTOM SHEET
        try {
            int color = Color.parseColor(currentAccentColor);

            if (btnRename != null) {
                btnRename.setBackgroundTintList(ColorStateList.valueOf(color));
            }
            if (btnManageMembers != null) {
                btnManageMembers.setTextColor(color);
                btnManageMembers.setIconTint(ColorStateList.valueOf(color));
            }
            if (btnChangeColor != null) {
                btnChangeColor.setTextColor(color);
                btnChangeColor.setIconTint(ColorStateList.valueOf(color));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        etServerNameSettings.setText(tvServerName.getText().toString());

        btnRename.setOnClickListener(v -> {
            String newName = etServerNameSettings.getText().toString().trim();
            if (newName.isEmpty()) {
                etServerNameSettings.setError("Server name cannot be empty");
                return;
            }

            if (newName.equals(tvServerName.getText().toString())) {
                dialog.dismiss();
                return;
            }

            db.collection("servers").document(serverId)
                    .update("serverName", newName)
                    .addOnSuccessListener(aVoid -> {
                        tvServerName.setText(newName);
                        // Cập nhật lại chữ cái trên Avatar khi đổi tên
                        com.google.android.material.card.MaterialCardView serverAvatar = findViewById(R.id.serverAvatarCard);
                        if (serverAvatar != null) {
                            TextView avatarText = (TextView) serverAvatar.getChildAt(0);
                            if (avatarText != null) {
                                avatarText.setText(String.valueOf(newName.charAt(0)).toUpperCase());
                            }
                        }
                        Toast.makeText(this, "Server renamed successfully", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Rename failed", Toast.LENGTH_LONG).show());
        });

        btnDelete.setOnClickListener(v -> {
            dialog.dismiss();
            showServerDeleteConfirm();
        });

        if (btnManageMembers != null) {
            btnManageMembers.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(ServerViewerActivity.this, ManageMembersActivity.class);
                intent.putExtra("SERVER_ID", serverId);
                startActivity(intent);
            });
        }

        if (btnChangeColor != null) {
            btnChangeColor.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(ServerViewerActivity.this, ChangeColorActivity.class);
                intent.putExtra("SERVER_ID", serverId);
                intent.putExtra("CURRENT_COLOR", currentAccentColor);
                startActivity(intent);
            });
        }

        // Dark Mode Toggle
        com.google.android.material.switchmaterial.SwitchMaterial switchDarkMode = view.findViewById(R.id.switchDarkMode);
        if (switchDarkMode != null) {
            switchDarkMode.setChecked(ThemeHelper.isDarkMode(this));
            switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                ThemeHelper.setDarkMode(this, isChecked);
            });
        }

        dialog.show();
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) bottomSheet.setBackgroundResource(android.R.color.transparent);
    }

    private void showServerDeleteConfirm() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Server")
                .setMessage("Are you sure you want to delete this server? This action cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    db.collection("servers").document(serverId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Server deleted", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- CHAT & CALL CHANNELS METHODS ---
    private void setupChatRecyclerView() {
        chatAdapter = new ChatZoneAdapter(chatList, new ChatZoneAdapter.OnChannelActionListener() {
            @Override public void onRename(ChatChannel channel) { showBaseRenameDialog(channel.getChatId(), channel.getChatName(), "Channels", "chat"); }
            @Override public void onRemove(ChatChannel channel) { db.collection("Channels").document(channel.getChatId()).delete().addOnSuccessListener(a -> loadChatData()); }
        });
        rvChatChannels.setLayoutManager(new LinearLayoutManager(this));
        rvChatChannels.setAdapter(chatAdapter);
        setupDragAndDrop(rvChatChannels, chatList, chatAdapter, "chat");
    }

    private void loadChatData() {
        db.collection("Channels").whereEqualTo("serverId", serverId).orderBy("orderIndex", Query.Direction.ASCENDING).get()
                .addOnSuccessListener(snapshots -> {
                    chatList.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        ChatChannel c = doc.toObject(ChatChannel.class);
                        if (c != null) { c.setChatId(doc.getId()); chatList.add(c); }
                    }
                    chatAdapter.notifyDataSetChanged();
                });
    }

    private void setupCallRecyclerView() {
        callAdapter = new CallAdapter(callList, new CallAdapter.OnCallActionListener() {
            @Override public void onRename(CallChannel channel) { showBaseRenameDialog(channel.getCallId(), channel.getCallName(), "CallChannels", "call"); }
            @Override public void onRemove(CallChannel channel) { db.collection("CallChannels").document(channel.getCallId()).delete().addOnSuccessListener(a -> loadCallData()); }
            @Override public void onJoinCall(CallChannel channel) {}
        });
        rvCallChannels.setLayoutManager(new LinearLayoutManager(this));
        rvCallChannels.setAdapter(callAdapter);
        setupDragAndDrop(rvCallChannels, callList, callAdapter, "call");
    }

    private void loadCallData() {
        db.collection("CallChannels").whereEqualTo("serverId", serverId).orderBy("orderIndex", Query.Direction.ASCENDING).get()
                .addOnSuccessListener(snapshots -> {
                    callList.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        CallChannel c = doc.toObject(CallChannel.class);
                        if (c != null) { c.setCallId(doc.getId()); callList.add(c); }
                    }
                    callAdapter.notifyDataSetChanged();
                });
    }

    private void setupPostRecyclerView() {
        postAdapter = new PostChannelAdapter(postList, new PostChannelAdapter.OnChannelActionListener() {
            @Override public void onRename(PostChannel channel) { showBaseRenameDialog(channel.getId(), channel.getName(), "PostChannels", "post"); }
            @Override public void onRemove(PostChannel channel) { db.collection("PostChannels").document(channel.getId()).delete().addOnSuccessListener(a -> loadPostData()); }
        });
        rvPostChannels.setLayoutManager(new LinearLayoutManager(this));
        rvPostChannels.setAdapter(postAdapter);
        setupDragAndDrop(rvPostChannels, postList, postAdapter, "post");
    }

    private void loadPostData() {
        db.collection("PostChannels").whereEqualTo("serverId", serverId).get()
                .addOnSuccessListener(snapshots -> {
                    postList.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        PostChannel c = doc.toObject(PostChannel.class);
                        if (c != null) { c.setId(doc.getId()); postList.add(c); }
                    }
                    Collections.sort(postList, (a, b) -> Integer.compare(a.getOrderIndex(), b.getOrderIndex()));
                    postAdapter.notifyDataSetChanged();
                });
    }

    private void setupDragAndDrop(RecyclerView rv, List<?> list, RecyclerView.Adapter<?> adapter, String type) {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                Collections.swap(list, vh.getAdapterPosition(), target.getAdapterPosition());
                adapter.notifyItemMoved(vh.getAdapterPosition(), target.getAdapterPosition()); return true;
            }
            @Override public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                WriteBatch batch = db.batch();
                if ("chat".equals(type)) {
                    for (int i = 0; i < chatList.size(); i++) batch.update(db.collection("Channels").document(chatList.get(i).getChatId()), "orderIndex", i);
                    batch.commit().addOnSuccessListener(a -> loadChatData());
                } else if ("call".equals(type)) {
                    for (int i = 0; i < callList.size(); i++) batch.update(db.collection("CallChannels").document(callList.get(i).getCallId()), "orderIndex", i);
                    batch.commit().addOnSuccessListener(a -> loadCallData());
                } else {
                    for (int i = 0; i < postList.size(); i++) batch.update(db.collection("PostChannels").document(postList.get(i).getId()), "orderIndex", i);
                    batch.commit().addOnSuccessListener(a -> loadPostData());
                }
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}
        }).attachToRecyclerView(rv);
    }

    private void showAddChannelDialog(String type) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.activity_add_channel_bottom_sheet, null);
        dialog.setContentView(view);

        TextView title = view.findViewById(R.id.tvBottomSheetTitle);
        EditText etName = view.findViewById(R.id.etChannelName);
        MaterialButton btn = view.findViewById(R.id.btnCreateConfirm);
        if (title != null) {
            if ("chat".equals(type)) title.setText("Create Chat Channel");
            else if ("call".equals(type)) title.setText("Create Call Channel");
            else title.setText("Create Post Channel");
        }

        try {
            int color = Color.parseColor(currentAccentColor);
            if (btn != null) btn.setBackgroundTintList(ColorStateList.valueOf(color));
        } catch (Exception e) {}

        btn.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) return;
            String col = "chat".equals(type) ? "Channels" : ("call".equals(type) ? "CallChannels" : "PostChannels");
            String field = "chat".equals(type) ? "chatName" : ("call".equals(type) ? "callName" : "name");

            db.collection(col).whereEqualTo("serverId", serverId).whereEqualTo(field, name).get().addOnSuccessListener(snaps -> {
                if (!snaps.isEmpty()) etName.setError("Name exists!");
                else {
                    if ("chat".equals(type)) db.collection(col).add(new ChatChannel(name, serverId, chatList.size())).addOnSuccessListener(r -> loadChatData());
                    else if ("call".equals(type)) db.collection(col).add(new CallChannel(name, serverId, callList.size())).addOnSuccessListener(r -> loadCallData());
                    else db.collection(col).add(new PostChannel(name, serverId, postList.size())).addOnSuccessListener(r -> loadPostData());
                    dialog.dismiss();
                }
            });
        });
        dialog.show();
    }

    private void showBaseRenameDialog(String id, String currentName, String collection, String type) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.activity_add_channel_bottom_sheet, null);
        dialog.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvBottomSheetTitle);
        EditText etName = view.findViewById(R.id.etChannelName);
        MaterialButton btnConfirm = view.findViewById(R.id.btnCreateConfirm);

        if (tvTitle != null) {
            if ("chat".equals(type)) tvTitle.setText("Rename Chat Channel");
            else if ("call".equals(type)) tvTitle.setText("Rename Call Channel");
            else tvTitle.setText("Rename Post Channel");
        }
        if (btnConfirm != null) btnConfirm.setText("Rename");
        etName.setText(currentName);

        try {
            int color = Color.parseColor(currentAccentColor);
            if (btnConfirm != null) btnConfirm.setBackgroundTintList(ColorStateList.valueOf(color));
        } catch (Exception e) {}

        btnConfirm.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            String field = "chat".equals(type) ? "chatName" : ("call".equals(type) ? "callName" : "name");
            if (newName.isEmpty() || newName.equalsIgnoreCase(currentName)) { dialog.dismiss(); return; }

            db.collection(collection).whereEqualTo("serverId", serverId).whereEqualTo(field, newName).get().addOnSuccessListener(snaps -> {
                if (!snaps.isEmpty()) etName.setError("Name exists!");
                else {
                    db.collection(collection).document(id).update(field, newName).addOnSuccessListener(a -> {
                        if ("chat".equals(type)) loadChatData(); 
                        else if ("call".equals(type)) loadCallData();
                        else loadPostData();
                        dialog.dismiss();
                    });
                }
            });
        });
        dialog.show();
    }
}
