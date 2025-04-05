package com.almobarmg.dynamicislandai

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.qualifiers.ActivityContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdManager @Inject constructor(
    private val context: Context
) {
    private var adEnabled = true
    private var adPosition = Gravity.BOTTOM
    private var adView: AdView? = null

    companion object {
        fun init(context: Context) {
            MobileAds.initialize(context)
        }
    }

    fun setAdEnabled(enabled: Boolean) {
        adEnabled = enabled
        if (!enabled) {
            adView?.destroy()
            adView = null
        }
    }

    fun setAdPosition(position: Int) {
        adPosition = position
    }

    fun showBanner(activity: Activity, force: Boolean = false) {
        if (!adEnabled && !force) return

        // Remove existing ad if any
        adView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }

        adView = AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = "ca-app-pub-1848530383668104/5342973352" // Test ad unit ID
            loadAd(AdRequest.Builder().build())
        }

        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = adPosition
        }

        rootView.addView(adView, layoutParams)
    }

    fun destroy() {
        adView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        adView = null
    }
}