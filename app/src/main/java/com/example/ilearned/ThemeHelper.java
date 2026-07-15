package com.example.ilearned;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * ThemeHelper
 *
 * Saves the student's Light/Dark preference to SharedPreferences
 * and applies it via AppCompatDelegate so it persists across sessions.
 *
 * Call ThemeHelper.applyTheme(context) in:
 *   - MainActivity.onCreate()  BEFORE setContentView()
 *   - Any Activity that can be opened directly
 *
 * Call ThemeHelper.setTheme(context, isDark) when the student
 * taps a theme button in SettingsFragment.
 */
public class ThemeHelper {

    private static final String PREFS_NAME  = "ilearned_prefs";
    private static final String KEY_DARK    = "dark_mode_enabled";

    /**
     * Read saved preference and apply the correct theme.
     * Call this at the very start of every Activity's onCreate,
     * before setContentView().
     */
    public static void applyTheme(Context context) {
        boolean isDark = isDarkMode(context);
        AppCompatDelegate.setDefaultNightMode(
                isDark
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /**
     * Save the theme choice and apply it immediately.
     * The Activity will recreate itself to reflect the change.
     */
    public static void setTheme(Context context, boolean isDark) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_DARK, isDark).apply();
        AppCompatDelegate.setDefaultNightMode(
                isDark
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /**
     * Returns true if dark mode is currently saved.
     */
    public static boolean isDarkMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_DARK, false); // default: light mode
    }
}