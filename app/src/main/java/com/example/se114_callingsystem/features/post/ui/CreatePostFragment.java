package com.example.se114_callingsystem.features.post.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.lifecycle.ViewModelProvider;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.features.post.viewmodel.PostViewModel;
import com.google.android.material.button.MaterialButton;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.List;

@AndroidEntryPoint
public class CreatePostFragment extends Fragment {

    private String channelId, serverId, serverColor, editPostId;
    private EditText etContent;
    private PostViewModel viewModel;
    private ProgressDialog progressDialog;
    
    // We will support multiple media upload
    private final List<Uri> selectedMediaUris = new ArrayList<>();
    private final List<String> selectedMediaTypes = new ArrayList<>();
    private LinearLayout mediaPreviewContainer;
    
    // Mentions
    private com.google.android.material.card.MaterialCardView cardMentionSuggestions;
    private androidx.recyclerview.widget.RecyclerView rvMentionSuggestions;
    private com.example.se114_callingsystem.core.util.MentionAdapter mentionAdapter;
    private final List<com.example.se114_callingsystem.core.model.ServerMember> serverMembers = new ArrayList<>();
    private final List<com.example.se114_callingsystem.core.model.ServerMember> filteredMembers = new ArrayList<>();
    private int mentionStartIndex = -1;

    private ActivityResultLauncher<Intent> mediaPickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mediaPickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        addMediaToSelection(uri);
                    }
                } else if (data.getData() != null) {
                    addMediaToSelection(data.getData());
                }
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_post_create, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(PostViewModel.class);

        if (getArguments() != null) {
            channelId = getArguments().getString("CHANNEL_ID");
            serverId = getArguments().getString("SERVER_ID");
            serverColor = getArguments().getString("SERVER_COLOR");
            editPostId = getArguments().getString("POST_ID");
            String editContent = getArguments().getString("POST_CONTENT");
            
            etContent = view.findViewById(R.id.etContent);
            if (editContent != null && etContent != null) {
                etContent.setText(editContent);
            }
        }

        ImageView btnBack = view.findViewById(R.id.btnBack);
        MaterialButton btnPost = view.findViewById(R.id.btnPost);
        ImageView btnAddImage = view.findViewById(R.id.btnAddImage);
        ImageView btnAddVideo = view.findViewById(R.id.btnAddVideo);
        mediaPreviewContainer = view.findViewById(R.id.mediaPreviewContainer);

        btnBack.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());
        
        setupMentionSuggestions(view);
        setupObservers();

        viewModel.loadServerMembers(serverId);

        try {
            if (serverColor != null) {
                int color = android.graphics.Color.parseColor(serverColor);
                btnPost.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                if (cardMentionSuggestions != null) {
                    cardMentionSuggestions.setStrokeColor(color);
                }
            }
        } catch (Exception e) {}

        btnAddImage.setOnClickListener(v -> pickMedia("image/*"));
        btnAddVideo.setOnClickListener(v -> pickMedia("video/*"));

        btnPost.setOnClickListener(v -> handlePost());
    }

    private void setupObservers() {
        viewModel.getServerMembers().observe(getViewLifecycleOwner(), members -> {
            if (members != null) {
                serverMembers.clear();
                serverMembers.addAll(members);
            }
        });

        viewModel.getOperationStatus().observe(getViewLifecycleOwner(), status -> {
            if (status == null || getContext() == null) return;

            if (status.equals("POSTING_START")) {
                showProgressDialog("Đang đăng bài...");
            } else if (status.startsWith("UPLOAD_PROGRESS:")) {
                String progress = status.substring("UPLOAD_PROGRESS:".length());
                showProgressDialog("Đang tải lên " + progress + "...");
            } else if (status.equals("POST_SUCCESS")) {
                hideProgressDialog();
                Toast.makeText(requireContext(), editPostId != null ? "Đã cập nhật bài viết!" : "Đã đăng bài thành công!", Toast.LENGTH_SHORT).show();
                Navigation.findNavController(requireView()).popBackStack();
            } else if (status.startsWith("POST_FAILED:")) {
                hideProgressDialog();
                String error = status.substring("POST_FAILED:".length());
                Toast.makeText(requireContext(), "Lỗi: " + error, Toast.LENGTH_SHORT).show();
            } else if (status.startsWith("VALIDATION_ERROR:")) {
                hideProgressDialog();
                String msg = status.substring("VALIDATION_ERROR:".length());
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
            viewModel.resetStatus();
        });
    }

    private void showProgressDialog(String message) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(requireContext());
            progressDialog.setCancelable(false);
        }
        progressDialog.setMessage(message);
        if (!progressDialog.isShowing()) {
            progressDialog.show();
        }
    }

    private void hideProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void setupMentionSuggestions(View view) {
        cardMentionSuggestions = view.findViewById(R.id.cardMentionSuggestions);
        rvMentionSuggestions = view.findViewById(R.id.rvMentionSuggestions);
        
        mentionAdapter = new com.example.se114_callingsystem.core.util.MentionAdapter(filteredMembers, member -> insertMention(member));
        rvMentionSuggestions.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
        rvMentionSuggestions.setAdapter(mentionAdapter);

        etContent.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                int cursor = etContent.getSelectionStart();
                if (cursor < 0) { hideMentionSuggestions(); return; }
                String text = s.toString();
                int atIndex = text.lastIndexOf("@", cursor - 1);
                if (atIndex != -1 && (atIndex == 0 || text.charAt(atIndex - 1) == ' ' || text.charAt(atIndex - 1) == '\n')) {
                    mentionStartIndex = atIndex;
                    String query = text.substring(atIndex + 1, cursor);
                    if (query.contains(" ") || query.contains("\n")) {
                        hideMentionSuggestions();
                    } else {
                        showMentionSuggestions(query);
                    }
                } else {
                    hideMentionSuggestions();
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void showMentionSuggestions(String query) {
        filteredMembers.clear();
        String lowercaseQuery = query.toLowerCase();
        for (com.example.se114_callingsystem.core.model.ServerMember member : serverMembers) {
            String name = member.getNickname() != null ? member.getNickname() : member.getUserName();
            if (name != null && name.toLowerCase().contains(lowercaseQuery)) {
                filteredMembers.add(member);
            }
        }
        if (filteredMembers.isEmpty()) {
            hideMentionSuggestions();
        } else {
            mentionAdapter.setList(filteredMembers);
            if (cardMentionSuggestions != null) cardMentionSuggestions.setVisibility(android.view.View.VISIBLE);
        }
    }

    private void hideMentionSuggestions() {
        mentionStartIndex = -1;
        if (cardMentionSuggestions != null) cardMentionSuggestions.setVisibility(android.view.View.GONE);
    }

    private void insertMention(com.example.se114_callingsystem.core.model.ServerMember member) {
        String nickname = member.getNickname() != null ? member.getNickname() : member.getUserName();
        String mention = "@" + nickname + " ";
        String text = etContent.getText().toString();
        String newText = text.substring(0, mentionStartIndex) + mention;
        etContent.setText(newText);
        etContent.setSelection(newText.length());
        hideMentionSuggestions();
    }

    private void pickMedia(String type) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(type);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        mediaPickerLauncher.launch(intent);
    }

    private void addMediaToSelection(Uri uri) {
        if (getContext() == null) return;
        selectedMediaUris.add(uri);
        String type = getContext().getContentResolver().getType(uri);
        if (type != null && type.startsWith("video")) selectedMediaTypes.add("video");
        else selectedMediaTypes.add("image");

        // Add a small preview
        if (mediaPreviewContainer != null) {
            ImageView preview = new ImageView(getContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(200, 200);
            params.setMargins(0, 0, 16, 0);
            preview.setLayoutParams(params);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            com.bumptech.glide.Glide.with(this).load(uri).into(preview);
            mediaPreviewContainer.addView(preview);
        }
    }

    private void handlePost() {
        String content = etContent.getText().toString().trim();
        viewModel.saveOrUpdatePost(editPostId, content, channelId, serverId, selectedMediaUris, selectedMediaTypes);
    }
}
