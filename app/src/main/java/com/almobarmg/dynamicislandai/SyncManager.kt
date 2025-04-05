package com.almobarmg.dynamicislandai

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var selectedDevice: BluetoothDevice? = null

    fun setSelectedDevice(device: BluetoothDevice) {
        // Check for BLUETOOTH_CONNECT permission before accessing device properties
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.e("BLUETOOTH_CONNECT permission not granted")
            return
        }

        selectedDevice = device
        Timber.i("Selected device: ${device.name ?: "Unknown Device"} (${device.address})") // Now safe
        connectToDevice(device)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        // Permission check is already done in setSelectedDevice, but we can double-check for safety
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.e("BLUETOOTH_CONNECT permission not granted")
            return
        }

        try {
            Timber.i("Connecting to device: ${device.name ?: "Unknown Device"}") // Now safe
            // Placeholder: Implement actual Bluetooth connection logic here
            // For example, create a BluetoothSocket and connect to the device
            // Example (not functional, just a placeholder):
            // val socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
            // socket.connect()
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to device: ${device.name ?: "Unknown Device"}")
        }
    }

    fun getSelectedDevice(): BluetoothDevice? {
        return selectedDevice
    }

    fun disconnect() {
        // Check permission before accessing device properties
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.e("BLUETOOTH_CONNECT permission not granted")
            selectedDevice = null
            return
        }

        Timber.i("Disconnecting from device: ${selectedDevice?.name ?: "Unknown Device"}")
        selectedDevice = null
    }
}