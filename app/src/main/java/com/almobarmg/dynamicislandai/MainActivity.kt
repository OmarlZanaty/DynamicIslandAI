package com.almobarmg.dynamicislandai

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.almobarmg.dynamicislandai.ui.theme.DynamicIslandAiTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("MainActivity created")

        // Request notification access permission
        requestNotificationAccess()

        // Request overlay permission
        requestOverlayPermission()

        // Start the DynamicIslandService
        try {
            val serviceIntent = Intent(this, DynamicIslandService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Timber.d("DynamicIslandService started from MainActivity")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start DynamicIslandService from MainActivity")
        }

        setContent {
            DynamicIslandAiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check notification access on resume
        if (!isNotificationAccessGranted()) {
            Timber.w("Notification access not granted, re-prompting user")
            requestNotificationAccess()
        } else {
            Timber.d("Notification access confirmed granted on resume")
        }

        // Re-check overlay permission on resume
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Timber.w("Overlay permission not granted, re-prompting user")
            requestOverlayPermission()
        } else {
            Timber.d("Overlay permission confirmed granted on resume")
        }
    }

    private fun requestNotificationAccess() {
        try {
            // Check if notification access is already granted
            val notificationListenerSet = Settings.Secure.getString(
                contentResolver,
                "enabled_notification_listeners"
            )
            val packageName = packageName
            val serviceName = "$packageName/.IslandNotificationService"

            if (notificationListenerSet == null || !notificationListenerSet.contains(serviceName)) {
                // Prompt the user to enable notification access
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivity(intent)
                Timber.d("Prompted user to enable notification access")
            } else {
                Timber.d("Notification access already granted")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to request notification access")
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val notificationListenerSet = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        val packageName = packageName
        val serviceName = "$packageName/.IslandNotificationService"
        val granted = notificationListenerSet?.contains(serviceName) == true
        Timber.d("Notification access granted: $granted")
        return granted
    }

    private fun requestOverlayPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                    Timber.d("Prompted user to enable overlay permission")
                } else {
                    Timber.d("Overlay permission already granted")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to request overlay permission")
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DynamicIslandAiTheme {
        Greeting("Android")
    }
}