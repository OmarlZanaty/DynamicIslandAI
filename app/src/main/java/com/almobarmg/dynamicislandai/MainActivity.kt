package com.almobarmg.dynamicislandai

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almobarmg.dynamicislandai.ui.theme.DynamicIslandAiTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import androidx.compose.ui.graphics.Color

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var adManager: AdManager

    @Inject
    lateinit var dynamicIslandManager: DynamicIslandManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (isNotificationListenerEnabled()) {
            Timber.i("Notification listener permission granted")
            startDynamicIslandService()
        } else {
            Timber.w("Notification listener permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("IslandPrefs", MODE_PRIVATE)
        val isOnboardingComplete = prefs.getBoolean("onboarding_complete", false)

        setContent {
            DynamicIslandAiTheme {
                MaterialTheme {
                    if (!isOnboardingComplete) {
                        OnboardingScreen {
                            prefs.edit().putBoolean("onboarding_complete", true).apply()
                            setContent { MainContent() }
                        }
                    } else {
                        MainContent()
                    }
                }
            }
        }

        // Load ads asynchronously
        CoroutineScope(Dispatchers.Main).launch {
            try {
                adManager.showBanner(this@MainActivity, false)
                Timber.i("Ad banner shown successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to show ad banner")
            }
        }

        // Start the DynamicIslandService if notification permissions are granted
        if (isNotificationListenerEnabled()) {
            startDynamicIslandService()
        }
    }

    @Composable
    fun MainContent() {
        var islandVisible by remember { mutableStateOf(false) }

        Scaffold { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = {
                    Timber.d("Show Dynamic Island button clicked")

                    // Check notification listener permission
                    if (!isNotificationListenerEnabled()) {
                        Timber.w("Notification listener permission not granted")
                        promptForNotificationListenerPermission()
                        return@Button
                    }

                    // Check API level for Dynamic Island support
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                        Timber.w("Dynamic Island is not supported on API ${Build.VERSION.SDK_INT}")
                        return@Button
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            if (!islandVisible) {
                                Timber.d("Attempting to initialize Dynamic Island")
                                withContext(Dispatchers.Main) {
                                    dynamicIslandManager.initialize(findViewById(android.R.id.content))
                                }
                                Timber.d("Attempting to show content in Dynamic Island")
                                dynamicIslandManager.showContent {
                                    Text(
                                        text = "Hello from Island!",
                                        style = TextStyle(
                                            fontSize = 20.sp,
                                            color = Color.White
                                        )
                                    )
                                }
                                islandVisible = true
                                Timber.d("Dynamic Island should now be visible")
                                Toast.makeText(
                                    this@MainActivity,
                                    "Dynamic Island shown!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Timber.d("Attempting to dismiss Dynamic Island")
                                dynamicIslandManager.dismiss()
                                islandVisible = false
                                Timber.d("Dynamic Island should now be dismissed")
                                Toast.makeText(
                                    this@MainActivity,
                                    "Dynamic Island dismissed!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to toggle Dynamic Island")
                            islandVisible = false
                            Toast.makeText(
                                this@MainActivity,
                                "Failed to toggle Dynamic Island: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }) {
                    Text(if (islandVisible) "Hide Dynamic Island" else "Show Dynamic Island")
                }
            }
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val componentName = ComponentName(this, IslandNotificationService::class.java)
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val isEnabled = enabledListeners?.contains(componentName.flattenToString()) == true
        Timber.d("Notification listener enabled: $isEnabled")
        return isEnabled
    }

    private fun promptForNotificationListenerPermission() {
        Timber.d("Prompting for notification listener permission")
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        notificationPermissionLauncher.launch(intent)
    }

    private fun startDynamicIslandService() {
        Timber.d("Starting DynamicIslandService")
        val serviceIntent = Intent(this, DynamicIslandService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            adManager.destroy()
            // Do not clean up DynamicIslandManager here; let the service handle it
            Timber.i("MainActivity destroyed")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clean up resources in onDestroy")
        }
    }
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = onComplete) {
            Text("Complete Onboarding")
        }
    }
}