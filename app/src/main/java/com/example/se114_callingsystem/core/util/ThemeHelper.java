package com.example.se114_callingsystem.core.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Utility class for managing Light/Dark theme switching.
 * Uses SharedPreferences to persist the user's theme choice.
 */
public class ThemeHelper {

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_DARK_MODE_PREFIX = "dark_mode_";

    private static String getDarkModeKey() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            return KEY_DARK_MODE_PREFIX + user.getUid();
        }
        return KEY_DARK_MODE_PREFIX + "default";
    }

    /**
     * Apply the saved theme preference. Call this in Application.onCreate()
     * or at the start of each Activity before setContentView().
     */
    public static void applyTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isDark = prefs.getBoolean(getDarkModeKey(), false);
        AppCompatDelegate.setDefaultNightMode(
                isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }

    /**
     * Toggle between light and dark mode.
     * Saves preference and applies immediately (triggers activity recreation).
     */
    public static void toggleTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = getDarkModeKey();
        boolean isDark = prefs.getBoolean(key, false);
        boolean newMode = !isDark;
        prefs.edit().putBoolean(key, newMode).apply();
        AppCompatDelegate.setDefaultNightMode(
                newMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }

    /**
     * Set dark mode explicitly.
     */
    public static void setDarkMode(Context context, boolean isDark) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(getDarkModeKey(), isDark).apply();
        AppCompatDelegate.setDefaultNightMode(
                isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }

    /**
     * Check if dark mode is currently enabled.
     */
    public static boolean isDarkMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(getDarkModeKey(), false);
    }
}

