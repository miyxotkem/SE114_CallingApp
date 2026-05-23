package com.example.se114_callingsystem.server;

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
import com.example.se114_callingsystem.call.CallAdapter;
import com.example.se114_callingsystem.chat.ChatZoneAdapter;
import com.example.se114_callingsystem.databinding.FragmentServerBinding;
import com.example.se114_callingsystem.model.CallChannel;
import com.example.se114_callingsystem.model.ChatChannel;
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

public class ServerFragment extends Fragment {

    private static final String TAG = "ServerFragment";

    private FragmentServerBinding binding;
    private FirebaseFirestore db;
    private String serverId;
    private String serverName;
    private String currentAccentColor = "#5865F2"; // Default Discord Blurple

    // Chat Channel Variables
    private ChatZoneAdapter chatAdapter;
    private List<ChatChannel> chatList = new ArrayList<>();
    private boolean isChatExpanded = true;

    // Call Channel Variables
    private CallAdapter callAdapter;
    private List<CallChannel> callList = new ArrayList<>();
    private boolean isCallExpanded = true;

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

        loadChatData();
        loadCallData();
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
                    if (server.getAccentColor() != null && !server.getAccentColor().isEmpty()) {
                        currentAccentColor = server.getAccentColor();
                        applyAccentColor();
                    }
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

            if (chatAdapter != null) chatAdapter.setServerColor(currentAccentColor);
            if (callAdapter != null) callAdapter.setServerColor(currentAccentColor);

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

        binding.btnAddChannel.setOnClickListener(v -> showAddChannelDialog(true));
        binding.btnAddCallChannel.setOnClickListener(v -> showAddChannelDialog(false));

        binding.btnServerSettings.setOnClickListener(v -> showServerSettingsDialog());

        binding.expandChatZone.setOnClickListener(v -> {
            isChatExpanded = !isChatExpanded;
            toggleVisibility(binding.rvChatChannels, binding.expandChatZone, isChatExpanded);
        });

        binding.expandCallZone.setOnClickListener(v -> {
            isCallExpanded = !isCallExpanded;
            toggleVisibility(binding.rvCallChannels, binding.expandCallZone, isCallExpanded);
        });

        binding.expandChatZone.setRotation(90f);
        binding.expandCallZone.setRotation(90f);
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
        View view = getLayoutInflater().inflate(R.layout.activity_bottom_sheet_server_settings, null);
        dialog.setContentView(view);

        EditText etServerNameSettings = view.findViewById(R.id.etServerNameSettings);
        MaterialButton btnRename = view.findViewById(R.id.btnRenameServer);
        MaterialButton btnDelete = view.findViewById(R.id.btnDeleteServer);
        MaterialButton btnManageMembers = view.findViewById(R.id.btnManageMembers);
        MaterialButton btnChangeColor = view.findViewById(R.id.btnChangeColor);

        try {
            int color = Color.parseColor(currentAccentColor);
            if (btnRename != null) btnRename.setBackgroundTintList(ColorStateList.valueOf(color));
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

        if (etServerNameSettings != null && serverName != null) {
            etServerNameSettings.setText(serverName);
        }

        if (btnRename != null) {
            btnRename.setOnClickListener(v -> {
                String newName = etServerNameSettings.getText().toString().trim();
                if (newName.isEmpty()) {
                    etServerNameSettings.setError("Server name cannot be empty");
                    return;
                }

                if (newName.equals(serverName)) {
                    dialog.dismiss();
                    return;
                }

                db.collection("servers").document(serverId)
                        .update("serverName", newName)
                        .addOnSuccessListener(aVoid -> {
                            serverName = newName;
                            if (binding != null) {
                                binding.tvServerName.setText(newName);
                                binding.tvAvatarLetter.setText(String.valueOf(newName.charAt(0)).toUpperCase());
                            }
                            Toast.makeText(getContext(), "Server renamed successfully", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Rename failed", Toast.LENGTH_LONG).show());
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
                Intent intent = new Intent(requireContext(), ManageMembersActivity.class);
                intent.putExtra("SERVER_ID", serverId);
                startActivity(intent);
            });
        }

        if (btnChangeColor != null) {
            btnChangeColor.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(requireContext(), ChangeColorActivity.class);
                intent.putExtra("SERVER_ID", serverId);
                intent.putExtra("CURRENT_COLOR", currentAccentColor);
                startActivity(intent);
            });
        }

        com.google.android.material.switchmaterial.SwitchMaterial switchDarkMode = view.findViewById(R.id.switchDarkMode);
        if (switchDarkMode != null) {
            switchDarkMode.setChecked(ThemeHelper.isDarkMode(requireContext()));
            switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                ThemeHelper.setDarkMode(requireContext(), isChecked);
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
            @Override public void onRename(ChatChannel channel) { showBaseRenameDialog(channel.getChatId(), channel.getChatName(), "Channels", true); }
            @Override public void onRemove(ChatChannel channel) { db.collection("Channels").document(channel.getChatId()).delete().addOnSuccessListener(a -> loadChatData()); }
        });
        binding.rvChatChannels.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvChatChannels.setAdapter(chatAdapter);
        setupDragAndDrop(binding.rvChatChannels, chatList, chatAdapter, true);
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
                    chatAdapter.notifyDataSetChanged();
                });
    }

    private void setupCallRecyclerView() {
        if (binding == null) return;
        callAdapter = new CallAdapter(callList, new CallAdapter.OnCallActionListener() {
            @Override public void onRename(CallChannel channel) { showBaseRenameDialog(channel.getCallId(), channel.getCallName(), "CallChannels", false); }
            @Override public void onRemove(CallChannel channel) { db.collection("CallChannels").document(channel.getCallId()).delete().addOnSuccessListener(a -> loadCallData()); }
        });
        binding.rvCallChannels.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvCallChannels.setAdapter(callAdapter);
        setupDragAndDrop(binding.rvCallChannels, callList, callAdapter, false);
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
                    callAdapter.notifyDataSetChanged();
                });
    }

    private void setupDragAndDrop(RecyclerView rv, List<?> list, RecyclerView.Adapter<?> adapter, boolean isChat) {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                Collections.swap(list, vh.getAdapterPosition(), target.getAdapterPosition());
                adapter.notifyItemMoved(vh.getAdapterPosition(), target.getAdapterPosition()); return true;
            }
            @Override public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                WriteBatch batch = db.batch();
                if (isChat) {
                    for (int i = 0; i < chatList.size(); i++) batch.update(db.collection("Channels").document(chatList.get(i).getChatId()), "orderIndex", i);
                    batch.commit().addOnSuccessListener(a -> loadChatData());
                } else {
                    for (int i = 0; i < callList.size(); i++) batch.update(db.collection("CallChannels").document(callList.get(i).getCallId()), "orderIndex", i);
                    batch.commit().addOnSuccessListener(a -> loadCallData());
                }
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}
        }).attachToRecyclerView(rv);
    }

    private void showAddChannelDialog(boolean isChat) {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.activity_add_channel_bottom_sheet, null);
        dialog.setContentView(view);

        TextView title = view.findViewById(R.id.tvBottomSheetTitle);
        EditText etName = view.findViewById(R.id.etChannelName);
        MaterialButton btn = view.findViewById(R.id.btnCreateConfirm);
        if (title != null) title.setText(isChat ? "Create Chat Channel" : "Create Call Channel");

        try {
            int color = Color.parseColor(currentAccentColor);
            if (btn != null) btn.setBackgroundTintList(ColorStateList.valueOf(color));
        } catch (Exception e) {}

        if (btn != null) {
            btn.setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                if (name.isEmpty()) return;
                String col = isChat ? "Channels" : "CallChannels";
                String field = isChat ? "chatName" : "callName";

                db.collection(col).whereEqualTo("serverId", serverId).whereEqualTo(field, name).get().addOnSuccessListener(snaps -> {
                    if (!snaps.isEmpty()) etName.setError("Name exists!");
                    else {
                        if (isChat) db.collection(col).add(new ChatChannel(name, serverId, chatList.size())).addOnSuccessListener(r -> loadChatData());
                        else db.collection(col).add(new CallChannel(name, serverId, callList.size())).addOnSuccessListener(r -> loadCallData());
                        dialog.dismiss();
                    }
                });
            });
        }
        dialog.show();
    }

    private void showBaseRenameDialog(String id, String currentName, String collection, boolean isChat) {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.activity_add_channel_bottom_sheet, null);
        dialog.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tvBottomSheetTitle);
        EditText etName = view.findViewById(R.id.etChannelName);
        MaterialButton btnConfirm = view.findViewById(R.id.btnCreateConfirm);

        if (tvTitle != null) tvTitle.setText(isChat ? "Rename Chat Channel" : "Rename Call Channel");
        if (btnConfirm != null) btnConfirm.setText("Rename");
        if (etName != null) etName.setText(currentName);

        try {
            int color = Color.parseColor(currentAccentColor);
            if (btnConfirm != null) btnConfirm.setBackgroundTintList(ColorStateList.valueOf(color));
        } catch (Exception e) {}

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                String newName = etName.getText().toString().trim();
                String field = isChat ? "chatName" : "callName";
                if (newName.isEmpty() || newName.equalsIgnoreCase(currentName)) { dialog.dismiss(); return; }

                db.collection(collection).whereEqualTo("serverId", serverId).whereEqualTo(field, newName).get().addOnSuccessListener(snaps -> {
                    if (!snaps.isEmpty()) etName.setError("Name exists!");
                    else {
                        db.collection(collection).document(id).update(field, newName).addOnSuccessListener(a -> {
                            if (isChat) loadChatData(); else loadCallData();
                            dialog.dismiss();
                        });
                    }
                });
            });
        }
        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
