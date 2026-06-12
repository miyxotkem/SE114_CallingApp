package com.example.se114_callingsystem.core.util;

import android.media.MediaPlayer;
import java.io.IOException;

public class AudioPlayerManager {
    private static MediaPlayer mediaPlayer;
    private static String currentAudioUrl;
    private static AudioPlayerListener currentListener;

    public interface AudioPlayerListener {
        void onStart();
        void onStop();
        void onComplete();
        void onError(String error);
    }

    public static synchronized void play(String url, AudioPlayerListener listener) {
        // Nếu đang phát cùng một url thì stop
        if (mediaPlayer != null && url.equals(currentAudioUrl)) {
            stop();
            return;
        }

        // Nếu đang phát url khác, stop cái cũ trước
        if (mediaPlayer != null) {
            stop();
        }

        currentAudioUrl = url;
        currentListener = listener;
        mediaPlayer = new MediaPlayer();

        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                if (mediaPlayer != null && url.equals(currentAudioUrl)) {
                    mediaPlayer.start();
                    if (currentListener != null) {
                        currentListener.onStart();
                    }
                }
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                if (currentListener != null) {
                    currentListener.onComplete();
                }
                releaseMediaPlayer();
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                if (currentListener != null) {
                    currentListener.onError("Error playing audio: " + what);
                }
                releaseMediaPlayer();
                return true;
            });
        } catch (IOException e) {
            if (currentListener != null) {
                currentListener.onError(e.getMessage());
            }
            releaseMediaPlayer();
        }
    }

    public static synchronized void stop() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ignored) {}
            if (currentListener != null) {
                currentListener.onStop();
            }
            releaseMediaPlayer();
        }
    }

    public static synchronized boolean isPlaying(String url) {
        return mediaPlayer != null && mediaPlayer.isPlaying() && url.equals(currentAudioUrl);
    }

    public static synchronized int getCurrentPosition() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception e) {}
        }
        return 0;
    }

    public static synchronized int getDuration() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getDuration();
            } catch (Exception e) {}
        }
        return 0;
    }

    public static synchronized void seekTo(int msec) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(msec);
            } catch (Exception e) {}
        }
    }

    public static synchronized String getCurrentAudioUrl() {
        return currentAudioUrl;
    }

    public static synchronized void setCurrentListener(AudioPlayerListener listener) {
        currentListener = listener;
    }

    private static void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        currentAudioUrl = null;
        currentListener = null;
    }
}
