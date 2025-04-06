package com.almobarmg.dynamicislandai

import android.app.Application
import androidx.multidex.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class DynamicIslandAIApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.d("DynamicIslandAIApplication created")
    }
}