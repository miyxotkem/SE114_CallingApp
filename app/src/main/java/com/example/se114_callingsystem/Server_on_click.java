package com.example.se114_callingsystem;

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

        TextView tvServerName = findViewById(R.id.tvServerName);
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

        // Chat Expand Logic
        ImageView btnExpandChat = findViewById(R.id.expandChatZone);
        rvChatChannels = findViewById(R.id.rvChatChannels);
        btnExpandChat.setOnClickListener(v -> {
            isChatExpanded = !isChatExpanded;
            toggleVisibility(rvChatChannels, btnExpandChat, isChatExpanded);
        });

        // Call Expand Logic
        ImageView btnExpandCall = findViewById(R.id.expandCallZone);
        rvCallChannels = findViewById(R.id.rvCallChannels);
        btnExpandCall.setOnClickListener(v -> {
            isCallExpanded = !isCallExpanded;
            toggleVisibility(rvCallChannels, btnExpandCall, isCallExpanded);
        });

        // Default states
        btnExpandChat.setRotation(90f);
        btnExpandCall.setRotation(90f);
    }

    private void toggleVisibility(View view, View icon, boolean expanded) {
        android.transition.TransitionManager.beginDelayedTransition((ViewGroup) view.getParent());
        view.setVisibility(expanded ? View.VISIBLE : View.GONE);
        icon.animate().rotation(expanded ? 90 : 0).setDuration(200).start();
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
        android.widget.Button btn = view.findViewById(R.id.btnCreateConfirm);

        if (title != null) {
            title.setText(isChat ? "Create Chat Channel" : "Create Call Channel");
        }

        btn.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (!name.isEmpty()) {
                if (isChat) {
                    createNewChatChannel(name);
                } else {
                    createNewCallChannel(name);
                }
                dialog.dismiss();
            }
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
        // Note: uses callList.size() for the orderIndex
        CallChannel channel = new CallChannel(name, serverId, callList.size());
        db.collection("CallChannels").add(channel)
                .addOnSuccessListener(ref -> {
                    loadCallData();
                    Toast.makeText(this, "Call Channel Added", Toast.LENGTH_SHORT).show();
                });
    }

    // --- DIALOGS (RENAME/DELETE) ---

    private void showChatRenameDialog(ChatChannel channel) {
        // Logic same as your previous rename but targeting "Channels" collection
        showBaseRenameDialog(channel.getChatId(), channel.getChatName(), "Channels", true);
    }

    private void showCallRenameDialog(CallChannel channel) {
        showBaseRenameDialog(channel.getCallId(), channel.getCallName(), "CallChannels", false);
    }

    private void showBaseRenameDialog(String id, String currentName, String collection, boolean isChat) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.activity_add_channel_bottom_sheet, null);
        dialog.setContentView(view);

        EditText etName = view.findViewById(R.id.etChannelName);
        etName.setText(currentName);

        view.findViewById(R.id.btnCreateConfirm).setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (!newName.isEmpty()) {
                db.collection(collection).document(id).update(isChat ? "chatName" : "callName", newName)
                        .addOnSuccessListener(aVoid -> {
                            if (isChat) loadChatData(); else loadCallData();
                            dialog.dismiss();
                        });
            }
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
}