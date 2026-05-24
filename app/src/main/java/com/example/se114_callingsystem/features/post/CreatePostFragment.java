package com.example.se114_callingsystem.features.post;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.Post;
import com.example.se114_callingsystem.core.util.ThemeHelper;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreatePostActivity extends AppCompatActivity {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_create);

        channelId = getIntent().getStringExtra("CHANNEL_ID");
        serverId = getIntent().getStringExtra("SERVER_ID");
        serverColor = getIntent().getStringExtra("SERVER_COLOR");
        editPostId = getIntent().getStringExtra("POST_ID");
        String editContent = getIntent().getStringExtra("POST_CONTENT");
        db = FirebaseFirestore.getInstance();

        initCloudinary();

        etContent = findViewById(R.id.etContent);
        if (editContent != null) {
            etContent.setText(editContent);
        }
        
        ImageView btnBack = findViewById(R.id.btnBack);
        MaterialButton btnPost = findViewById(R.id.btnPost);
        ImageView btnAddImage = findViewById(R.id.btnAddImage);
        ImageView btnAddVideo = findViewById(R.id.btnAddVideo);
        mediaPreviewContainer = findViewById(R.id.mediaPreviewContainer); // Need to add this to XML

        try {
            int color = android.graphics.Color.parseColor(serverColor);
            btnPost.setTextColor(color);
        } catch (Exception e) {}

        btnBack.setOnClickListener(v -> finish());
        
        setupMentionSuggestions();
        fetchServerMembers();

        btnAddImage.setOnClickListener(v -> pickMedia("image/*"));
        btnAddVideo.setOnClickListener(v -> pickMedia("video/*"));

        btnPost.setOnClickListener(v -> handlePost());
    }

    private void initCloudinary() {
        Map config = new HashMap();
        config.put("cloud_name", "dxoukp0yb");
        config.put("api_key", "359217744855482");
        config.put("api_secret", "eTG0UvW_hdsHm4hl0r2XJCvidR0");
        try {
            MediaManager.init(this, config);
        } catch (IllegalStateException e) {}
    }

    private void fetchServerMembers() {
        if (serverId != null) {
            db.collection("servers").document(serverId).collection("members").get().addOnSuccessListener(snapshots -> {
                serverMembers.clear();
                for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots) {
                    com.example.se114_callingsystem.core.model.ServerMember member = doc.toObject(com.example.se114_callingsystem.core.model.ServerMember.class);
                    if (member != null) serverMembers.add(member);
                }
            });
        }
    }

    private void setupMentionSuggestions() {
        cardMentionSuggestions = findViewById(R.id.cardMentionSuggestions);
        rvMentionSuggestions = findViewById(R.id.rvMentionSuggestions);
        
        mentionAdapter = new com.example.se114_callingsystem.core.util.MentionAdapter(filteredMembers, member -> insertMention(member));
        rvMentionSuggestions.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
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
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
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
    }

    private void addMediaToSelection(Uri uri) {
        selectedMediaUris.add(uri);
        String type = getContentResolver().getType(uri);
        if (type != null && type.startsWith("video")) selectedMediaTypes.add("video");
        else selectedMediaTypes.add("image");

        // Add a small preview
        if (mediaPreviewContainer != null) {
            ImageView preview = new ImageView(this);
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
            Toast.makeText(this, "Vui lÃ²ng nháº­p ná»™i dung hoáº·c chá»n áº£nh/video", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Äang Ä‘Äƒng bÃ i...");
        pd.setCancelable(false);
        pd.show();

        if (!selectedMediaUris.isEmpty()) {
            List<String> uploadedUrls = new ArrayList<>();
            uploadMediaRecursive(0, uploadedUrls, content, pd);
        } else {
            savePostToFirestore(content, new ArrayList<>(), pd);
        }
    }

    private void uploadMediaRecursive(int index, List<String> uploadedUrls, String content, ProgressDialog pd) {
        if (index >= selectedMediaUris.size()) {
            savePostToFirestore(content, uploadedUrls, pd);
            return;
        }

        Uri uri = selectedMediaUris.get(index);
        MediaManager.get().upload(uri).option("resource_type", "auto").callback(new UploadCallback() {
            @Override public void onStart(String requestId) {
                pd.setMessage("Äang táº£i lÃªn " + (index + 1) + "/" + selectedMediaUris.size() + "...");
            }
            @Override public void onProgress(String requestId, long bytes, long totalBytes) {}
            @Override public void onSuccess(String requestId, Map resultData) {
                uploadedUrls.add((String) resultData.get("secure_url"));
                uploadMediaRecursive(index + 1, uploadedUrls, content, pd);
            }
            @Override public void onError(String requestId, ErrorInfo error) {
                pd.dismiss();
                Toast.makeText(CreatePostActivity.this, "Upload lá»—i: " + error.getDescription(), Toast.LENGTH_SHORT).show();
            }
            @Override public void onReschedule(String requestId, ErrorInfo error) {}
        }).dispatch();
    }

    private void savePostToFirestore(String content, List<String> mediaUrls, ProgressDialog pd) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) { pd.dismiss(); return; }

        if (editPostId != null) {
            // Update existing post
            db.collection("Posts").document(editPostId).update("content", content).addOnSuccessListener(a -> {
                pd.dismiss();
                Toast.makeText(this, "ÄÃ£ cáº­p nháº­t bÃ i viáº¿t!", Toast.LENGTH_SHORT).show();
                finish();
            }).addOnFailureListener(e -> {
                pd.dismiss();
                Toast.makeText(this, "Lá»—i cáº­p nháº­t", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "ÄÃ£ Ä‘Äƒng bÃ i thÃ nh cÃ´ng!", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).addOnFailureListener(e -> {
            pd.dismiss();
            Toast.makeText(this, "Lá»—i Ä‘Äƒng bÃ i", Toast.LENGTH_SHORT).show();
        });
    }
}

