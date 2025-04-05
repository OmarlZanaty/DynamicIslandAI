package com.almobarmg.dynamicislandai

import android.app.Application
import android.webkit.WebView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class DynamicIslandAIApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        clearWebViewCache()
        setupAdMob()
    }

    private fun clearWebViewCache() {
        try {
            WebView(this).clearCache(true)
            Timber.i("WebView cache cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear WebView cache")
        }
    }

    private fun setupAdMob() {
        MobileAds.initialize(this) {
            Timber.i("AdMob initialized")
        }
        val testDeviceIds = listOf("734A560202EBF76584A4EB4C6A11BFD0")
        val configuration = RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        MobileAds.setRequestConfiguration(configuration)
        Timber.i("AdMob configured with test device IDs: $testDeviceIds")
    }
}