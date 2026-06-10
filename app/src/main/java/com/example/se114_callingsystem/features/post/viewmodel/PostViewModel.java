package com.example.se114_callingsystem.features.post.viewmodel;

import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.example.se114_callingsystem.core.model.ChatChannel;
import com.example.se114_callingsystem.core.model.Comment;
import com.example.se114_callingsystem.core.model.Post;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.example.se114_callingsystem.features.post.data.PostRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class PostViewModel extends ViewModel {

    private final PostRepository repository;

    private final MutableLiveData<List<Post>> posts = new MutableLiveData<>();
    private final MutableLiveData<List<ServerMember>> serverMembers = new MutableLiveData<>();
    private final MutableLiveData<List<Comment>> comments = new MutableLiveData<>();
    private final MutableLiveData<List<ChatChannel>> serverChannels = new MutableLiveData<>();
    private final MutableLiveData<String> operationStatus = new MutableLiveData<>();
    
    private ListenerRegistration postsListener;
    private ListenerRegistration commentsListener;

    @Inject
    public PostViewModel(PostRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<Post>> getPosts() { return posts; }
    public LiveData<List<ServerMember>> getServerMembers() { return serverMembers; }
    public LiveData<List<Comment>> getComments() { return comments; }
    public LiveData<List<ChatChannel>> getServerChannels() { return serverChannels; }
    public LiveData<String> getOperationStatus() { return operationStatus; }

    public String getCurrentUserId() {
        return repository.getCurrentUserId();
    }

    public void loadPosts(String channelId) {
        clearPostsListener();
        postsListener = repository.listenToPosts(channelId, new PostRepository.RealtimeCallback<List<Post>>() {
            @Override
            public void onData(List<Post> data) {
                posts.setValue(data);
            }

            @Override
            public void onError(Exception e) {
                operationStatus.setValue("LOAD_POSTS_FAILED: " + e.getMessage());
            }
        });
    }

    public void loadServerMembers(String serverId) {
        repository.fetchServerMembers(serverId, new PostRepository.RepositoryCallback<List<ServerMember>>() {
            @Override
            public void onSuccess(List<ServerMember> result) {
                serverMembers.setValue(result);
            }

            @Override
            public void onFailure(Exception e) {
                operationStatus.setValue("LOAD_MEMBERS_FAILED: " + e.getMessage());
            }
        });
    }

    public void handleLike(Post post, String emoji) {
        String userId = getCurrentUserId();
        if (userId == null) return;
        repository.updateReaction(post, emoji, userId, new PostRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {}
            @Override
            public void onFailure(Exception e) {
                operationStatus.setValue("LIKE_FAILED: " + e.getMessage());
            }
        });
    }

    public void deletePost(String postId) {
        repository.deletePost(postId, new PostRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                operationStatus.setValue("DELETE_POST_SUCCESS");
            }

            @Override
            public void onFailure(Exception e) {
                operationStatus.setValue("DELETE_POST_FAILED: " + e.getMessage());
            }
        });
    }

    public void loadServerChannels(String serverId) {
        repository.fetchChannelsForServer(serverId, new PostRepository.RepositoryCallback<List<ChatChannel>>() {
            @Override
            public void onSuccess(List<ChatChannel> result) {
                serverChannels.setValue(result);
            }

            @Override
            public void onFailure(Exception e) {
                operationStatus.setValue("LOAD_CHANNELS_FAILED: " + e.getMessage());
            }
        });
    }

    public void sharePostToChannel(Post post, ChatChannel channel) {
        String userId = getCurrentUserId();
        if (userId == null) return;
        repository.sharePostToChannel(post, channel, userId, new PostRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                operationStatus.setValue("SHARE_SUCCESS:" + channel.getChatName());
            }

            @Override
            public void onFailure(Exception e) {
                operationStatus.setValue("SHARE_FAILED: " + e.getMessage());
            }
        });
    }

    public void loadComments(String postId) {
        clearCommentsListener();
        commentsListener = repository.listenToComments(postId, new PostRepository.RealtimeCallback<List<Comment>>() {
            @Override
            public void onData(List<Comment> data) {
                comments.setValue(data);
            }

            @Override
            public void onError(Exception e) {
                operationStatus.setValue("LOAD_COMMENTS_FAILED: " + e.getMessage());
            }
        });
    }

    public void addComment(String postId, String text, String replyToCommentId, String replyToAuthorName) {
        String uid = getCurrentUserId();
        if (uid == null) return;

        Comment c = new Comment(null, postId, uid, text, System.currentTimeMillis());
        if (replyToCommentId != null) {
            c.setParentCommentId(replyToCommentId);
            c.setParentCommentAuthorName(replyToAuthorName);
        }

        repository.addComment(postId, c, new PostRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                operationStatus.setValue("ADD_COMMENT_SUCCESS");
            }

            @Override
            public void onFailure(Exception e) {
                operationStatus.setValue("ADD_COMMENT_FAILED: " + e.getMessage());
            }
        });
    }

    public void deleteComment(String postId, Comment comment) {
        repository.deleteComment(postId, comment, new PostRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                operationStatus.setValue("DELETE_COMMENT_SUCCESS");
            }

            @Override
            public void onFailure(Exception e) {
                operationStatus.setValue("DELETE_COMMENT_FAILED: " + e.getMessage());
            }
        });
    }

    public void saveOrUpdatePost(String editPostId, String content, String channelId, String serverId, List<Uri> selectedMediaUris, List<String> selectedMediaTypes) {
        if (content.isEmpty() && selectedMediaUris.isEmpty()) {
            operationStatus.setValue("VALIDATION_ERROR: Vui lòng nhập nội dung hoặc chọn ảnh/video");
            return;
        }

        operationStatus.setValue("POSTING_START");

        if (editPostId != null) {
            repository.updatePostContent(editPostId, content, new PostRepository.RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    operationStatus.setValue("POST_SUCCESS");
                }

                @Override
                public void onFailure(Exception e) {
                    operationStatus.setValue("POST_FAILED: " + e.getMessage());
                }
            });
        } else {
            if (!selectedMediaUris.isEmpty()) {
                uploadMediaParallelAndSave(content, channelId, serverId, selectedMediaUris, selectedMediaTypes);
            } else {
                savePostToFirestore(content, channelId, serverId, new ArrayList<>(), new ArrayList<>());
            }
        }
    }

    private void uploadMediaParallelAndSave(String content, String channelId, String serverId, List<Uri> selectedMediaUris, List<String> selectedMediaTypes) {
        int total = selectedMediaUris.size();
        List<TaskCompletionSource<String>> tcsList = new ArrayList<>();
        List<Task<String>> tasks = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            TaskCompletionSource<String> tcs = new TaskCompletionSource<>();
            tcsList.add(tcs);
            tasks.add(tcs.getTask());
        }

        AtomicInteger finishedCount = new AtomicInteger(0);

        for (int i = 0; i < total; i++) {
            final int index = i;
            Uri uri = selectedMediaUris.get(index);
            MediaManager.get().upload(uri).option("resource_type", "auto").callback(new UploadCallback() {
                @Override public void onStart(String requestId) {}
                @Override public void onProgress(String requestId, long bytes, long totalBytes) {}
                @Override public void onSuccess(String requestId, Map resultData) {
                    int currentFinished = finishedCount.incrementAndGet();
                    operationStatus.postValue("UPLOAD_PROGRESS: " + currentFinished + "/" + total);
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
                savePostToFirestore(content, channelId, serverId, uploadedUrls, selectedMediaTypes);
            })
            .addOnFailureListener(e -> {
                operationStatus.setValue("POST_FAILED: Upload lỗi - " + e.getMessage());
            });
    }

    private void savePostToFirestore(String content, String channelId, String serverId, List<String> mediaUrls, List<String> mediaTypes) {
        String uid = getCurrentUserId();
        if (uid == null) {
            operationStatus.setValue("POST_FAILED: User not authenticated");
            return;
        }

        Post post = new Post(null, channelId, serverId, uid, content, mediaUrls, mediaTypes, System.currentTimeMillis());
        post.setReactions(new HashMap<>());

        repository.savePost(post, new PostRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                operationStatus.setValue("POST_SUCCESS");
            }

            @Override
            public void onFailure(Exception e) {
                operationStatus.setValue("POST_FAILED: " + e.getMessage());
            }
        });
    }

    public void resetStatus() {
        operationStatus.setValue(null);
    }

    private void clearPostsListener() {
        if (postsListener != null) {
            postsListener.remove();
            postsListener = null;
        }
    }

    private void clearCommentsListener() {
        if (commentsListener != null) {
            commentsListener.remove();
            commentsListener = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        clearPostsListener();
        clearCommentsListener();
    }
}
