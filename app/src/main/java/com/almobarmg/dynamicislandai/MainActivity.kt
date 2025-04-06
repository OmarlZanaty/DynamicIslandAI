package com.almobarmg.dynamicislandai

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.NotificationCompat
import com.almobarmg.dynamicislandai.ui.theme.DynamicIslandAiTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Timber.d("Notification permission granted")
            sendTestNotification()
        } else {
            Timber.w("Notification permission denied")
        }
    }

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
                    Column {
                        Greeting("Android")
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    Timber.d("Requesting POST_NOTIFICATIONS permission")
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    Timber.d("POST_NOTIFICATIONS permission already granted")
                                    sendTestNotification()
                                }
                            } else {
                                sendTestNotification()
                            }
                        }) {
                            Text("Send Test Notification")
                        }
                    }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                Timber.d("Overlay permission granted after returning from settings")
                // Restart DynamicIslandService to ensure the overlay is set up
                try {
                    val serviceIntent = Intent(this, DynamicIslandService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    Timber.d("DynamicIslandService restarted after overlay permission granted")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to restart DynamicIslandService after overlay permission granted")
                }
            } else {
                Timber.w("Overlay permission not granted after returning from settings")
            }
        }
    }

    private fun requestNotificationAccess() {
        try {
            if (!isNotificationAccessGranted()) {
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
        try {
            val notificationListenerSet = Settings.Secure.getString(
                contentResolver,
                "enabled_notification_listeners"
            )
            Timber.d("Raw enabled_notification_listeners: $notificationListenerSet")

            // Use ComponentName to get the correct service name format
            val componentName = ComponentName(this, IslandNotificationService::class.java)
            val flattenedComponentName = componentName.flattenToString()
            Timber.d("Expected service name: $flattenedComponentName")

            val granted = notificationListenerSet?.contains(flattenedComponentName) == true
            Timber.d("Notification access granted: $granted")
            return granted
        } catch (e: Exception) {
            Timber.e(e, "Failed to check notification access, assuming not granted")
            return false
        }
    }

    private fun requestOverlayPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
                    Timber.d("Prompted user to enable overlay permission")
                } else {
                    Timber.d("Overlay permission already granted")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to request overlay permission")
        }
    }

    private fun sendTestNotification() {
        try {
            val channelId = "test_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Test Channel",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                )
                val notificationManager = getSystemService(android.app.NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
                Timber.d("Notification channel created: $channelId")
            }

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Test Notification")
                .setContentText("This is a test notification")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            Timber.d("Notification built: $notification")

            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.notify(1, notification)
            Timber.d("Test notification sent")
        } catch (e: Exception) {
            Timber.e(e, "Failed to send test notification")
        }
    }

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1001
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