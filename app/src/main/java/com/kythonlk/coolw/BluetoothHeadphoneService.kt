package com.kythonlk.coolw

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.util.Locale

class BluetoothHeadphoneService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var bluetoothAdapter: BluetoothAdapter? = null

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshConnectedDevice()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT) {
                parseVendorEvent(intent)
            }
            when (intent?.action) {
                ACTION_BATTERY_LEVEL_CHANGED,
                BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT,
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothAdapter.ACTION_STATE_CHANGED -> refreshConnectedDevice()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = manager?.adapter
        createNotificationChannel()
        registerBluetoothReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        refreshConnectedDevice()
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshRunnable)
        unregisterReceiver(bluetoothReceiver)
        super.onDestroy()
    }

    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_BATTERY_LEVEL_CHANGED)
            addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bluetoothReceiver, filter)
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshConnectedDevice() {
        if (!hasBluetoothPermission()) {
            saveDisconnected("PERMISSION NEEDED")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            saveDisconnected("BLUETOOTH OFF")
            return
        }

        // To avoid deadlocks and ANR, we query A2DP first, then HEADSET in a chain.
        adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profileId: Int, a2dpProxy: BluetoothProfile?) {
                val a2dpDevices = a2dpProxy?.connectedDevices.orEmpty()
                if (a2dpProxy != null) adapter.closeProfileProxy(profileId, a2dpProxy)
                
                // Now query Headset
                adapter.getProfileProxy(this@BluetoothHeadphoneService, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(pId: Int, headsetProxy: BluetoothProfile?) {
                        val headsetDevices = headsetProxy?.connectedDevices.orEmpty()
                        if (headsetProxy != null) adapter.closeProfileProxy(pId, headsetProxy)
                        
                        val allDevices = (a2dpDevices + headsetDevices).distinctBy { it.address }
                        val selected = selectPreferredDevice(allDevices)

                        if (selected == null) {
                            saveDisconnected("NOT CONNECTED")
                        } else {
                            publishDeviceState(selected)
                        }
                    }

                    override fun onServiceDisconnected(pId: Int) {
                        // If headset fails, still use A2DP info
                        val selected = selectPreferredDevice(a2dpDevices)
                        if (selected == null) saveDisconnected("NOT CONNECTED") else publishDeviceState(selected)
                    }
                }, BluetoothProfile.HEADSET)
            }

            override fun onServiceDisconnected(profileId: Int) {
                saveDisconnected("NOT CONNECTED")
            }
        }, BluetoothProfile.A2DP)
    }

    @SuppressLint("MissingPermission")
    private fun selectPreferredDevice(devices: List<BluetoothDevice>): BluetoothDevice? {
        if (devices.isEmpty()) return null
        return devices.firstOrNull { device ->
            device.name?.contains("wiwu", ignoreCase = true) == true
        } ?: devices.first()
    }

    @SuppressLint("MissingPermission")
    private fun publishDeviceState(device: BluetoothDevice) {
        val name = device.name ?: "Headphones"
        val batteryInfo = readBattery(device)
        
        val prefs = CoolWPrefs.prefs(this)
        val currentMode = prefs.getString(CoolWPrefs.BT_MODE, "—")
        val mode = if (currentMode == "—" || currentMode == "CONNECTED") {
            readModeFromMetadata(device)
        } else {
            currentMode ?: "CONNECTED"
        }

        prefs.edit {
            putBoolean(CoolWPrefs.BT_CONNECTED, true)
            putString(CoolWPrefs.BT_DEVICE_NAME, name)
            putInt(CoolWPrefs.BT_BATTERY, batteryInfo.main)
            putInt(CoolWPrefs.BT_BATTERY_LEFT, batteryInfo.left)
            putInt(CoolWPrefs.BT_BATTERY_RIGHT, batteryInfo.right)
            putString(CoolWPrefs.BT_MODE, mode)
        }

         CoolWPrefs.notifyHeadphonesUpdate(this)
    }

    private fun saveDisconnected(status: String) {
        CoolWPrefs.prefs(this).edit {
            putBoolean(CoolWPrefs.BT_CONNECTED, false)
            putString(CoolWPrefs.BT_DEVICE_NAME, status)
            putInt(CoolWPrefs.BT_BATTERY, -1)
            putInt(CoolWPrefs.BT_BATTERY_LEFT, -1)
            putInt(CoolWPrefs.BT_BATTERY_RIGHT, -1)
            putString(CoolWPrefs.BT_MODE, "—")
        }

        CoolWPrefs.notifyHeadphonesUpdate(this)
    }

    @SuppressLint("MissingPermission")
    private fun readBattery(device: BluetoothDevice): BatteryInfo {
        var main = -1
        var left = -1
        var right = -1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            main = device.getBatteryLevelCompat()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            left = device.getMetadataCompat(METADATA_UNTETHERED_LEFT_BATTERY)?.toBatteryPercent() ?: -1
            right = device.getMetadataCompat(METADATA_UNTETHERED_RIGHT_BATTERY)?.toBatteryPercent() ?: -1
            if (main == BATTERY_LEVEL_UNKNOWN || main < 0) {
                main = device.getMetadataCompat(METADATA_MAIN_BATTERY)?.toBatteryPercent() ?: -1
            }
        }

        if (main < 0 && left >= 0 && right >= 0) {
            main = (left + right) / 2
        } else if (main < 0 && left >= 0) {
            main = left
        } else if (main < 0 && right >= 0) {
            main = right
        }

        return BatteryInfo(main, left, right)
    }

    @SuppressLint("MissingPermission")
    private fun readModeFromMetadata(device: BluetoothDevice): String {
        // Manufacturer specific metadata key for ANC might be 7 for some, but it's non-standard.
        // We mainly rely on parseVendorEvent for Wiwu.
        return "CONNECTED"
    }

    private fun ByteArray.toBatteryPercent(): Int {
        return toMetadataString()?.toIntOrNull()?.coerceIn(0, 100) ?: -1
    }

    private fun ByteArray.toMetadataString(): String? {
        return try {
            String(this, Charsets.UTF_8).trim().ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVendorEvent(intent: Intent) {
        val args = intent.getStringArrayExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS) ?: return
        val joined = args.joinToString(" ").uppercase(Locale.getDefault())

        val mode = when {
            joined.contains("ANC") || joined.contains("NOISE") -> "ANC"
            joined.contains("TRANSP") || joined.contains("AMBIENT") || joined.contains("AWARE") -> "TRANSPARENCY"
            joined.contains("NORMAL") || joined.contains("STANDARD") -> "NORMAL"
            else -> null
        }

        if (mode != null) {
            CoolWPrefs.prefs(this).edit { putString(CoolWPrefs.BT_MODE, mode) }
        }

        val batteryMatch = Regex("(\\d{1,3})").findAll(joined).map { it.value.toIntOrNull() ?: -1 }
            .firstOrNull { it in 0..100 }
        if (batteryMatch != null) {
            CoolWPrefs.prefs(this).edit { putInt(CoolWPrefs.BT_BATTERY, batteryMatch) }
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Headphone Sync",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CoolW Headphones")
            .setContentText("Syncing Bluetooth headphone status")
            .setSmallIcon(R.drawable.ic_headphones)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private data class BatteryInfo(val main: Int, val left: Int, val right: Int)

    companion object {
        private const val CHANNEL_ID = "headphone_sync_channel"
        private const val NOTIFICATION_ID = 1002
        private const val REFRESH_INTERVAL_MS = 30_000L

        private const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val BATTERY_LEVEL_UNKNOWN = -1
        private const val METADATA_MAIN_BATTERY = 6
        private const val METADATA_UNTETHERED_LEFT_BATTERY = 10
        private const val METADATA_UNTETHERED_RIGHT_BATTERY = 11

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }
            val intent = Intent(context, BluetoothHeadphoneService::class.java)
            context.startForegroundService(intent)
        }
    }
}

private fun BluetoothDevice.getBatteryLevelCompat(): Int {
    return try {
        val method = javaClass.getMethod("getBatteryLevel")
        method.invoke(this) as Int
    } catch (_: Exception) {
        -1
    }
}

private fun BluetoothDevice.getMetadataCompat(key: Int): ByteArray? {
    return try {
        val method = javaClass.getMethod("getMetadata", Int::class.javaPrimitiveType)
        method.invoke(this, key) as? ByteArray
    } catch (_: Exception) {
        null
    }
}
