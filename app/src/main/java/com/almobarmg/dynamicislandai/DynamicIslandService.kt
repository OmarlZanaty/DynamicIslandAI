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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class DynamicIslandService : Service() {

    @Inject
    lateinit var dynamicIslandManager: DynamicIslandManager

    @Inject
    lateinit var mediaHandler: MediaHandler

    @Inject
    lateinit var systemStats: SystemStats

    private val notificationReceiver = NotificationReceiver()
    private var windowManager: WindowManager? = null
    private var overlayView: ViewGroup? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("DynamicIslandService created")

        try {
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

            // Initialize DynamicIslandManager with the overlay view
            if (overlayView != null) {
                dynamicIslandManager.initialize(overlayView)
            } else {
                Timber.e("Overlay view is null, cannot initialize DynamicIslandManager")
                stopSelf()
                return
            }

            // Add test content to confirm the Dynamic Island is working
            dynamicIslandManager.showContent {
                Column(
                    modifier = Modifier
                        .semantics { contentDescription = "Test Content" }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Test Dynamic Island",
                        style = TextStyle(fontSize = 20.sp, color = Color.White),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "This is a test message",
                        style = TextStyle(fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f)),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            Timber.d("Added test content to Dynamic Island")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize DynamicIslandService")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("DynamicIslandService started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(notificationReceiver)
            dynamicIslandManager.cleanup()
            windowManager?.removeView(overlayView)
            Timber.d("DynamicIslandService destroyed")
        } catch (e: Exception) {
            Timber.e(e, "Failed to destroy DynamicIslandService")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startForegroundService() {
        try {
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
                Timber.d("Notification channel created for foreground service: $channelId")
            }

            val notificationBuilder = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Dynamic Island Service")
                .setContentText("Running in the background to manage Dynamic Island")
                .setSmallIcon(android.R.drawable.ic_dialog_info)

            // Set the foreground service type explicitly for Android 14+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                notificationBuilder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }

            val notification = notificationBuilder.build()
            Timber.d("Foreground notification built")

            startForeground(1, notification)
            Timber.d("Foreground service started successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start foreground service")
            stopSelf()
        }
    }

    private fun setupOverlayWindow() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            overlayView = ViewGroup.inflate(this, android.R.layout.simple_list_item_1, null) as ViewGroup

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT
            )

            val statusBarHeight = getStatusBarHeight()
            params.y = statusBarHeight + 150 // Increased offset to ensure visibility
            windowManager?.addView(overlayView, params)
            Timber.d("Overlay window set up with y position: ${params.y}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set up overlay window")
            stopSelf()
        }
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
            try {
                Timber.d("Received notification broadcast with intent: $intent")
                if (intent?.action != "com.almobarmg.dynamicislandai.NOTIFICATION_RECEIVED") {
                    Timber.w("Received broadcast with unexpected action: ${intent?.action}")
                    return
                }

                val title = intent.getStringExtra("notification_title") ?: "New Notification"
                val text = intent.getStringExtra("notification_text") ?: ""
                val packageName = intent.getStringExtra("package_name") ?: "Unknown"
                val isMediaNotification = intent.getBooleanExtra("is_media_notification", false)
                Timber.i("Processing notification: $packageName - $title - $text (isMedia: $isMediaNotification)")

                // Show the notification in the Dynamic Island
                dynamicIslandManager.showContent {
                    NotificationContent(
                        title = title,
                        text = text,
                        packageName = packageName,
                        isMediaNotification = isMediaNotification
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to process notification broadcast")
            }
        }
    }

    @Composable
    fun NotificationContent(
        title: String,
        text: String,
        packageName: String,
        isMediaNotification: Boolean
    ) {
        Column(
            modifier = Modifier
                .semantics { contentDescription = "Notification: $title from $packageName" }
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "$packageName: $title",
                style = TextStyle(fontSize = 20.sp, color = Color.White),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    style = TextStyle(fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f)),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (isMediaNotification) {
                mediaHandler.MediaControls()
            } else {
                systemStats.StatsDisplay()
            }
        }
    }
}