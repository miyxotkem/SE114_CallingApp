package com.example.se114_callingsystem.features.server.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.features.call.ui.CallChannelAdapter;
import com.example.se114_callingsystem.features.chat.ui.ChatZoneAdapter;
import com.example.se114_callingsystem.databinding.FragmentServerBinding;
import com.example.se114_callingsystem.core.model.CallChannel;
import com.example.se114_callingsystem.core.model.ChatChannel;
import com.example.se114_callingsystem.core.model.PostChannel;
import com.example.se114_callingsystem.core.model.Server;
import com.example.se114_callingsystem.features.server.viewmodel.ServerViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.net.Uri;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@AndroidEntryPoint
public class ServerFragment extends Fragment {

    private static final String TAG = "ServerFragment";

    private FragmentServerBinding binding;
    private ServerViewModel viewModel;
    
    private String serverId;
    private String serverName;
    private String serverPurpose = "";
    private String currentAccentColor = "#5865F2"; // Default Discord Blurple
    
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
    private BottomSheetDialog settingsDialog;

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
                viewModel.uploadServerIcon(serverId, uri);
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

        viewModel = new ViewModelProvider(this).get(ServerViewModel.class);

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
        setupObservers();

        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null ? 
            FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        viewModel.initServer(serverId, currentUid);
    }

    private void setupObservers() {
        viewModel.getServerInfo().observe(getViewLifecycleOwner(), server -> {
            if (server == null || binding == null) return;
            
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
        });

        viewModel.getMemberRole().observe(getViewLifecycleOwner(), member -> {
            if (binding == null) return;
            if (member != null) {
                isAdminOrOwner = "owner".equals(member.getRole()) || "admin".equals(member.getRole());
            } else {
                isAdminOrOwner = false;
            }
            updatePermissionUI();
        });

        viewModel.getChatChannels().observe(getViewLifecycleOwner(), channels -> {
            if (binding == null || channels == null) return;
            chatList.clear();
            chatList.addAll(channels);
            if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
            isChatLoaded = true;
            checkDataLoaded();
        });

        viewModel.getCallChannels().observe(getViewLifecycleOwner(), channels -> {
            if (binding == null || channels == null) return;
            callList.clear();
            callList.addAll(channels);
            if (CallChannelAdapter != null) CallChannelAdapter.notifyDataSetChanged();
            isCallLoaded = true;
            checkDataLoaded();
        });

        viewModel.getPostChannels().observe(getViewLifecycleOwner(), channels -> {
            if (binding == null || channels == null) return;
            postList.clear();
            postList.addAll(channels);
            if (PostListAdapter != null) PostListAdapter.notifyDataSetChanged();
            isPostLoaded = true;
            checkDataLoaded();
        });

        viewModel.getStatusMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null || getContext() == null) return;

            switch (message) {
                case "CHANNEL_EXISTS":
                    Toast.makeText(getContext(), "Channel name already exists!", Toast.LENGTH_SHORT).show();
                    break;
                case "CREATE_SUCCESS":
                    Toast.makeText(getContext(), "Channel created successfully", Toast.LENGTH_SHORT).show();
                    break;
                case "RENAME_SUCCESS":
                    Toast.makeText(getContext(), "Channel renamed successfully", Toast.LENGTH_SHORT).show();
                    break;
                case "REMOVE_SUCCESS":
                    Toast.makeText(getContext(), "Channel removed successfully", Toast.LENGTH_SHORT).show();
                    break;
                case "UPDATE_SERVER_SUCCESS":
                    Toast.makeText(getContext(), "Server updated successfully", Toast.LENGTH_SHORT).show();
                    if (settingsDialog != null && settingsDialog.isShowing()) {
                        settingsDialog.dismiss();
                    }
                    break;
                case "UPLOAD_ICON_SUCCESS":
                    Toast.makeText(getContext(), "Avatar updated", Toast.LENGTH_SHORT).show();
                    break;
                case "REMOVE_ICON_SUCCESS":
                    Toast.makeText(getContext(), "Avatar removed", Toast.LENGTH_SHORT).show();
                    break;
                case "LEAVE_FAILED_LAST_ADMIN":
                    Toast.makeText(getContext(), "Không thể rời! Bạn là Admin/Owner duy nhất còn lại.", Toast.LENGTH_LONG).show();
                    break;
                case "LEAVE_SUCCESS":
                    Toast.makeText(getContext(), "Đã rời Server", Toast.LENGTH_SHORT).show();
                    break;
                case "DELETE_SERVER_SUCCESS":
                    Toast.makeText(getContext(), "Server deleted", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    break;
            }
            viewModel.resetStatus();
        });

        viewModel.getIsLeftOrDeleted().observe(getViewLifecycleOwner(), state -> {
            if (state != null && state && getView() != null) {
                Navigation.findNavController(getView()).popBackStack();
            }
        });

        viewModel.getIsUploaded().observe(getViewLifecycleOwner(), uploaded -> {
            if (uploaded != null && uploaded) {
                if (settingsDialog != null && settingsDialog.isShowing() && getContext() != null) {
                    // Refresh fields in settings dialog
                    if (dialogAvatarView != null && serverIconUrl != null) {
                        if (dialogAvatarLetter != null) dialogAvatarLetter.setVisibility(View.GONE);
                        dialogAvatarView.setVisibility(View.VISIBLE);
                        Glide.with(requireContext()).load(serverIconUrl).into(dialogAvatarView);
                        if (dialogRemoveAvatar != null) dialogRemoveAvatar.setVisibility(View.VISIBLE);
                    }
                }
                viewModel.resetUploaded();
            }
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

        binding.btnAddChannel.setOnClickListener(v -> handleAddChannelClick("chat"));
        binding.btnAddCallChannel.setOnClickListener(v -> handleAddChannelClick("call"));
        binding.btnAddPostChannel.setOnClickListener(v -> handleAddChannelClick("post"));

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
    
    private void handleAddChannelClick(String type) {
        if (getContext() == null || binding == null) return;
        int currentCount = "chat".equals(type) ? chatList.size() : ("call".equals(type) ? callList.size() : postList.size());

        android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE);
        String currentPlan = prefs.getString("current_plan", "Basic");
        int limit = 2;
        if ("Standard".equals(currentPlan)) limit = 5;
        else if ("Pro".equals(currentPlan)) limit = 10;

        if (currentCount >= limit) {
            com.example.se114_callingsystem.core.util.BottomSheetUtils.showConfirmDialog(
                requireContext(),
                "Plan Limit Reached",
                "The number of channels is limited to " + limit + " on your " + currentPlan + " plan. You should upgrade to a higher plan to create more.",
                "Upgrade",
                "#5865F2", // Blurple for upgrade
                () -> {
                    Navigation.findNavController(binding.getRoot()).navigate(R.id.nav_upgrade_plan);
                }
            );
        } else {
            showAddChannelDialog(type);
        }
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
        settingsDialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_server_settings, null);
        settingsDialog.setContentView(view);

        EditText etServerNameSettings = view.findViewById(R.id.etServerNameSettings);
        EditText etServerDescriptionSettings = view.findViewById(R.id.etServerDescriptionSettings);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveServerDetails);
        View btnDelete = view.findViewById(R.id.btnDeleteServer);
        View btnManageMembers = view.findViewById(R.id.btnManageMembers);
        View btnChangeColor = view.findViewById(R.id.btnChangeColor);

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
            dialogRemoveAvatar.setOnClickListener(v -> {
                viewModel.removeServerAvatar(serverId);
                if (dialogAvatarView != null) dialogAvatarView.setVisibility(View.GONE);
                if (dialogAvatarLetter != null) dialogAvatarLetter.setVisibility(View.VISIBLE);
                if (dialogRemoveAvatar != null) dialogRemoveAvatar.setVisibility(View.GONE);
            });
        }

        try {
            int color = Color.parseColor(currentAccentColor);
            if (btnSave != null) btnSave.setBackgroundTintList(ColorStateList.valueOf(color));
            if (dialogAvatarLetter != null) dialogAvatarLetter.setTextColor(color);
            com.google.android.material.card.MaterialCardView cardAvatar = view.findViewById(R.id.cardServerAvatarSettings);
            if (cardAvatar != null) cardAvatar.setStrokeColor(color);
        } catch (Exception e) {}

        View btnLeave = view.findViewById(R.id.btnLeaveServer);
        if (btnLeave != null) {
            btnLeave.setOnClickListener(v -> {
                String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                if (uid == null) return;
                
                com.example.se114_callingsystem.core.util.BottomSheetUtils.showConfirmDialog(
                    getContext(),
                    "Rời Server",
                    "Bạn có chắc chắn muốn rời khỏi Server này?",
                    "Rời đi",
                    "#F23F42",
                    () -> {
                        settingsDialog.dismiss();
                        viewModel.leaveServer(serverId, uid);
                    }
                );
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

                viewModel.updateServerDetails(serverId, newName, newPurpose);
            });
        }

        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> {
                settingsDialog.dismiss();
                showServerDeleteConfirm();
            });
        }

        if (btnManageMembers != null) {
            btnManageMembers.setOnClickListener(v -> {
                settingsDialog.dismiss();
                Bundle args = new Bundle();
                args.putString("SERVER_ID", serverId);
                Navigation.findNavController(binding.getRoot()).navigate(R.id.action_server_to_manage_members, args);
            });
        }

        if (btnChangeColor != null) {
            btnChangeColor.setOnClickListener(v -> {
                settingsDialog.dismiss();
                Bundle args = new Bundle();
                args.putString("SERVER_ID", serverId);
                args.putString("CURRENT_COLOR", currentAccentColor);
                Navigation.findNavController(binding.getRoot()).navigate(R.id.action_server_to_change_color, args);
            });
        }

        settingsDialog.show();
        View bottomSheet = settingsDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) bottomSheet.setBackgroundResource(android.R.color.transparent);
    }

    private void showServerDeleteConfirm() {
        if (getContext() == null) return;
        com.example.se114_callingsystem.core.util.BottomSheetUtils.showConfirmDialog(
                requireContext(),
                "Delete Server",
                "Are you sure you want to delete this server? This action cannot be undone.",
                "Delete",
                "#F23F42",
                () -> {
                    viewModel.deleteServer(serverId);
                }
        );
    }

    private void setupChatRecyclerView() {
        if (binding == null) return;
        chatAdapter = new ChatZoneAdapter(chatList, new ChatZoneAdapter.OnChannelActionListener() {
            @Override public void onRename(ChatChannel channel) { showBaseRenameDialog(channel.getChatId(), channel.getChatName(), "Channels", "chat"); }
            @Override public void onRemove(ChatChannel channel) { viewModel.removeChannel("chat", channel.getChatId()); }
        });
        chatAdapter.setAdmin(isAdminOrOwner);
        binding.rvChatChannels.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvChatChannels.setAdapter(chatAdapter);
        setupDragAndDrop(binding.rvChatChannels, chatList, chatAdapter, "chat");
    }

    private void setupCallRecyclerView() {
        if (binding == null) return;
        CallChannelAdapter = new CallChannelAdapter(callList, new CallChannelAdapter.OnCallActionListener() {
            @Override public void onRename(CallChannel channel) { showBaseRenameDialog(channel.getCallId(), channel.getCallName(), "CallChannels", "call"); }
            @Override public void onRemove(CallChannel channel) { viewModel.removeChannel("call", channel.getCallId()); }
            @Override
            public void onJoinCall(CallChannel channel) {
                if (getContext() != null && !com.example.se114_callingsystem.core.util.NetworkMonitor.isNetworkAvailable(getContext())) {
                    Toast.makeText(getContext(), "Không có kết nối mạng. Không thể tham gia cuộc gọi.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(requireContext(), com.example.se114_callingsystem.features.call.ui.CallActivity.class);
                intent.putExtra("CALL_CHANNEL_NAME", channel.getCallName());
                intent.putExtra("SERVER_ID", channel.getServerId());
                intent.putExtra("SERVER_COLOR", currentAccentColor);
                startActivity(intent);
            }
        });
        CallChannelAdapter.setAdmin(isAdminOrOwner);
        binding.rvCallChannels.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvCallChannels.setAdapter(CallChannelAdapter);
        setupDragAndDrop(binding.rvCallChannels, callList, CallChannelAdapter, "call");
    }

    private void setupPostRecyclerView() {
        if (binding == null) return;
        PostListAdapter = new PostChannelAdapter(postList, new PostChannelAdapter.OnChannelActionListener() {
            @Override public void onRename(PostChannel channel) { showBaseRenameDialog(channel.getId(), channel.getName(), "PostChannels", "post"); }
            @Override public void onRemove(PostChannel channel) { viewModel.removeChannel("post", channel.getId()); }
        });
        PostListAdapter.setAdmin(isAdminOrOwner);
        binding.rvPostChannels.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvPostChannels.setAdapter(PostListAdapter);
        setupDragAndDrop(binding.rvPostChannels, postList, PostListAdapter, "post");
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
                viewModel.updateChannelsOrder(type, list);
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
                int orderIndex = "chat".equals(type) ? chatList.size() : ("call".equals(type) ? callList.size() : postList.size());
                viewModel.createChannel(type, serverId, name, orderIndex);
                dialog.dismiss();
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
                viewModel.renameChannel(type, serverId, id, currentName, newName);
                dialog.dismiss();
            });
        }
        dialog.show();
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

    public void switchServer(String newServerId, String newServerName) {
        // Update server details
        this.serverId = newServerId;
        this.serverName = newServerName;
        this.serverPurpose = "";
        
        isChatLoaded = false;
        isCallLoaded = false;
        isPostLoaded = false;
        
        // Update UI state to loading
        if (binding != null) {
            binding.tvServerName.setText(serverName);
            binding.tvServerDescription.setVisibility(View.GONE);
            binding.shimmerViewContainer.setVisibility(View.VISIBLE);
            binding.shimmerViewContainer.startShimmer();
            binding.channelsContainer.setVisibility(View.GONE);
        }
        
        // Reload data
        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null ? 
            FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        viewModel.initServer(serverId, currentUid);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
