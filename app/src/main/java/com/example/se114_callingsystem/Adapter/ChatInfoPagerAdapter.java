package com.example.se114_callingsystem.Adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.se114_callingsystem.Activity.Fragment.MediaGridFragment;
import com.example.se114_callingsystem.Activity.Fragment.SharedFilesFragment;
import com.example.se114_callingsystem.Activity.Fragment.SharedLinksFragment;
import com.example.se114_callingsystem.Model.Message;

import java.util.List;

public class ChatInfoPagerAdapter extends FragmentStateAdapter {

    private final List<String> mediaUrls;
    private final List<Message> fileMessages;
    private final List<String[]> linkItems;

    public ChatInfoPagerAdapter(@NonNull FragmentActivity fragmentActivity,
                                 List<String> mediaUrls,
                                 List<Message> fileMessages,
                                 List<String[]> linkItems) {
        super(fragmentActivity);
        this.mediaUrls = mediaUrls;
        this.fileMessages = fileMessages;
        this.linkItems = linkItems;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new MediaGridFragment();
            case 1: return new SharedFilesFragment();
            case 2: return new SharedLinksFragment();
            default: return new MediaGridFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
