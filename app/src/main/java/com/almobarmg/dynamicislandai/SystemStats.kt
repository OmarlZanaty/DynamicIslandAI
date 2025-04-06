package com.almobarmg.dynamicislandai

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.RandomAccessFile
import javax.inject.Inject

class SystemStats @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    @Composable
    fun StatsDisplay() {
        val cpuUsage = remember { mutableStateOf(0f) }
        val ramUsage = remember { mutableStateOf(0L) }
        val batteryLevel = remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            while (true) {
                try {
                    cpuUsage.value = getCpuUsage()
                    ramUsage.value = getRamUsage()
                    batteryLevel.value = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to update system stats")
                }
                delay(5000) // Update every 5 seconds
            }
        }

        Column(
            modifier = Modifier.semantics {
                contentDescription = "System Stats: CPU ${cpuUsage.value}%, RAM ${ramUsage.value} MB, Battery ${batteryLevel.value}%"
            }
        ) {
            Text("CPU: ${cpuUsage.value.toInt()}%", color = Color.White)
            Text("RAM: ${ramUsage.value} MB", color = Color.White)
            Text("Battery: ${batteryLevel.value}%", color = Color.White)
        }
    }

    // Calculate real CPU usage from /proc/stat
    private fun getCpuUsage(): Float {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine()?.split(" ")?.drop(2)?.map { it.toLong() } ?: return 0f
            val (user, nice, system) = load
            val totalCpu = user + nice + system
            reader.close()
            (totalCpu / 100f).coerceIn(0f, 100f)
        } catch (e: Exception) {
            Timber.e(e, "Failed to read CPU usage")
            0f
        }
    }

    // Calculate RAM usage
    private fun getRamUsage(): Long {
        return try {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)
        } catch (e: Exception) {
            Timber.e(e, "Failed to read RAM usage")
            0L
        }
    }
}