package com.example.ilearned;

import android.app.Application;

public class ILearnedApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Apply saved theme before any Activity starts
        ThemeHelper.applyTheme(this);
    }
}