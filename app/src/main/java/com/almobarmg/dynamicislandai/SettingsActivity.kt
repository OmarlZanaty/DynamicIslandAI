package com.almobarmg.dynamicislandai

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.almobarmg.dynamicislandai.ui.theme.DynamicIslandAiTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Timber.w("BLUETOOTH_CONNECT permission denied")
        }
    }

    @Inject
    lateinit var mediaHandler: MediaHandler

    @Inject
    lateinit var adManager: AdManager

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var dynamicIslandManager: DynamicIslandManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dynamicIslandManager.initialize(findViewById(android.R.id.content))
            } else {
                Timber.w("Dynamic Island is not supported on API ${Build.VERSION.SDK_INT}")
            }

            val notificationListenerEnabled = isNotificationListenerEnabled()
            if (!notificationListenerEnabled) {
                promptForNotificationListenerPermission()
            }

            val prefs = getSharedPreferences("IslandPrefs", MODE_PRIVATE)
            DynamicIslandAiTheme {
                MaterialTheme {
                    Column(Modifier.padding(16.dp)) {
                        SwitchRow("Enable Notifications", prefs, "notifications", true)
                        var mediaEnabled by remember { mutableStateOf(prefs.getBoolean("media", true)) }
                        SwitchRow(
                            label = "Enable Media",
                            prefs = prefs,
                            key = "media",
                            default = true,
                            onCheckedChange = { mediaEnabled = it }
                        )
                        if (mediaEnabled) {
                            mediaHandler.MediaControls()
                        }
                        SwitchRow("Enable Stats", prefs, "stats", true)
                        SwitchRow("Dark Mode", prefs, "darkMode", true) { dynamicIslandManager.updateIsland() }
                        SwitchRow("Animations", prefs, "animations", true) { dynamicIslandManager.updateIsland() }
                        SwitchRow("Ads Enabled", prefs, "adsEnabled", true) {
                            adManager.setAdEnabled(it)
                            // Load ads asynchronously
                            CoroutineScope(Dispatchers.Main).launch {
                                adManager.showBanner(this@SettingsActivity, true)
                            }
                        }
                        AdFrequencySlider(prefs)
                        AdPositionSelector(prefs)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            DeviceSelector(prefs)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaHandler.release()
        adManager.destroy()
        syncManager.disconnect()
        dynamicIslandManager.cleanup()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val componentName = ComponentName(this, IslandNotificationService::class.java)
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return enabledListeners?.contains(componentName.flattenToString()) == true
    }

    private fun promptForNotificationListenerPermission() {
        AlertDialog.Builder(this)
            .setTitle("Enable Notification Access")
            .setMessage("To display notifications in the Dynamic Island, please enable notification access for this app.")
            .setPositiveButton("Go to Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    @Composable
    private fun SwitchRow(
        label: String,
        prefs: SharedPreferences,
        key: String,
        default: Boolean,
        onCheckedChange: (Boolean) -> Unit = {}
    ) {
        var checked by remember { mutableStateOf(prefs.getBoolean(key, default)) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .semantics { contentDescription = "$label Switch" }
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = checked,
                onCheckedChange = { newValue ->
                    checked = newValue
                    prefs.edit().putBoolean(key, newValue).apply()
                    onCheckedChange(newValue)
                }
            )
        }
        if (checked) {
            onCheckedChange(checked)
        }
    }

    @Composable
    private fun AdFrequencySlider(prefs: SharedPreferences) {
        var frequency by remember { mutableStateOf(prefs.getInt("adFrequency", 10).toFloat()) }
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .semantics { contentDescription = "Ad Frequency Slider" }
        ) {
            Text(
                text = "Ad Frequency (notifications): ${frequency.toInt()}",
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = frequency,
                onValueChange = {
                    frequency = it
                    prefs.edit().putInt("adFrequency", it.toInt()).apply()
                },
                valueRange = 5f..20f,
                steps = 15,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    @Composable
    private fun AdPositionSelector(prefs: SharedPreferences) {
        var position by remember { mutableStateOf(prefs.getInt("adPosition", Gravity.BOTTOM)) }
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .semantics { contentDescription = "Ad Position Selector" }
        ) {
            Text(
                text = "Ad Position",
                style = MaterialTheme.typography.bodyLarge
            )
            Row(
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = position == Gravity.TOP,
                        onClick = {
                            position = Gravity.TOP
                            prefs.edit().putInt("adPosition", Gravity.TOP).apply()
                            adManager.setAdPosition(Gravity.TOP)
                        }
                    )
                    Text("Top")
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = position == Gravity.BOTTOM,
                        onClick = {
                            position = Gravity.BOTTOM
                            prefs.edit().putInt("adPosition", Gravity.BOTTOM).apply()
                            adManager.setAdPosition(Gravity.BOTTOM)
                        }
                    )
                    Text("Bottom")
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Composable
    private fun DeviceSelector(prefs: SharedPreferences) {
        val context = LocalContext.current
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter
        val devices = remember { mutableStateOf(bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()) }
        var selectedDevice by remember { mutableStateOf(prefs.getString("selectedDevice", null)) }

        val hasBluetoothPermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasBluetoothPermission) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

        Column(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .semantics { contentDescription = "Device Selector" }
        ) {
            Text(
                text = "Select Sync Device",
                style = MaterialTheme.typography.bodyLarge
            )

            if (!hasBluetoothPermission) {
                Text(
                    text = "Bluetooth permission required",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else if (devices.value.isEmpty()) {
                Text(
                    text = "No Bluetooth devices found",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                devices.value.forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedDevice == device.address,
                            onClick = {
                                selectedDevice = device.address
                                prefs.edit().putString("selectedDevice", device.address).apply()
                                syncManager.setSelectedDevice(device)
                            }
                        )
                        Text(
                            text = device.name ?: "Unknown Device",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}