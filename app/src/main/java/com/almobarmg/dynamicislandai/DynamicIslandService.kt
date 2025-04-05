package com.almobarmg.dynamicislandai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class DynamicIslandService : Service() {

    @Inject
    lateinit var dynamicIslandManager: DynamicIslandManager

    private val notificationReceiver = NotificationReceiver()
    private var windowManager: WindowManager? = null
    private var overlayView: ViewGroup? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("DynamicIslandService created")

        // Register the broadcast receiver to listen for notifications
        val filter = IntentFilter("com.almobarmg.dynamicislandai.NOTIFICATION_RECEIVED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, filter)
        }
        Timber.d("Broadcast receiver registered")

        // Start as a foreground service
        startForegroundService()

        // Create a system overlay window
        setupOverlayWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("DynamicIslandService started")

        // Try to initialize with the root view from MainActivity
        val rootView = (applicationContext as? MainActivity)?.findViewById<ViewGroup>(android.R.id.content)
        if (rootView != null) {
            dynamicIslandManager.initialize(rootView)
        } else {
            // If root view is not available, use the overlay view
            Timber.w("Root view not available, using system overlay")
            dynamicIslandManager.initialize(overlayView)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(notificationReceiver)
        dynamicIslandManager.cleanup()
        windowManager?.removeView(overlayView)
        Timber.d("DynamicIslandService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startForegroundService() {
        val channelId = "DynamicIslandServiceChannel"
        val channelName = "Dynamic Island Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Dynamic Island Service")
            .setContentText("Running in the background to manage Dynamic Island")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Use a default icon for now
            .build()

        startForeground(1, notification)
    }

    private fun setupOverlayWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = ViewGroup.inflate(this, android.R.layout.simple_list_item_1, null) as ViewGroup

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        // Ensure the overlay respects the status bar
        val statusBarHeight = getStatusBarHeight()
        params.y = statusBarHeight + 60 // Match the padding used in adjustForCutout
        windowManager?.addView(overlayView, params)
    }

    private fun getStatusBarHeight(): Int {
        var statusBarHeight = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = resources.getDimensionPixelSize(resourceId)
        }
        Timber.d("Calculated status bar height: $statusBarHeight")
        return statusBarHeight
    }
    inner class NotificationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Timber.d("Received notification broadcast with intent: $intent")
            val notificationText = intent?.getStringExtra("notification_text") ?: "New Notification"
            val packageName = intent?.getStringExtra("package_name") ?: "Unknown"
            Timber.i("Processing notification: $packageName - $notificationText")

            // Show the notification in the Dynamic Island
            dynamicIslandManager.showContent {
                Text(
                    text = "$packageName: $notificationText",
                    style = TextStyle(
                        fontSize = 20.sp,
                        color = Color.White
                    )
                )
            }
        }
    }
}