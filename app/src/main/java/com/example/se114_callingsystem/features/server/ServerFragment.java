package com.example.se114_callingsystem.features.server;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.features.call.CallChannelAdapter;
import com.example.se114_callingsystem.features.chat.ChatZoneAdapter;
import com.example.se114_callingsystem.databinding.FragmentServerBinding;
import com.example.se114_callingsystem.core.model.CallChannel;
import com.example.se114_callingsystem.core.model.ChatChannel;
import com.example.se114_callingsystem.core.model.PostChannel;
import com.example.se114_callingsystem.core.model.Server;
import com.example.se114_callingsystem.core.util.ThemeHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.net.Uri;
import com.bumptech.glide.Glide;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ServerFragment extends Fragment {

    private static final String TAG = "ServerFragment";

    private FragmentServerBinding binding;
    private FirebaseFirestore db;
    private String serverId;
    private String serverName;
    private String serverPurpose = "";
    private String currentAccentColor = "#5865F2"; // Default Discord Blurple

    private com.google.firebase.firestore.ListenerRegistration chatListener;
    private com.google.firebase.firestore.ListenerRegistration callListener;
    private com.google.firebase.firestore.ListenerRegistration postListener;
    private com.google.firebase.firestore.ListenerRegistration memberRoleListener;
    
    private boolean isAdminOrOwner = false;
    
    private boolean isChatLoaded = false;
    private boolean isCallLoaded = false;
    private boolean isPostLoaded = false;

    // Chat Channel Variables
    private ChatZoneAdapter chatAdapter;
    private List<ChatChannel> chatList = new ArrayList<>();
    private boolean isChatExpanded = true;

    // Call Channel Variables
    private CallChannelAdapter CallChannelAdapter;
    private List<CallChannel> callList = new ArrayList<>();
    private boolean isCallExpanded = true;

    // Post Channel Variables
    private PostChannelAdapter PostListAdapter;
    private List<PostChannel> postList = new ArrayList<>();
    private boolean isPostExpanded = true;

    // Avatar Variables
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ImageView dialogAvatarView;
    private TextView dialogAvatarLetter;
    private TextView dialogRemoveAvatar;
    private String serverIconUrl;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                if (dialogAvatarView != null) {
                    dialogAvatarView.setImageURI(uri);
                    dialogAvatarView.setVisibility(View.VISIBLE);
                    if (dialogAvatarLetter != null) dialogAvatarLetter.setVisibility(View.GONE);
                    if (dialogRemoveAvatar != null) dialogRemoveAvatar.setVisibility(View.VISIBLE);
                }
                uploadImageToFirebase(uri);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentServerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();

        if (getArguments() != null) {
            serverId = getArguments().getString("SERVER_ID");
            serverName = getArguments().getString("SERVER_NAME");
        }

        if (serverId == null) {
            Toast.makeText(getContext(), "Error: Server ID not found", Toast.LENGTH_SHORT).show();
            if (getView() != null) {
                Navigation.findNavController(getView()).popBackStack();
            }
            return;
        }

        if (serverName != null) {
            binding.tvServerName.setText(serverName);
        }

        initViews();
        setupChatRecyclerView();
        setupCallRecyclerView();
        setupPostRecyclerView();

        loadUserRole();
        loadServerInfo();
        loadChatData();
        loadCallData();
        loadPostData();
    }

    private void loadUserRole() {
        if (serverId == null) return;
        String currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        if (currentUid.isEmpty()) return;

        memberRoleListener = db.collection("servers").document(serverId).collection("members").document(currentUid)
            .addSnapshotListener((doc, e) -> {
                if (e != null || binding == null || doc == null || !doc.exists()) {
                    isAdminOrOwner = false;
                    updatePermissionUI();
                    return;
                }
                com.example.se114_callingsystem.core.model.ServerMember m = doc.toObject(com.example.se114_callingsystem.core.model.ServerMember.class);
                if (m != null) {
                    isAdminOrOwner = "owner".equals(m.getRole()) || "admin".equals(m.getRole());
                } else {
                    isAdminOrOwner = false;
                }
                updatePermissionUI();
            });
    }

    private void updatePermissionUI() {
        if (binding == null) return;
        int visibility = isAdminOrOwner ? View.VISIBLE : View.GONE;
        binding.btnAddChannel.setVisibility(visibility);
        binding.btnAddCallChannel.setVisibility(visibility);
        binding.btnAddPostChannel.setVisibility(visibility);
        
        binding.btnServerSettings.setVisibility(View.VISIBLE);
        
        if (chatAdapter != null) chatAdapter.setAdmin(isAdminOrOwner);
        if (CallChannelAdapter != null) CallChannelAdapter.setAdmin(isAdminOrOwner);
        if (PostListAdapter != null) PostListAdapter.setAdmin(isAdminOrOwner);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadServerInfo();
    }

    private void loadServerInfo() {
        if (serverId == null) return;
        db.collection("servers").document(serverId).get().addOnSuccessListener(doc -> {
            if (doc.exists() && binding != null) {
                Server server = doc.toObject(Server.class);
                if (server != null) {
                    if (server.getServerName() != null && !server.getServerName().isEmpty()) {
                        serverName = server.getServerName();
                        binding.tvServerName.setText(serverName);
                    }
                    if (server.getPurpose() != null && !server.getPurpose().isEmpty()) {
                        serverPurpose = server.getPurpose();
                        binding.tvServerDescription.setText(serverPurpose);
                        binding.tvServerDescription.setVisibility(View.VISIBLE);
                    } else {
                        serverPurpose = "";
                        binding.tvServerDescription.setVisibility(View.GONE);
                    }
                    if (server.getAccentColor() != null && !server.getAccentColor().isEmpty()) {
                        currentAccentColor = server.getAccentColor();
                        applyAccentColor();
                    }
                    if (server.getIconUrl() != null && !server.getIconUrl().trim().isEmpty() && !server.getIconUrl().equals("default_icon_url")) {
                        serverIconUrl = server.getIconUrl();
                    } else {
                        serverIconUrl = null;
                    }
                    updateMainAvatarUI();
                }
            }
        });
    }

    private void applyAccentColor() {
        if (binding == null || getContext() == null) return;
        try {
            int color = Color.parseColor(currentAccentColor);

            binding.serverBanner.setBackgroundColor(color);
            binding.tvAvatarLetter.setTextColor(color);
            if (serverName != null && !serverName.isEmpty()) {
                binding.tvAvatarLetter.setText(String.valueOf(serverName.charAt(0)).toUpperCase());
            }

            binding.chatNeonStrip.setBackgroundColor(color);
            binding.callNeonStrip.setBackgroundColor(color);
            binding.postNeonStrip.setBackgroundColor(color);

            if (chatAdapter != null) chatAdapter.setServerColor(currentAccentColor);
            if (CallChannelAdapter != null) CallChannelAdapter.setServerColor(currentAccentColor);
            if (PostListAdapter != null) PostListAdapter.setServerColor(currentAccentColor);

            if (getActivity() != null) {
                getActivity().getWindow().setStatusBarColor(color);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initViews() {
        if (binding == null) return;

        binding.btnBack.setOnClickListener(v -> {
            Navigation.findNavController(v).popBackStack();
        });

        binding.btnAddChannel.setOnClickListener(v -> showAddChannelDialog("chat"));
        binding.btnAddCallChannel.setOnClickListener(v -> showAddChannelDialog("call"));
        binding.btnAddPostChannel.setOnClickListener(v -> showAddChannelDialog("post"));

        binding.btnServerSettings.setOnClickListener(v -> showServerSettingsDialog());

        binding.expandChatZone.setOnClickListener(v -> {
            isChatExpanded = !isChatExpanded;
            toggleVisibility(binding.rvChatChannels, binding.expandChatZone, isChatExpanded);
        });

        binding.expandCallZone.setOnClickListener(v -> {
            isCallExpanded = !isCallExpanded;
            toggleVisibility(binding.rvCallChannels, binding.expandCallZone, isCallExpanded);
        });

        binding.expandPostZone.setOnClickListener(v -> {
            isPostExpanded = !isPostExpanded;
            toggleVisibility(binding.rvPostChannels, binding.expandPostZone, isPostExpanded);
        });

        binding.expandChatZone.setRotation(90f);
        binding.expandCallZone.setRotation(90f);
        binding.expandPostZone.setRotation(90f);
    }
    
    private void checkDataLoaded() {
        if (isChatLoaded && isCallLoaded && isPostLoaded) {
            if (binding != null && binding.shimmerViewContainer != null) {
                binding.shimmerViewContainer.stopShimmer();
                binding.shimmerViewContainer.setVisibility(View.GONE);
                binding.channelsContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    private void toggleVisibility(View view, View icon, boolean expanded) {
        if (getContext() == null) return;
        android.transition.TransitionManager.beginDelayedTransition((ViewGroup) view.getParent());
        view.setVisibility(expanded ? View.VISIBLE : View.GONE);
        icon.animate().rotation(expanded ? 90 : 0).setDuration(200).start();
    }

    private void showServerSettingsDialog() {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_server_settings, null);
        dialog.setContentView(view);

        EditText etServerNameSettings = view.findViewById(R.id.etServerNameSettings);
        EditText etServerDescriptionSettings = view.findViewById(R.id.etServerDescriptionSettings);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveServerDetails);
        MaterialButton btnDelete = view.findViewById(R.id.btnDeleteServer);
        MaterialButton btnManageMembers = view.findViewById(R.id.btnManageMembers);
        MaterialButton btnChangeColor = view.findViewById(R.id.btnChangeColor);

        dialogAvatarView = view.findViewById(R.id.ivServerAvatarSettings);
        dialogAvatarLetter = view.findViewById(R.id.tvAvatarLetterSettings);
        dialogRemoveAvatar = view.findViewById(R.id.btnRemoveAvatar);
        View btnEditAvatar = view.findViewById(R.id.btnEditAvatar);

        if (serverName != null && !serverName.isEmpty() && dialogAvatarLetter != null) {
            dialogAvatarLetter.setText(String.valueOf(serverName.charAt(0)).toUpperCase());
        }
        if (etServerNameSettings != null && serverName != null) {
            etServerNameSettings.setText(serverName);
        }
        if (etServerDescriptionSettings != null) {
            etServerDescriptionSettings.setText(serverPurpose);
        }

        if (serverIconUrl != null && !serverIconUrl.isEmpty() && dialogAvatarView != null) {
            if (dialogAvatarLetter != null) dialogAvatarLetter.setVisibility(View.GONE);
            dialogAvatarView.setVisibility(View.VISIBLE);
            Glide.with(requireContext()).load(serverIconUrl).into(dialogAvatarView);
            if (dialogRemoveAvatar != null) dialogRemoveAvatar.setVisibility(View.VISIBLE);
        } else {
            if (dialogAvatarLetter != null) dialogAvatarLetter.setVisibility(View.VISIBLE);
            if (dialogAvatarView != null) dialogAvatarView.setVisibility(View.GONE);
            if (dialogRemoveAvatar != null) dialogRemoveAvatar.setVisibility(View.GONE);
        }

        if (btnEditAvatar != null) {
            btnEditAvatar.setOnClickListener(v -> {
                if (imagePickerLauncher != null) imagePickerLauncher.launch("image/*");
            });
        }
        if (dialogRemoveAvatar != null) {
            dialogRemoveAvatar.setOnClickListener(v -> removeServerAvatar());
        }

        try {
            int color = Color.parseColor(currentAccentColor);
            if (btnSave != null) btnSave.setBackgroundTintList(ColorStateList.valueOf(color));
            if (btnManageMembers != null) {
                btnManageMembers.setTextColor(color);
                btnManageMembers.setIconTint(ColorStateList.valueOf(color));
            }
            if (btnChangeColor != null) {
                btnChangeColor.setTextColor(color);
                btnChangeColor.setIconTint(ColorStateList.valueOf(color));
            }
            if (dialogAvatarLetter != null) dialogAvatarLetter.setTextColor(color);
            com.google.android.material.card.MaterialCardView cardAvatar = view.findViewById(R.id.cardServerAvatarSettings);
            if (cardAvatar != null) cardAvatar.setStrokeColor(color);
        } catch (Exception e) {}

        MaterialButton btnLeave = view.findViewById(R.id.btnLeaveServer);
        if (btnLeave != null) {
            btnLeave.setOnClickListener(v -> {
                String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                if (uid == null) return;
                
                db.collection("servers").document(serverId).collection("members").get().addOnSuccessListener(snaps -> {
                    boolean canLeave = true;
                    if (isAdminOrOwner) {
                        int adminOwnerCount = 0;
                        for (com.google.firebase.firestore.DocumentSnapshot doc : snaps) {
                            com.example.se114_callingsystem.core.model.ServerMember m = doc.toObject(com.example.se114_callingsystem.core.model.ServerMember.class);
                            if (m != null && ("owner".equals(m.getRole()) || "admin".equals(m.getRole()))) {
                                adminOwnerCount++;
                            }
                        }
                        if (adminOwnerCount <= 1) {
                            canLeave = false;
                        }
                    }
                    
                    if (!canLeave) {
                        Toast.makeText(getContext(), "Không thể rời! Bạn là Admin/Owner duy nhất còn lại.", Toast.LENGTH_LONG).show();
                    } else {
                        new android.app.AlertDialog.Builder(getContext())
                            .setTitle("Rời Server")
                            .setMessage("Bạn có chắc chắn muốn rời khỏi Server này?")
                            .setPositiveButton("Rời đi", (dialogInterface, i) -> {
                                dialog.dismiss();
                                leaveServer(uid);
                            })
                            .setNegativeButton("Huỷ", null)
                            .show();
                    }
                });
            });
        }

        if (!isAdminOrOwner) {
            if (btnSave != null) btnSave.setVisibility(View.GONE);
            if (btnDelete != null) btnDelete.setVisibility(View.GONE);
            if (btnChangeColor != null) btnChangeColor.setVisibility(View.GONE);
            if (btnEditAvatar != null) btnEditAvatar.setVisibility(View.GONE);
            if (dialogRemoveAvatar != null) dialogRemoveAvatar.setVisibility(View.GONE);
            if (etServerNameSettings != null) etServerNameSettings.setEnabled(false);
            if (etServerDescriptionSettings != null) etServerDescriptionSettings.setEnabled(false);
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                String newName = etServerNameSettings.getText().toString().trim();
                String newPurpose = etServerDescriptionSettings.getText().toString().trim();
                
                if (newName.isEmpty()) {
                    etServerNameSettings.setError("Server name cannot be empty");
                    return;
                }

                if (newName.equals(serverName) && newPurpose.equals(serverPurpose)) {
                    dialog.dismiss();
                    return;
                }

                db.collection("servers").document(serverId)
                        .update(
                            "serverName", newName,
                            "purpose", newPurpose
                        )
                        .addOnSuccessListener(aVoid -> {
                            serverName = newName;
                            serverPurpose = newPurpose;
                            if (binding != null) {
                                binding.tvServerName.setText(newName);
                                binding.tvAvatarLetter.setText(String.valueOf(newName.charAt(0)).toUpperCase());
                                
                                if (!serverPurpose.isEmpty()) {
                                    binding.tvServerDescription.setText(serverPurpose);
                                    binding.tvServerDescription.setVisibility(View.VISIBLE);
                                } else {
                                    binding.tvServerDescription.setVisibility(View.GONE);
                                }
                            }
                            Toast.makeText(getContext(), "Server updated successfully", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Update failed", Toast.LENGTH_LONG).show());
            });
        }

        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> {
                dialog.dismiss();
                showServerDeleteConfirm();
            });
        }

        if (btnManageMembers != null) {
            btnManageMembers.setOnClickListener(v -> {
                dialog.dismiss();
                Bundle args = new Bundle();
                args.putString("SERVER_ID", serverId);
                Navigation.findNavController(binding.getRoot()).navigate(R.id.action_server_to_manage_members, args);
            });
        }

        if (btnChangeColor != null) {
            btnChangeColor.setOnClickListener(v -> {
                dialog.dismiss();
                Bundle args = new Bundle();
                args.putString("SERVER_ID", serverId);
                args.putString("CURRENT_COLOR", currentAccentColor);
                Navigation.findNavController(binding.getRoot()).navigate(R.id.action_server_to_change_color, args);
            });
        }



        dialog.show();
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) bottomSheet.setBackgroundResource(android.R.color.transparent);
    }

    private void showServerDeleteConfirm() {
        if (getContext() == null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Server")
                .setMessage("Are you sure you want to delete this server? This action cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    db.collection("servers").document(serverId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(getContext(), "Server deleted", Toast.LENGTH_SHORT).show();
                                if (getView() != null) {
                                    Navigation.findNavController(getView()).popBackStack();
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupChatRecyclerView() {
        if (binding == null) return;
        chatAdapter = new ChatZoneAdapter(chatList, new ChatZoneAdapter.OnChannelActionListener() {
            @Override public void onRename(ChatChannel channel) { showBaseRenameDialog(channel.getChatId(), channel.getChatName(), "Channels", "chat"); }
            @Override public void onRemove(ChatChannel channel) { db.collection("Channels").document(channel.getChatId()).delete().addOnSuccessListener(a -> loadChatData()); }
        });
        chatAdapter.setAdmin(isAdminOrOwner);
        binding.rvChatChannels.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvChatChannels.setAdapter(chatAdapter);
        setupDragAndDrop(binding.rvChatChannels, chatList, chatAdapter, "chat");
    }

    private void loadChatData() {
        if (serverId == null) return;
        db.collection("Channels").whereEqualTo("serverId", serverId).orderBy("orderIndex", Query.Direction.ASCENDING).get()
                .addOnSuccessListener(snapshots -> {
                    if (binding == null) return;
                    chatList.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        ChatChannel c = doc.toObject(ChatChannel.class);
                        if (c != null) { c.setChatId(doc.getId()); chatList.add(c); }
                    }
                    if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
                    isChatLoaded = true;
                    checkDataLoaded();
                });
    }

    private void setupCallRecyclerView() {
        if (binding == null) return;
        CallChannelAdapter = new CallChannelAdapter(callList, new CallChannelAdapter.OnCallActionListener() {
            @Override public void onRename(CallChannel channel) { showBaseRenameDialog(channel.getCallId(), channel.getCallName(), "CallChannels", "call"); }
            @Override public void onRemove(CallChannel channel) { db.collection("CallChannels").document(channel.getCallId()).delete().addOnSuccessListener(a -> loadCallData()); }
            @Override
            public void onJoinCall(CallChannel channel) {
                Bundle args = new Bundle();
                args.putString("CALL_CHANNEL_NAME", channel.getCallName());
                args.putString("SERVER_ID", channel.getServerId());
                args.putString("SERVER_COLOR", currentAccentColor);
                Navigation.findNavController(requireView()).navigate(R.id.action_server_to_voice_call, args);
            }
        });
        CallChannelAdapter.setAdmin(isAdminOrOwner);
        binding.rvCallChannels.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvCallChannels.setAdapter(CallChannelAdapter);
        setupDragAndDrop(binding.rvCallChannels, callList, CallChannelAdapter, "call");
    }

    private void loadCallData() {
        if (serverId == null) return;
        db.collection("CallChannels").whereEqualTo("serverId", serverId).orderBy("orderIndex", Query.Direction.ASCENDING).get()
                .addOnSuccessListener(snapshots -> {
                    if (binding == null) return;
                    callList.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        CallChannel c = doc.toObject(CallChannel.class);
                        if (c != null) { c.setCallId(doc.getId()); callList.add(c); }
                    }
                    if (CallChannelAdapter != null) CallChannelAdapter.notifyDataSetChanged();
                    isCallLoaded = true;
                    checkDataLoaded();
                });
    }

    private void setupPostRecyclerView() {
        if (binding == null) return;
        PostListAdapter = new PostChannelAdapter(postList, new PostChannelAdapter.OnChannelActionListener() {
            @Override public void onRename(PostChannel channel) { showBaseRenameDialog(channel.getId(), channel.getName(), "PostChannels", "post"); }
            @Override public void onRemove(PostChannel channel) { db.collection("PostChannels").document(channel.getId()).delete().addOnSuccessListener(a -> loadPostData()); }
        });
        PostListAdapter.setAdmin(isAdminOrOwner);
        binding.rvPostChannels.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvPostChannels.setAdapter(PostListAdapter);
        setupDragAndDrop(binding.rvPostChannels, postList, PostListAdapter, "post");
    }

    private void loadPostData() {
        if (serverId == null) return;
        db.collection("PostChannels").whereEqualTo("serverId", serverId).get()
                .addOnSuccessListener(snapshots -> {
                    if (binding == null) return;
                    postList.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        PostChannel c = doc.toObject(PostChannel.class);
                        if (c != null) { c.setId(doc.getId()); postList.add(c); }
                    }
                    Collections.sort(postList, (a, b) -> Integer.compare(a.getOrderIndex(), b.getOrderIndex()));
                    if (PostListAdapter != null) PostListAdapter.notifyDataSetChanged();
                    isPostLoaded = true;
                    checkDataLoaded();
                });
    }

    private void setupDragAndDrop(RecyclerView rv, List<?> list, RecyclerView.Adapter<?> adapter, String type) {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                if (!isAdminOrOwner) return makeMovementFlags(0, 0);
                return super.getMovementFlags(rv, vh);
            }
            @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                if (!isAdminOrOwner) return false;
                Collections.swap(list, vh.getAdapterPosition(), target.getAdapterPosition());
                adapter.notifyItemMoved(vh.getAdapterPosition(), target.getAdapterPosition()); return true;
            }
            @Override public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                if (!isAdminOrOwner) return;
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
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_server_add_channel, null);
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

        if (btn != null) {
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
        }
        dialog.show();
    }

    private void showBaseRenameDialog(String id, String currentName, String collection, String type) {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_server_add_channel, null);
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
        if (etName != null) etName.setText(currentName);

        try {
            int color = Color.parseColor(currentAccentColor);
            if (btnConfirm != null) btnConfirm.setBackgroundTintList(ColorStateList.valueOf(color));
        } catch (Exception e) {}

        if (btnConfirm != null) {
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
        }
        dialog.show();
    }
    
    private void leaveServer(String uid) {
        if (serverId == null || getContext() == null) return;
        
        // Remove from users serverOrder
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                java.util.List<String> order = (java.util.List<String>) doc.get("serverOrder");
                if (order != null && order.contains(serverId)) {
                    order.remove(serverId);
                    db.collection("users").document(uid).update("serverOrder", order);
                }
            }
        });
        
        // Remove from servers members array
        db.collection("servers").document(serverId).update("members", com.google.firebase.firestore.FieldValue.arrayRemove(uid));
        
        // Remove from members subcollection
        db.collection("servers").document(serverId).collection("members").document(uid).delete();
        
        Toast.makeText(getContext(), "Đã rời Server", Toast.LENGTH_SHORT).show();
        
        // Go back to home
        androidx.navigation.Navigation.findNavController(requireView()).popBackStack();
    }

    private void uploadImageToFirebase(Uri uri) {
        if (serverId == null || getContext() == null) return;
        Toast.makeText(getContext(), "Uploading...", Toast.LENGTH_SHORT).show();
        StorageReference storageRef = FirebaseStorage.getInstance().getReference().child("server_icons/" + serverId + "_" + System.currentTimeMillis() + ".jpg");
        storageRef.putFile(uri).addOnSuccessListener(taskSnapshot -> {
            storageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                String url = downloadUri.toString();
                db.collection("servers").document(serverId).update("iconUrl", url).addOnSuccessListener(a -> {
                    serverIconUrl = url;
                    updateMainAvatarUI();
                    Toast.makeText(getContext(), "Avatar updated", Toast.LENGTH_SHORT).show();
                });
            });
        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "Failed to upload image", Toast.LENGTH_SHORT).show();
        });
    }

    private void removeServerAvatar() {
        if (serverId == null || getContext() == null) return;
        db.collection("servers").document(serverId).update("iconUrl", null).addOnSuccessListener(a -> {
            serverIconUrl = null;
            if (dialogAvatarView != null) dialogAvatarView.setVisibility(View.GONE);
            if (dialogAvatarLetter != null) dialogAvatarLetter.setVisibility(View.VISIBLE);
            if (dialogRemoveAvatar != null) dialogRemoveAvatar.setVisibility(View.GONE);
            updateMainAvatarUI();
            Toast.makeText(getContext(), "Avatar removed", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateMainAvatarUI() {
        if (binding == null || getContext() == null) return;
        ImageView ivServerAvatar = binding.getRoot().findViewById(R.id.ivServerAvatar);
        if (ivServerAvatar == null) return;
        if (serverIconUrl != null && !serverIconUrl.isEmpty()) {
            binding.tvAvatarLetter.setVisibility(View.GONE);
            ivServerAvatar.setVisibility(View.VISIBLE);
            Glide.with(this).load(serverIconUrl).into(ivServerAvatar);
        } else {
            binding.tvAvatarLetter.setVisibility(View.VISIBLE);
            ivServerAvatar.setVisibility(View.GONE);
            if (serverName != null && !serverName.isEmpty()) {
                binding.tvAvatarLetter.setText(String.valueOf(serverName.charAt(0)).toUpperCase());
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

