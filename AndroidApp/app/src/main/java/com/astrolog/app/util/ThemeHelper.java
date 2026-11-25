package com.astrolog.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Dark mode support (feature f).
 * Observers' eyes adapt to the dark, so a dark theme is easier on night vision.
 * The choice is remembered in SharedPreferences.
 */
public final class ThemeHelper {

    private static final String PREFS = "astrolog_prefs";
    private static final String KEY_DARK = "dark_mode";

    private ThemeHelper() { }

    public static boolean isDarkMode(Context context) {
        // Default to dark: this app is used at night
        return prefs(context).getBoolean(KEY_DARK, true);
    }

    public static void applySavedTheme(Context context) {
        AppCompatDelegate.setDefaultNightMode(isDarkMode(context)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /** Switches theme, saves the choice; AppCompat recreates visible activities automatically. */
    public static void toggle(Context context) {
        prefs(context).edit().putBoolean(KEY_DARK, !isDarkMode(context)).apply();
        applySavedTheme(context);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
