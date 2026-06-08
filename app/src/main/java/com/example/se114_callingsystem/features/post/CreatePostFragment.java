package com.example.se114_callingsystem.features.post;

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
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.Post;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.se114_callingsystem.core.di.AppDependencyProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreatePostFragment extends Fragment {

    private String channelId, serverId, serverColor, editPostId;
    private EditText etContent;
    private FirebaseFirestore db;
    
    // We will support multiple media upload
    private List<Uri> selectedMediaUris = new ArrayList<>();
    private List<String> selectedMediaTypes = new ArrayList<>();
    private LinearLayout mediaPreviewContainer;
    
    // Mentions
    private com.google.android.material.card.MaterialCardView cardMentionSuggestions;
    private androidx.recyclerview.widget.RecyclerView rvMentionSuggestions;
    private com.example.se114_callingsystem.core.util.MentionAdapter mentionAdapter;
    private List<com.example.se114_callingsystem.core.model.ServerMember> serverMembers = new ArrayList<>();
    private List<com.example.se114_callingsystem.core.model.ServerMember> filteredMembers = new ArrayList<>();
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
        
        db = AppDependencyProvider.getFirestore();

        ImageView btnBack = view.findViewById(R.id.btnBack);
        MaterialButton btnPost = view.findViewById(R.id.btnPost);
        ImageView btnAddImage = view.findViewById(R.id.btnAddImage);
        ImageView btnAddVideo = view.findViewById(R.id.btnAddVideo);
        mediaPreviewContainer = view.findViewById(R.id.mediaPreviewContainer);

        try {
            int color = android.graphics.Color.parseColor(serverColor);
            btnPost.setTextColor(color);
        } catch (Exception e) {}

        btnBack.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());
        
        setupMentionSuggestions(view);
        fetchServerMembers();

        btnAddImage.setOnClickListener(v -> pickMedia("image/*"));
        btnAddVideo.setOnClickListener(v -> pickMedia("video/*"));

        btnPost.setOnClickListener(v -> handlePost());
    }



    private void fetchServerMembers() {
        if (serverId != null) {
            db.collection("servers").document(serverId).collection("members").get().addOnSuccessListener(snapshots -> {
                if (getView() == null) return;
                serverMembers.clear();
                for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots) {
                    com.example.se114_callingsystem.core.model.ServerMember member = doc.toObject(com.example.se114_callingsystem.core.model.ServerMember.class);
                    if (member != null) serverMembers.add(member);
                }
            });
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
        if (content.isEmpty() && selectedMediaUris.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập nội dung hoặc chọn ảnh/video", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog pd = new ProgressDialog(requireContext());
        pd.setMessage("Đang đăng bài...");
        pd.setCancelable(false);
        pd.show();

        if (!selectedMediaUris.isEmpty()) {
            uploadMediaParallel(content, pd);
        } else {
            savePostToFirestore(content, new ArrayList<>(), pd);
        }
    }

    private void uploadMediaParallel(String content, ProgressDialog pd) {
        int total = selectedMediaUris.size();
        List<TaskCompletionSource<String>> tcsList = new ArrayList<>();
        List<Task<String>> tasks = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            TaskCompletionSource<String> tcs = new TaskCompletionSource<>();
            tcsList.add(tcs);
            tasks.add(tcs.getTask());
        }

        pd.setMessage("Đang tải lên 0/" + total + "...");

        java.util.concurrent.atomic.AtomicInteger finishedCount = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < total; i++) {
            final int index = i;
            Uri uri = selectedMediaUris.get(index);
            MediaManager.get().upload(uri).option("resource_type", "auto").callback(new UploadCallback() {
                @Override public void onStart(String requestId) {}
                @Override public void onProgress(String requestId, long bytes, long totalBytes) {}
                @Override public void onSuccess(String requestId, Map resultData) {
                    int currentFinished = finishedCount.incrementAndGet();
                    if (getContext() != null) {
                        pd.setMessage("Đang tải lên " + currentFinished + "/" + total + "...");
                    }
                    tcsList.get(index).setResult((String) resultData.get("secure_url"));
                }
                @Override public void onError(String requestId, ErrorInfo error) {
                    tcsList.get(index).setException(new Exception(error.getDescription()));
                }
                @Override public void onReschedule(String requestId, ErrorInfo error) {}
            }).dispatch();
        }

        Tasks.whenAll(tasks)
            .addOnSuccessListener(aVoid -> {
                List<String> uploadedUrls = new ArrayList<>();
                for (Task<String> task : tasks) {
                    uploadedUrls.add(task.getResult());
                }
                savePostToFirestore(content, uploadedUrls, pd);
            })
            .addOnFailureListener(e -> {
                pd.dismiss();
                if (getContext() != null) {
                    Toast.makeText(requireContext(), "Upload lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void savePostToFirestore(String content, List<String> mediaUrls, ProgressDialog pd) {
        String uid = AppDependencyProvider.getFirebaseAuth().getUid();
        if (uid == null) { pd.dismiss(); return; }

        if (editPostId != null) {
            // Update existing post
            db.collection("Posts").document(editPostId).update("content", content).addOnSuccessListener(a -> {
                pd.dismiss();
                if (getContext() != null) {
                    Toast.makeText(requireContext(), "Đã cập nhật bài viết!", Toast.LENGTH_SHORT).show();
                }
                Navigation.findNavController(requireView()).popBackStack();
            }).addOnFailureListener(e -> {
                pd.dismiss();
                if (getContext() != null) {
                    Toast.makeText(requireContext(), "Lỗi cập nhật", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        List<String> mediaTypes = new ArrayList<>();
        if (editPostId == null && !mediaUrls.isEmpty()) {
            mediaTypes.addAll(selectedMediaTypes);
        }

        Post post = new Post(null, channelId, serverId, uid, content, mediaUrls, mediaTypes, System.currentTimeMillis());
        post.setReactions(new HashMap<>());
        
        db.collection("Posts").add(post).addOnSuccessListener(doc -> {
            post.setId(doc.getId());
            db.collection("Posts").document(doc.getId()).set(post).addOnSuccessListener(a -> {
                pd.dismiss();
                if (getContext() != null) {
                    Toast.makeText(requireContext(), "Đã đăng bài thành công!", Toast.LENGTH_SHORT).show();
                }
                Navigation.findNavController(requireView()).popBackStack();
            });
        }).addOnFailureListener(e -> {
            pd.dismiss();
            if (getContext() != null) {
                Toast.makeText(requireContext(), "Lỗi đăng bài", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
