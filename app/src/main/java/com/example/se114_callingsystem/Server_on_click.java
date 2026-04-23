package com.example.se114_callingsystem;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Server_on_click extends AppCompatActivity {

    private FirebaseFirestore db;
    private String serverId;
    private TextView tvServerName;

    // Chat Channel Variables
    private RecyclerView rvChatChannels;
    private ChatAdapter chatAdapter;
    private List<ChatChannel> chatList = new ArrayList<>();
    private boolean isChatExpanded = true;

    // Call Channel Variables
    private RecyclerView rvCallChannels;
    private CallAdapter callAdapter;
    private List<CallChannel> callList = new ArrayList<>();
    private boolean isCallExpanded = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_on_click);

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

        loadChatData();
        loadCallData();
    }

    private void initViews() {
        ImageView btnBack = findViewById(R.id.btnBack);
        ImageView btnAddChannel = findViewById(R.id.btnAddChannel);
        ImageView btnAddChat = findViewById(R.id.btnAddChannel);
        ImageView btnAddCall = findViewById(R.id.btnAddCallChannel);

        btnBack.setOnClickListener(v -> finish());
        btnAddChannel.setOnClickListener(v -> showAddChannelDialog(true));
        btnAddChat.setOnClickListener(v -> showAddChannelDialog(true));
        btnAddCall.setOnClickListener(v -> showAddChannelDialog(false));

        tvServerName.setOnClickListener(v -> showServerSettingsDialog());

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

        btnExpandChat.setRotation(90f);
        btnExpandCall.setRotation(90f);
    }

    private void toggleVisibility(View view, View icon, boolean expanded) {
        android.transition.TransitionManager.beginDelayedTransition((ViewGroup) view.getParent());
        view.setVisibility(expanded ? View.VISIBLE : View.GONE);
        icon.animate().rotation(expanded ? 90 : 0).setDuration(200).start();
    }

    // --- SERVER SETTINGS METHODS ---

    private void showServerSettingsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.activity_bottom_sheet_server_settings, null);
        dialog.setContentView(view);

        EditText etServerNameSettings = view.findViewById(R.id.etServerNameSettings);
        Button btnRename = view.findViewById(R.id.btnRenameServer);
        Button btnDelete = view.findViewById(R.id.btnDeleteServer);

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

            // Make sure "Servers" and "serverName" match your Firestore exactly!
            db.collection("servers").document(serverId)
                    .update("serverName", newName)
                    .addOnSuccessListener(aVoid -> {
                        tvServerName.setText(newName);
                        Toast.makeText(this, "Server renamed successfully", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    // ADDED ERROR LOGGING HERE
                    .addOnFailureListener(e -> Toast.makeText(this, "Rename failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        });

        btnDelete.setOnClickListener(v -> {
            dialog.dismiss();
            showServerDeleteConfirm();
        });

        dialog.show();
        applyTransparentBackground(dialog);
    }

    private void showServerDeleteConfirm() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Server")
                .setMessage("Are you sure you want to delete this server? This action cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    // CHANGED FROM "Servers" TO "servers" HERE:
                    db.collection("servers").document(serverId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Server deleted", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- CHAT CHANNEL METHODS ---

    private void setupChatRecyclerView() {
        chatAdapter = new ChatAdapter(chatList, new ChatAdapter.OnChannelActionListener() {
            @Override
            public void onRename(ChatChannel channel) { showChatRenameDialog(channel); }
            @Override
            public void onRemove(ChatChannel channel) { showChatDeleteConfirm(channel); }
        });
        rvChatChannels.setLayoutManager(new LinearLayoutManager(this));
        rvChatChannels.setAdapter(chatAdapter);

        setupDragAndDrop(rvChatChannels, chatList, chatAdapter, true);
    }

    private void loadChatData() {
        db.collection("Channels").whereEqualTo("serverId", serverId)
                .orderBy("orderIndex", Query.Direction.ASCENDING).get()
                .addOnSuccessListener(snapshots -> {
                    chatList.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        ChatChannel c = doc.toObject(ChatChannel.class);
                        if (c != null) { c.setChatId(doc.getId()); chatList.add(c); }
                    }
                    chatAdapter.notifyDataSetChanged();
                });
    }

    // --- CALL CHANNEL METHODS ---

    private void setupCallRecyclerView() {
        callAdapter = new CallAdapter(callList, new CallAdapter.OnCallActionListener() {
            @Override
            public void onRename(CallChannel channel) { showCallRenameDialog(channel); }
            @Override
            public void onRemove(CallChannel channel) { showCallDeleteConfirm(channel); }
        });
        rvCallChannels.setLayoutManager(new LinearLayoutManager(this));
        rvCallChannels.setAdapter(callAdapter);

        setupDragAndDrop(rvCallChannels, callList, callAdapter, false);
    }

    private void loadCallData() {
        db.collection("CallChannels").whereEqualTo("serverId", serverId)
                .orderBy("orderIndex", Query.Direction.ASCENDING).get()
                .addOnSuccessListener(snapshots -> {
                    callList.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        CallChannel c = doc.toObject(CallChannel.class);
                        if (c != null) { c.setCallId(doc.getId()); callList.add(c); }
                    }
                    callAdapter.notifyDataSetChanged();
                });
    }

    // --- SHARED UTILITIES ---

    private void setupDragAndDrop(RecyclerView rv, List<?> list, RecyclerView.Adapter<?> adapter, boolean isChat) {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                Collections.swap(list, vh.getAdapterPosition(), target.getAdapterPosition());
                adapter.notifyItemMoved(vh.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }
            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                if (isChat) updateChatFirestoreOrder(); else updateCallFirestoreOrder();
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}
        }).attachToRecyclerView(rv);
    }

    private void updateChatFirestoreOrder() {
        WriteBatch batch = db.batch();
        for (int i = 0; i < chatList.size(); i++) {
            batch.update(db.collection("Channels").document(chatList.get(i).getChatId()), "orderIndex", i);
        }
        batch.commit().addOnSuccessListener(aVoid -> loadChatData());
    }

    private void updateCallFirestoreOrder() {
        WriteBatch batch = db.batch();
        for (int i = 0; i < callList.size(); i++) {
            batch.update(db.collection("CallChannels").document(callList.get(i).getCallId()), "orderIndex", i);
        }
        batch.commit().addOnSuccessListener(aVoid -> loadCallData());
    }

    private void showAddChannelDialog(boolean isChat) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.activity_add_channel_bottom_sheet, null);
        dialog.setContentView(view);

        TextView title = view.findViewById(R.id.tvBottomSheetTitle);
        EditText etName = view.findViewById(R.id.etChannelName);
        Button btn = view.findViewById(R.id.btnCreateConfirm);

        if (title != null) title.setText(isChat ? "Create Chat Channel" : "Create Call Channel");

        btn.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) return;

            String collection = isChat ? "Channels" : "CallChannels";
            String field = isChat ? "chatName" : "callName";

            checkChannelNameExists(collection, field, name, exists -> {
                if (exists) {
                    etName.setError("Channel name already exists!");
                } else {
                    if (isChat) createNewChatChannel(name);
                    else createNewCallChannel(name);
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
        applyTransparentBackground(dialog);
    }

    private void createNewChatChannel(String name) {
        ChatChannel channel = new ChatChannel(name, serverId, chatList.size());
        db.collection("Channels").add(channel)
                .addOnSuccessListener(ref -> {
                    loadChatData();
                    Toast.makeText(this, "Chat Channel Added", Toast.LENGTH_SHORT).show();
                });
    }

    private void createNewCallChannel(String name) {
        CallChannel channel = new CallChannel(name, serverId, callList.size());
        db.collection("CallChannels").add(channel)
                .addOnSuccessListener(ref -> {
                    loadCallData();
                    Toast.makeText(this, "Call Channel Added", Toast.LENGTH_SHORT).show();
                });
    }

    // --- DIALOGS (RENAME/DELETE) ---

    private void showChatRenameDialog(ChatChannel channel) {
        showBaseRenameDialog(channel.getChatId(), channel.getChatName(), "Channels", true);
    }

    private void showCallRenameDialog(CallChannel channel) {
        showBaseRenameDialog(channel.getCallId(), channel.getCallName(), "CallChannels", false);
    }

    private void showBaseRenameDialog(String id, String currentName, String collection, boolean isChat) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.activity_add_channel_bottom_sheet, null);
        dialog.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvBottomSheetTitle);
        EditText etName = view.findViewById(R.id.etChannelName);
        Button btnConfirm = view.findViewById(R.id.btnCreateConfirm);

        if (tvTitle != null) tvTitle.setText(isChat ? "Rename Chat Channel" : "Rename Call Channel");
        if (btnConfirm != null) btnConfirm.setText("Rename");

        etName.setText(currentName);

        btnConfirm.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            String field = isChat ? "chatName" : "callName";

            if (newName.isEmpty()) return;

            if (newName.equalsIgnoreCase(currentName)) {
                dialog.dismiss();
                return;
            }

            checkChannelNameExists(collection, field, newName, exists -> {
                if (exists) {
                    etName.setError("This name is already taken in this server");
                } else {
                    db.collection(collection).document(id)
                            .update(field, newName)
                            .addOnSuccessListener(aVoid -> {
                                if (isChat) loadChatData(); else loadCallData();
                                dialog.dismiss();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show());
                }
            });
        });

        dialog.show();
        applyTransparentBackground(dialog);
    }

    private void showChatDeleteConfirm(ChatChannel channel) {
        new MaterialAlertDialogBuilder(this).setTitle("Delete Chat Channel")
                .setPositiveButton("Delete", (d, w) -> db.collection("Channels").document(channel.getChatId()).delete().addOnSuccessListener(aVoid -> loadChatData()))
                .show();
    }

    private void showCallDeleteConfirm(CallChannel channel) {
        new MaterialAlertDialogBuilder(this).setTitle("Delete Call Channel")
                .setPositiveButton("Delete", (d, w) -> db.collection("CallChannels").document(channel.getCallId()).delete().addOnSuccessListener(aVoid -> loadCallData()))
                .show();
    }

    private void applyTransparentBackground(BottomSheetDialog dialog) {
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) bottomSheet.setBackgroundResource(android.R.color.transparent);
    }

    private void checkChannelNameExists(String collection, String fieldName, String name, OnValidationListener listener) {
        db.collection(collection)
                .whereEqualTo("serverId", serverId)
                .whereEqualTo(fieldName, name)
                .get()
                .addOnSuccessListener(snapshots -> {
                    listener.onResult(!snapshots.isEmpty());
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error checking name: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    interface OnValidationListener {
        void onResult(boolean exists);
    }
}