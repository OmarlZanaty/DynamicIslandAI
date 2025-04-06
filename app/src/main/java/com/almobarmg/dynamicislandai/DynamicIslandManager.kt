package com.almobarmg.dynamicislandai

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DynamicIslandManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val analytics: FirebaseAnalytics
) {

    private var islandView: View? = null
    private var rootView: ViewGroup? = null
    private val _stackedContent = mutableStateListOf<@Composable () -> Unit>()
    val stackedContent: List<@Composable () -> Unit> get() = _stackedContent
    private val prefs = context.getSharedPreferences("IslandPrefs", Context.MODE_PRIVATE)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var dragStartX: Float = 0f
    private var dragEndX: Float = 0f

    fun initialize(rootView: ViewGroup?) {
        try {
            Timber.d("Initializing Dynamic Island with rootView: $rootView")
            if (rootView == null) {
                Timber.w("Root view is null, cannot initialize Dynamic Island")
                return
            }
            this.rootView = rootView
            if (islandView == null) {
                islandView = createIslandView().apply {
                    visibility = View.GONE
                    elevation = 20f
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    // Set initial position to center
                    val displayMetrics = context.resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    x = (screenWidth - width) / 2f
                    y = getStatusBarHeight().toFloat() + 100f // Match the offset from DynamicIslandService
                    Timber.d("Initial position set: x=$x, y=$y")
                }
                Timber.d("Adding islandView to rootView")
                rootView.addView(islandView)
            } else {
                Timber.d("islandView already exists, skipping creation")
            }
            adjustForCutout(rootView)
            setupGestures(rootView)
            analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
                param(FirebaseAnalytics.Param.SCREEN_NAME, "DynamicIsland_Initialized")
            }
            Timber.i("Dynamic Island initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Dynamic Island")
        }
    }

    fun showContent(content: @Composable () -> Unit) {
        try {
            Timber.d("Adding content to Dynamic Island")
            _stackedContent.add(content)
            // Recreate the island view to adjust its size
            if (rootView != null) {
                islandView?.let { rootView?.removeView(it) }
                islandView = createIslandView().apply {
                    visibility = View.GONE
                    elevation = 20f
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    val displayMetrics = context.resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    x = (screenWidth - width) / 2f
                    y = getStatusBarHeight().toFloat() + 100f
                    Timber.d("New position set after adding content: x=$x, y=$y")
                }
                rootView?.addView(islandView)
                adjustForCutout(rootView!!)
                setupGestures(rootView!!)
            }
            updateIsland()
            expand()
            analytics.logEvent("content_shown") {
                param("content_count", _stackedContent.size.toString())
            }
            Timber.i("Content added to Dynamic Island, stack size: ${_stackedContent.size}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to show content")
        }
    }

    fun updateIsland() {
        try {
            val composeView = islandView?.findViewById<ComposeView>(R.id.compose_host)
            if (composeView == null) {
                Timber.e("ComposeView not found in islandView")
                return
            }

            Timber.d("Setting content on ComposeView")
            composeView.setContent {
                androidx.compose.material3.MaterialTheme(
                    colorScheme = if (prefs.getBoolean("darkMode", true)) {
                        androidx.compose.material3.darkColorScheme()
                    } else {
                        androidx.compose.material3.lightColorScheme()
                    }
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(shapeFromPrefs())
                            .background(Color.Black.copy(alpha = 0.9f))
                            .padding(horizontal = 32.dp, vertical = 16.dp)
                            .border(0.5.dp, Color.White.copy(alpha = 0.1f), shapeFromPrefs())
                            .semantics { contentDescription = "Dynamic Island Content Scroll" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(_stackedContent.size) { index ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .wrapContentWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                _stackedContent[index]()
                            }
                        }
                    }
                }
            }
            islandView?.visibility = View.VISIBLE
            if (!powerManager.isPowerSaveMode) {
                Timber.d("Starting animation for islandView")
                islandView?.animate()
                    ?.scaleX(1f)?.scaleY(1f)?.alpha(1f)
                    ?.setDuration(if (prefs.getBoolean("animations", true)) 300L else 0L)
                    ?.setInterpolator(OvershootInterpolator())
                    ?.start()
            }
            Timber.i("Dynamic Island updated and made visible")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update Dynamic Island")
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun adjustForCutout(rootView: ViewGroup) {
        try {
            Timber.d("Adjusting Dynamic Island position for cutout")
            val windowInsets = (context as? MainActivity)?.window?.decorView?.rootWindowInsets
            val cutout = windowInsets?.displayCutout
            val statusBarHeight = getStatusBarHeight()

            if (cutout != null) {
                val safeInsetTop = cutout.safeInsetTop
                islandView?.y = safeInsetTop.toFloat() + statusBarHeight.toFloat() + 100f
                islandView?.x = (rootView.width - (islandView?.width ?: 0)) / 2f
            } else {
                islandView?.y = statusBarHeight.toFloat() + 100f
                islandView?.x = (rootView.width - (islandView?.width ?: 0)) / 2f
            }
            Timber.i("Adjusted Dynamic Island position: x=${islandView?.x}, y=${islandView?.y}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to adjust for cutout, using fallback position")
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            islandView?.x = (screenWidth - (islandView?.width ?: 0)) / 2f
            islandView?.y = getStatusBarHeight().toFloat() + 100f
        }
    }

    private fun getStatusBarHeight(): Int {
        var statusBarHeight = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        }
        Timber.d("Calculated status bar height: $statusBarHeight")
        return statusBarHeight
    }

    private fun setupGestures(rootView: ViewGroup) {
        Timber.d("Setting up gestures for islandView")
        islandView?.setOnTouchListener { v, event ->
            try {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartX = event.rawX
                        Timber.d("Gesture: ACTION_DOWN at rawX=${event.rawX}")
                    }
                    MotionEvent.ACTION_UP -> {
                        dragEndX = event.rawX
                        val dragAmount = dragEndX - dragStartX

                        Timber.d("Gesture: ACTION_UP, dragAmount=$dragAmount")
                        if (abs(event.rawX - v.x) < 10 && abs(event.rawY - v.y) < 10) {
                            expand()
                        } else if (dragAmount > 50) {
                            switchContent()
                        } else if (dragAmount < -50) {
                            toggleSettings()
                        } else if (v.y - event.rawY > 50) {
                            dismiss()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Gesture handling failed")
            }
            true
        }
    }

    private fun expand() {
        try {
            Timber.d("Expanding Dynamic Island")
            islandView?.animate()
                ?.scaleX(1.8f)?.scaleY(1.8f)
                ?.setDuration(300L)
                ?.setInterpolator(OvershootInterpolator())
                ?.start()
            analytics.logEvent("island_expanded") {}
            Timber.i("Dynamic Island expanded")
        } catch (e: Exception) {
            Timber.e(e, "Failed to expand Dynamic Island")
        }
    }

    fun dismiss() {
        try {
            Timber.d("Dismissing Dynamic Island")
            _stackedContent.clear()
            islandView?.visibility = View.GONE
            analytics.logEvent("island_dismissed") {}
            Timber.i("Dynamic Island dismissed")
        } catch (e: Exception) {
            Timber.e(e, "Failed to dismiss Dynamic Island")
        }
    }

    private fun switchContent() {
        try {
            Timber.d("Switching content in Dynamic Island")
            if (_stackedContent.isNotEmpty()) {
                val rotatedList = _stackedContent.toMutableList().apply {
                    add(0, removeAt(size - 1))
                }
                _stackedContent.clear()
                _stackedContent.addAll(rotatedList)
                updateIsland()
                analytics.logEvent("content_switched") {}
                Timber.i("Dynamic Island content switched")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to switch content in Dynamic Island")
        }
    }

    private fun toggleSettings() {
        try {
            Timber.d("Opening SettingsActivity from Dynamic Island")
            val intent = Intent(context, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            analytics.logEvent("settings_opened") {}
            Timber.i("Settings activity opened")
        } catch (e: Exception) {
            Timber.e(e, "Failed to open SettingsActivity")
        }
    }

    private fun shapeFromPrefs() = when (prefs.getString("shape", "pill")) {
        "circle" -> CircleShape
        "square" -> RoundedCornerShape(8.dp)
        else -> RoundedCornerShape(20.dp)
    }

    private fun createIslandView(): View {
        try {
            Timber.d("Creating ComposeView for Dynamic Island")
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            // Adjust width based on content
            val baseWidth = (screenWidth * 0.5).toInt()
            val additionalWidthPerItem = 50 // Add 50 pixels per additional item
            val islandWidth = baseWidth + (_stackedContent.size - 1) * additionalWidthPerItem
            // Adjust height based on content
            val baseHeight = 100
            val additionalHeightPerItem = 20 // Add 20 pixels per additional item
            val islandHeight = baseHeight + (_stackedContent.size - 1) * additionalHeightPerItem

            return ComposeView(context).apply {
                id = R.id.compose_host
                layoutParams = ViewGroup.LayoutParams(islandWidth, islandHeight)
                x = (screenWidth - islandWidth) / 2f
                y = getStatusBarHeight().toFloat() + 100f
                Timber.d("ComposeView created with width=$islandWidth, height=$islandHeight")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create island view, using default dimensions")
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val defaultWidth = (screenWidth * 0.5).toInt()
            val defaultHeight = 100
            return ComposeView(context).apply {
                id = R.id.compose_host
                layoutParams = ViewGroup.LayoutParams(defaultWidth, defaultHeight)
                x = (screenWidth - defaultWidth) / 2f
                y = getStatusBarHeight().toFloat() + 100f
                Timber.d("ComposeView created with default width=$defaultWidth, height=$defaultHeight")
            }
        }
    }

    fun cleanup() {
        try {
            Timber.d("Cleaning up DynamicIslandManager")
            islandView?.let { rootView?.removeView(it) }
            islandView = null
            rootView = null
            _stackedContent.clear()
            Timber.i("DynamicIslandManager cleaned up")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clean up DynamicIslandManager")
        }
    }
}