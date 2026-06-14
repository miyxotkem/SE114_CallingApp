package com.example.se114_callingsystem.core.viewer;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.example.se114_callingsystem.R;

public class VideoViewerFragment extends Fragment {

    private VideoView videoView;
    private ProgressBar progressBar;
    private ImageButton btnBack;
    private ImageButton btnPlayPause;
    private SeekBar seekBarProgress;
    private TextView tvVideoTime;
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isUserSeeking = false;

    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (videoView != null && videoView.isPlaying() && !isUserSeeking) {
                int currentPosition = videoView.getCurrentPosition();
                int duration = videoView.getDuration();
                if (duration > 0) {
                    seekBarProgress.setProgress(currentPosition);
                    tvVideoTime.setText(formatTime(currentPosition) + " / " + formatTime(duration));
                }
            }
            handler.postDelayed(this, 100);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_core_video_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        videoView = view.findViewById(R.id.videoView);
        progressBar = view.findViewById(R.id.progressBar);
        btnBack = view.findViewById(R.id.btnBackFromVideo);
        btnPlayPause = view.findViewById(R.id.btnPlayPause);
        seekBarProgress = view.findViewById(R.id.seekBarVideoProgress);
        tvVideoTime = view.findViewById(R.id.tvVideoTime);

        String videoUrl = null;
        if (getArguments() != null) {
            videoUrl = getArguments().getString("VIDEO_URL");
        }

        if (videoUrl != null) {
            Uri videoUri = Uri.parse(videoUrl);
            videoView.setVideoURI(videoUri);

            videoView.setOnPreparedListener(mp -> {
                progressBar.setVisibility(View.GONE);
                int duration = videoView.getDuration();
                seekBarProgress.setMax(duration);
                seekBarProgress.setProgress(0);
                tvVideoTime.setText("00:00 / " + formatTime(duration));
                videoView.start();
                btnPlayPause.setImageResource(R.drawable.ic_pause);
                handler.post(updateProgressRunnable);
            });

            videoView.setOnCompletionListener(mp -> {
                btnPlayPause.setImageResource(R.drawable.ic_play);
                seekBarProgress.setProgress(videoView.getDuration());
                tvVideoTime.setText(formatTime(videoView.getDuration()) + " / " + formatTime(videoView.getDuration()));
            });

            videoView.setOnErrorListener((mp, what, extra) -> {
                progressBar.setVisibility(View.GONE);
                if (getContext() != null) {
                    android.widget.Toast.makeText(getContext(), "Không thể phát video này", android.widget.Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        } else {
            progressBar.setVisibility(View.GONE);
        }

        btnBack.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());

        btnPlayPause.setOnClickListener(v -> {
            if (videoView.isPlaying()) {
                videoView.pause();
                btnPlayPause.setImageResource(R.drawable.ic_play);
            } else {
                videoView.start();
                btnPlayPause.setImageResource(R.drawable.ic_pause);
            }
        });

        seekBarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvVideoTime.setText(formatTime(progress) + " / " + formatTime(videoView.getDuration()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                videoView.seekTo(seekBar.getProgress());
                if (!videoView.isPlaying()) {
                    videoView.start();
                    btnPlayPause.setImageResource(R.drawable.ic_pause);
                }
            }
        });
    }

    private String formatTime(int milliseconds) {
        int seconds = (milliseconds / 1000) % 60;
        int minutes = (milliseconds / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(updateProgressRunnable);
        super.onDestroyView();
    }
}
