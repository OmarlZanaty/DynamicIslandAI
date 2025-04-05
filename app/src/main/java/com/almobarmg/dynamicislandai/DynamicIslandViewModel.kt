package com.almobarmg.dynamicislandai

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs

enum class IslandVisibility {
    VISIBLE, INVISIBLE, GONE
}

@HiltViewModel
class DynamicIslandViewModel @Inject constructor(
    private val context: Context,
    private val analytics: FirebaseAnalytics // Inject Firebase Analytics
) : ViewModel() {

    // Nullable property to hold the island view
    private var islandView: View? = null
    private var rootView: ViewGroup? = null

    // Mutable state list for stacked content, managed by ViewModel
    private val _stackedContent = mutableStateListOf<@Composable () -> Unit>()
    val stackedContent: List<@Composable () -> Unit> get() = _stackedContent

    // Shared preferences for user settings
    private val prefs = context.getSharedPreferences("IslandPrefs", Context.MODE_PRIVATE)

    // Power manager for battery optimization
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    // Variables to track drag gestures
    private var dragStartX: Float = 0f
    private var dragEndX: Float = 0f

    // Initialize the island and log the event
    fun initialize(rootView: ViewGroup) {
        try {
            this.rootView = rootView
            // Create the island view if not already created
            if (islandView == null) {
                islandView = createIslandView().apply {
                    visibility = View.GONE
                    elevation = 20f
                    // Fix: Use setLayerType instead of reassigning layerType
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                }
                // Add the island view to the rootView
                rootView.addView(islandView)
            }
            adjustForCutout(rootView)
            setupGestures(rootView)
            (context as? MainActivity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
                param(FirebaseAnalytics.Param.SCREEN_NAME, "DynamicIsland_Initialized")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Dynamic Island")
        }
    }

    // Show new content in the island and log the action
    fun showContent(content: @Composable () -> Unit) {
        viewModelScope.launch {
            try {
                _stackedContent.add(content)
                updateIsland()
                analytics.logEvent("content_shown") {
                    param("content_count", _stackedContent.size.toString())
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to show content")
            }
        }
    }

    // Update the island UI with animations
    fun updateIsland() {
        if (!powerManager.isPowerSaveMode) {
            islandView?.findViewById<ComposeView>(R.id.compose_host)?.setContent {
                // Use MaterialTheme with a simple color scheme
                androidx.compose.material3.MaterialTheme(
                    colorScheme = if (prefs.getBoolean("darkMode", true)) {
                        androidx.compose.material3.darkColorScheme()
                    } else {
                        androidx.compose.material3.lightColorScheme()
                    }
                ) {
                    LazyRow(
                        modifier = Modifier
                            .clip(shapeFromPrefs())
                            .background(Color.Black.copy(alpha = 0.8f))
                            .semantics { contentDescription = "Dynamic Island Content Scroll" }
                    ) {
                        items(_stackedContent.size) { index ->
                            Box(Modifier.padding(8.dp)) { _stackedContent[index]() }
                        }
                    }
                }
            } ?: run {
                islandView?.visibility = View.VISIBLE
            }
            islandView?.animate()
                ?.scaleX(1f)?.scaleY(1f)?.alpha(1f)
                ?.setDuration(if (prefs.getBoolean("animations", true)) 300L else 0L)
                ?.setInterpolator(OvershootInterpolator())
                ?.start()
        } else {
            islandView?.visibility = View.VISIBLE
        }
    }

    // Adjust island position based on screen cutout
    @RequiresApi(Build.VERSION_CODES.P)
    private fun adjustForCutout(rootView: ViewGroup) {
        try {
            val cutout = (context as? MainActivity)?.window?.decorView?.rootWindowInsets?.displayCutout
            cutout?.let {
                islandView?.y = it.safeInsetTop.toFloat() + 10f
                islandView?.x = (rootView.width - (islandView?.width ?: 0)) / 2f
            } ?: run {
                islandView?.x = (rootView.width - (islandView?.width ?: 0)) / 2f
                islandView?.y = 10f
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to adjust for cutout")
            islandView?.x = (rootView.width - (islandView?.width ?: 0)) / 2f
            islandView?.y = 10f
        }
    }

    // Set up gesture handling for user interaction
    private fun setupGestures(rootView: ViewGroup) {
        islandView?.setOnTouchListener { v, event ->
            try {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Record the starting position of the drag
                        dragStartX = event.rawX
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Update the position of the island view while dragging
                        v.x = event.rawX - v.width / 2
                        v.y = event.rawY - v.height / 2
                    }
                    MotionEvent.ACTION_UP -> {
                        // Record the ending position of the drag
                        dragEndX = event.rawX
                        val dragAmount = dragEndX - dragStartX

                        // Handle gestures based on drag distance and position
                        if (abs(event.rawX - v.x) < 10 && abs(event.rawY - v.y) < 10) {
                            expand() // Tap to expand
                        } else if (dragAmount > 50) {
                            switchContent() // Swipe right to switch content
                        } else if (dragAmount < -50) {
                            toggleSettings() // Swipe left to open settings
                        } else if (v.y - event.rawY > 50) {
                            dismiss() // Swipe down to dismiss
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Gesture handling failed")
            }
            true
        }
    }

    // Expand the island with animation
    private fun expand() {
        islandView?.animate()
            ?.scaleX(1.5f)?.scaleY(1.5f)
            ?.setDuration(300L)
            ?.setInterpolator(OvershootInterpolator())
            ?.withEndAction { islandView?.scaleX = 1f; islandView?.scaleY = 1f }
            ?.start()
        analytics.logEvent("island_expanded") {}
    }

    // Dismiss the island and clear content
    fun dismiss() {
        _stackedContent.clear()
        islandView?.visibility = View.GONE
        analytics.logEvent("island_dismissed") {}
    }

    // Switch content in the island
    private fun switchContent() {
        if (_stackedContent.isNotEmpty()) {
            val rotatedList = _stackedContent.toMutableList().apply {
                add(0, removeAt(size - 1))
            }
            _stackedContent.clear()
            _stackedContent.addAll(rotatedList)
            updateIsland()
            analytics.logEvent("content_switched") {}
        }
    }

    // Open settings activity
    private fun toggleSettings() {
        val intent = Intent(context, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Required since we're starting an activity from a non-activity context
        }
        context.startActivity(intent)
        analytics.logEvent("settings_opened") {}
    }

    // Determine shape from user preferences
    private fun shapeFromPrefs() = when (prefs.getString("shape", "pill")) {
        "circle" -> CircleShape
        "square" -> RoundedCornerShape(8.dp)
        else -> RoundedCornerShape(20.dp)
    }

    // Create the island view using ComposeView
    private fun createIslandView(): View {
        return ComposeView(context).apply {
            id = R.id.compose_host // Set the ID to match the expected ID in updateIsland
        }
    }

    // Cleanup on ViewModel destruction
    override fun onCleared() {
        (context as? MainActivity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        islandView?.let { rootView?.removeView(it) }
        islandView = null
        rootView = null
        Timber.i("DynamicIslandViewModel cleared")
        super.onCleared()
    }
}