package com.astrolog.app;

import android.app.Application;

import com.astrolog.app.util.ThemeHelper;

/**
 * Application class – runs once when the app process starts.
 * We apply the saved light/dark preference here so every screen opens in the right theme.
 */
public class AstroLogApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.applySavedTheme(this);
    }
}
