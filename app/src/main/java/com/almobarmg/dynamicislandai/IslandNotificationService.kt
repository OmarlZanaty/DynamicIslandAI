package com.almobarmg.dynamicislandai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class IslandNotificationService : NotificationListenerService() {

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var mediaHandler: MediaHandler

    @Inject
    lateinit var systemStats: SystemStats

    override fun onCreate() {
        super.onCreate()
        Timber.i("Notification service created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Timber.i("Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Timber.w("Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            Timber.d("onNotificationPosted called with sbn: $sbn")

            // Log notification details
            Timber.d("Notification package: ${sbn.packageName}, id: ${sbn.id}, tag: ${sbn.tag}")

            // Filter out low-priority notifications
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelImportance = getNotificationChannelImportance(sbn)
                Timber.d("Notification importance: $channelImportance")
                if (channelImportance < NotificationManager.IMPORTANCE_DEFAULT) {
                    Timber.d("Ignoring notification due to low importance: $channelImportance")
                    return
                }
            } else {
                @Suppress("DEPRECATION")
                val priority = sbn.notification.priority
                Timber.d("Notification priority: $priority")
                if (priority < Notification.PRIORITY_DEFAULT) {
                    Timber.d("Ignoring notification due to low priority: $priority")
                    return
                }
            }

            // Extract notification details
            val title = sbn.notification.extras.getString("android.title") ?: "New Notification"
            val text = sbn.notification.extras.getString("android.text") ?: ""
            val packageName = sbn.packageName
            Timber.i("Notification received: $packageName - $title - $text")

            // Send a broadcast to DynamicIslandService to handle the notification
            val intent = Intent("com.almobarmg.dynamicislandai.NOTIFICATION_RECEIVED").apply {
                putExtra("notification_title", title)
                putExtra("notification_text", text)
                putExtra("package_name", packageName)
                putExtra("is_media_notification", isMediaNotification(sbn))
            }
            sendBroadcast(intent)
            Timber.d("Broadcast sent for notification: $title")
        } catch (e: Exception) {
            Timber.e(e, "Failed to process notification in IslandNotificationService")
        }
    }

    private fun getNotificationChannelImportance(sbn: StatusBarNotification): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = sbn.notification.channelId
                val channel = notificationManager.getNotificationChannel(channelId)
                val importance = channel?.importance ?: NotificationManager.IMPORTANCE_DEFAULT
                Timber.d("Notification channel importance: $importance for channelId: $channelId")
                return importance
            }
            return NotificationManager.IMPORTANCE_DEFAULT
        } catch (e: Exception) {
            Timber.e(e, "Failed to get notification channel importance")
            return NotificationManager.IMPORTANCE_DEFAULT
        }
    }

    private fun isMediaNotification(sbn: StatusBarNotification): Boolean {
        try {
            val isMedia = sbn.notification.flags and Notification.FLAG_ONGOING_EVENT != 0 ||
                    sbn.notification.category == Notification.CATEGORY_TRANSPORT
            Timber.d("Is media notification: $isMedia")
            return isMedia
        } catch (e: Exception) {
            Timber.e(e, "Failed to determine if notification is media notification")
            return false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("Notification service destroyed")
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