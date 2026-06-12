package com.example.se114_callingsystem.features.post.ui;

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
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.databinding.FragmentPostChannelBinding;
import com.example.se114_callingsystem.core.model.ChatChannel;
import com.example.se114_callingsystem.core.model.Message;
import com.example.se114_callingsystem.core.model.Post;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.example.se114_callingsystem.features.post.viewmodel.PostViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.List;

@AndroidEntryPoint
public class PostChannelFragment extends Fragment {

    private FragmentPostChannelBinding binding;
    private String channelId, channelName, serverId, serverColor;
    private PostListAdapter PostListAdapter;
    private final List<Post> postList = new ArrayList<>();
    private PostViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPostChannelBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(PostViewModel.class);

        if (getArguments() != null) {
            channelId = getArguments().getString("CHANNEL_ID");
            channelName = getArguments().getString("CHANNEL_NAME");
            serverId = getArguments().getString("SERVER_ID");
            serverColor = getArguments().getString("SERVER_COLOR");
        }

        if (channelName != null) {
            binding.tvChannelName.setText("📰 " + channelName);
        }

        binding.btnBack.setOnClickListener(v -> {
            Navigation.findNavController(v).popBackStack();
        });

        // Set create post launch from FAB
        binding.fabCreatePost.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("CHANNEL_ID", channelId);
            bundle.putString("SERVER_ID", serverId);
            bundle.putString("SERVER_COLOR", serverColor);
            Navigation.findNavController(v).navigate(R.id.action_post_channel_to_post_create, bundle);
        });

        // Set create post launch from Empty State CTA
        binding.btnEmptyCreatePost.setOnClickListener(v -> {
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
                binding.btnEmptyCreatePost.setBackgroundTintList(ColorStateList.valueOf(color));
                // 10% opacity backdrop tint for circular icon holder
                int translucentColor = (color & 0x00FFFFFF) | 0x1A000000;
                binding.emptyIconContainer.setBackgroundTintList(ColorStateList.valueOf(translucentColor));
            }
        } catch (Exception e) {}

        setupRecyclerView();
        setupObservers();

        viewModel.loadPosts(channelId);
        viewModel.loadServerMembers(serverId);
    }

    private void setupObservers() {
        viewModel.getPosts().observe(getViewLifecycleOwner(), list -> {
            if (binding == null) return;
            postList.clear();
            if (list != null) {
                postList.addAll(list);
            }
            PostListAdapter.notifyDataSetChanged();

            // Toggle Empty State Layout
            if (postList.isEmpty()) {
                binding.layoutEmptyState.setVisibility(View.VISIBLE);
                binding.rvPosts.setVisibility(View.GONE);
            } else {
                binding.layoutEmptyState.setVisibility(View.GONE);
                binding.rvPosts.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getServerMembers().observe(getViewLifecycleOwner(), members -> {
            if (binding == null || members == null) return;
            PostListAdapter.setServerMembers(members);
        });

        viewModel.getOperationStatus().observe(getViewLifecycleOwner(), status -> {
            if (status == null || getContext() == null) return;
            if (status.startsWith("SHARE_SUCCESS:")) {
                String name = status.substring("SHARE_SUCCESS:".length());
                Toast.makeText(getContext(), "Đã chia sẻ bài viết vào " + name, Toast.LENGTH_SHORT).show();
            } else if (status.startsWith("SHARE_FAILED:")) {
                Toast.makeText(getContext(), "Chia sẻ lỗi: " + status, Toast.LENGTH_SHORT).show();
            } else if (status.equals("DELETE_POST_SUCCESS")) {
                Toast.makeText(getContext(), "Đã xóa bài đăng", Toast.LENGTH_SHORT).show();
            } else if (status.startsWith("DELETE_POST_FAILED:")) {
                Toast.makeText(getContext(), "Lỗi xóa bài đăng: " + status, Toast.LENGTH_SHORT).show();
            }
            viewModel.resetStatus();
        });
    }

    private void setupRecyclerView() {
        PostListAdapter = new PostListAdapter(requireContext(), postList, serverColor, new PostListAdapter.OnPostInteractionListener() {
            @Override
            public void onLikeClick(Post post, String emoji) {
                viewModel.handleLike(post, emoji);
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

    private void showEmojiPicker(Post post, View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), anchor);
        popup.getMenu().add("👍");
        popup.getMenu().add("❤️");
        popup.getMenu().add("😂");
        popup.getMenu().add("😮");
        popup.getMenu().add("😢");
        popup.getMenu().add("😡");
        popup.setOnMenuItemClickListener(item -> {
            viewModel.handleLike(post, item.getTitle().toString());
            return true;
        });
        popup.show();
    }

    private void showPostOptions(Post post, View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), anchor);
        popup.getMenu().add("Chỉnh sửa");
        popup.getMenu().add("Xóa");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Chỉnh sửa")) {
                Bundle bundle = new Bundle();
                bundle.putString("CHANNEL_ID", channelId);
                bundle.putString("SERVER_ID", serverId);
                bundle.putString("SERVER_COLOR", serverColor);
                bundle.putString("POST_ID", post.getId());
                bundle.putString("POST_CONTENT", post.getContent());
                Navigation.findNavController(binding.getRoot()).navigate(R.id.action_post_channel_to_post_create, bundle);
            } else if (item.getTitle().equals("Xóa")) {
                viewModel.deletePost(post.getId());
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

        viewModel.getServerChannels().observe(getViewLifecycleOwner(), channels -> {
            if (channels != null) {
                channelList.clear();
                channelList.addAll(channels);
                channelNames.clear();
                for (ChatChannel c : channels) {
                    channelNames.add("# " + c.getChatName());
                }
                adapter.notifyDataSetChanged();
            }
        });

        viewModel.loadServerChannels(serverId);

        lvChannels.setOnItemClickListener((parent, view1, position, id) -> {
            ChatChannel targetChannel = channelList.get(position);
            viewModel.sharePostToChannel(post, targetChannel);
            bottomSheet.dismiss();
        });
        
        bottomSheet.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
