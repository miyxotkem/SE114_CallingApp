package com.example.se114_callingsystem.features.post;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.databinding.FragmentPostChannelBinding;
import com.example.se114_callingsystem.core.model.ChatChannel;
import com.example.se114_callingsystem.core.model.Message;
import com.example.se114_callingsystem.core.model.Post;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostChannelFragment extends Fragment {

    private FragmentPostChannelBinding binding;
    private String channelId, channelName, serverId, serverColor;
    private PostListAdapter PostListAdapter;
    private List<Post> postList = new ArrayList<>();
    private FirebaseFirestore db;
    private String currentUserId;
    private ListenerRegistration postsListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPostChannelBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            channelId = getArguments().getString("CHANNEL_ID");
            channelName = getArguments().getString("CHANNEL_NAME");
            serverId = getArguments().getString("SERVER_ID");
            serverColor = getArguments().getString("SERVER_COLOR");
        }

        if (channelName != null) {
            binding.tvChannelName.setText("ðŸ“° " + channelName);
        }

        binding.btnBack.setOnClickListener(v -> {
            Navigation.findNavController(v).popBackStack();
        });

        binding.fabCreatePost.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("CHANNEL_ID", channelId);
            bundle.putString("SERVER_ID", serverId);
            bundle.putString("SERVER_COLOR", serverColor);
            Navigation.findNavController(v).navigate(R.id.action_post_channel_to_post_create, bundle);
        });

        try {
            if (serverColor != null) {
                int color = Color.parseColor(serverColor);
                binding.fabCreatePost.setBackgroundTintList(ColorStateList.valueOf(color));
            }
        } catch (Exception e) {}

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();

        setupRecyclerView();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPosts();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (postsListener != null) {
            postsListener.remove();
            postsListener = null;
        }
    }

    private void setupRecyclerView() {
        PostListAdapter = new PostListAdapter(requireContext(), postList, serverColor, new PostListAdapter.OnPostInteractionListener() {
            @Override
            public void onLikeClick(Post post, String emoji) {
                handleLike(post, emoji);
            }

            @Override
            public void onLikeLongClick(Post post, View anchorView) {
                showEmojiPicker(post, anchorView);
            }

            @Override
            public void onOptionsClick(Post post, View anchorView) {
                showPostOptions(post, anchorView);
            }

            @Override
            public void onCommentClick(Post post) {
                Bundle bundle = new Bundle();
                bundle.putString("POST_ID", post.getId());
                bundle.putString("POST_AUTHOR_ID", post.getAuthorId());
                bundle.putString("SERVER_ID", post.getServerId());
                bundle.putString("SERVER_COLOR", serverColor);
                Navigation.findNavController(binding.getRoot()).navigate(R.id.action_post_channel_to_post_comment, bundle);
            }

            @Override
            public void onShareClick(Post post) {
                showShareBottomSheet(post);
            }

            @Override
            public void onMediaClick(String url, String type) {
                // View media in fullscreen later
            }
        });
        binding.rvPosts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPosts.setAdapter(PostListAdapter);
    }

    private void loadPosts() {
        if (channelId == null) return;

        postsListener = db.collection("Posts")
          .whereEqualTo("channelId", channelId)
          .addSnapshotListener((snapshots, error) -> {
              if (error != null) return;
              if (snapshots != null && binding != null) {
                  postList.clear();
                  for (DocumentSnapshot doc : snapshots) {
                      Post p = doc.toObject(Post.class);
                      if (p != null) { 
                          p.setId(doc.getId()); 
                          postList.add(p); 
                      }
                  }
                  Collections.sort(postList, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                  PostListAdapter.notifyDataSetChanged();
              }
          });

        if (serverId != null) {
            db.collection("servers").document(serverId).collection("members").get().addOnSuccessListener(snapshots -> {
                if (snapshots != null && binding != null) {
                    List<ServerMember> members = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots) {
                        ServerMember member = doc.toObject(ServerMember.class);
                        if (member != null) {
                            members.add(member);
                        }
                    }
                    PostListAdapter.setServerMembers(members);
                }
            });
        }
    }

    private void handleLike(Post post, String emoji) {
        if (currentUserId == null) return;
        Map<String, String> reactions = post.getReactions();
        if (reactions == null) reactions = new HashMap<>();

        if (reactions.containsKey(currentUserId) && reactions.get(currentUserId).equals(emoji)) {
            reactions.remove(currentUserId);
        } else {
            reactions.put(currentUserId, emoji);
        }

        db.collection("Posts").document(post.getId()).update("reactions", reactions);
    }

    private void showEmojiPicker(Post post, View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), anchor);
        popup.getMenu().add("ðŸ‘");
        popup.getMenu().add("â¤ï¸");
        popup.getMenu().add("ðŸ˜‚");
        popup.getMenu().add("ðŸ˜®");
        popup.getMenu().add("ðŸ˜¢");
        popup.getMenu().add("ðŸ˜¡");
        popup.setOnMenuItemClickListener(item -> {
            handleLike(post, item.getTitle().toString());
            return true;
        });
        popup.show();
    }

    private void showPostOptions(Post post, View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), anchor);
        popup.getMenu().add("Chá»‰nh sá»­a");
        popup.getMenu().add("XÃ³a");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Chá»‰nh sá»­a")) {
                Bundle bundle = new Bundle();
                bundle.putString("CHANNEL_ID", channelId);
                bundle.putString("SERVER_ID", serverId);
                bundle.putString("SERVER_COLOR", serverColor);
                bundle.putString("POST_ID", post.getId());
                bundle.putString("POST_CONTENT", post.getContent());
                Navigation.findNavController(binding.getRoot()).navigate(R.id.action_post_channel_to_post_create, bundle);
            } else if (item.getTitle().equals("XÃ³a")) {
                db.collection("Posts").document(post.getId()).delete();
            }
            return true;
        });
        popup.show();
    }

    private void showShareBottomSheet(Post post) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_post_share, null);
        bottomSheet.setContentView(view);
        
        android.widget.ListView lvChannels = view.findViewById(R.id.lvChannels);
        List<ChatChannel> channelList = new ArrayList<>();
        List<String> channelNames = new ArrayList<>();
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, channelNames);
        lvChannels.setAdapter(adapter);

        db.collection("Channels").whereEqualTo("serverId", serverId).get().addOnSuccessListener(snapshots -> {
            if (snapshots != null) {
                for (DocumentSnapshot doc : snapshots) {
                    ChatChannel c = doc.toObject(ChatChannel.class);
                    if (c != null) { 
                        c.setChatId(doc.getId()); 
                        channelList.add(c); 
                        channelNames.add("# " + c.getChatName());
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });

        lvChannels.setOnItemClickListener((parent, view1, position, id) -> {
            ChatChannel targetChannel = channelList.get(position);
            sharePostToChannel(post, targetChannel);
            bottomSheet.dismiss();
        });
        
        bottomSheet.show();
    }

    private void sharePostToChannel(Post post, ChatChannel channel) {
        Message msg = new Message(currentUserId, channel.getChatId(), post.getId(), System.currentTimeMillis());
        msg.setType("post_share");
        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("chats")
            .child(channel.getChatId()).push().setValue(msg)
            .addOnSuccessListener(a -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "ÄÃ£ chia sáº» bÃ i viáº¿t vÃ o " + channel.getChatName(), Toast.LENGTH_SHORT).show();
                }
            });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

