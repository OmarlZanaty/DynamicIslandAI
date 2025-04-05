package com.almobarmg.dynamicislandai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    lateinit var dynamicIslandManager: DynamicIslandManager

    override fun onCreate() {
        super.onCreate()
        Timber.i("Notification service created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelImportance = getNotificationChannelImportance(sbn)
            if (channelImportance < NotificationManager.IMPORTANCE_DEFAULT) return
        } else {
            @Suppress("DEPRECATION")
            if (sbn.notification.priority < Notification.PRIORITY_DEFAULT) return
        }

        val title = sbn.notification.extras.getString("android.title") ?: "New Notification"

        Handler(Looper.getMainLooper()).post {
            try {
                dynamicIslandManager.showContent {
                    Column(
                        modifier = Modifier.semantics { contentDescription = "Notification: $title" }
                    ) {
                        Text(title, color = Color.White)
                        if (isMediaNotification(sbn)) {
                           // MediaHandler(context).MediaControls()
                        } else {
                            SystemStats(context).StatsDisplay()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to post notification")
            }
        }
    }

    private fun getNotificationChannelImportance(sbn: StatusBarNotification): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = sbn.notification.channelId
            val channel = notificationManager.getNotificationChannel(channelId)
            return channel?.importance ?: NotificationManager.IMPORTANCE_DEFAULT
        }
        return NotificationManager.IMPORTANCE_DEFAULT
    }

    private fun isMediaNotification(sbn: StatusBarNotification): Boolean {
        return sbn.notification.flags and Notification.FLAG_ONGOING_EVENT != 0 ||
                sbn.notification.category == Notification.CATEGORY_TRANSPORT
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("Notification service destroyed")
    }
}