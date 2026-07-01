package com.kythonlk.coolw

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class StepCounterService : Service(), SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var stepOffset = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        registerStepSensor()
        syncSteps()
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        scope.cancel()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        if (!hasActivityRecognitionPermission()) return

        val totalSteps = event.values[0].toInt()
        val prefs = CoolWPrefs.prefs(this)
        ensureDailyReset(prefs)

        if (stepOffset == -1) {
            stepOffset = prefs.getInt(CoolWPrefs.SENSOR_STEP_OFFSET, -1)
            if (stepOffset == -1 || stepOffset > totalSteps) {
                stepOffset = totalSteps
                prefs.edit().putInt(CoolWPrefs.SENSOR_STEP_OFFSET, stepOffset).apply()
            }
        }

        val sensorSteps = (totalSteps - stepOffset).coerceAtLeast(0)
        val currentSource = prefs.getString(CoolWPrefs.STEPS_SOURCE, CoolWPrefs.SOURCE_NONE)

        if (currentSource != CoolWPrefs.SOURCE_HEALTH_CONNECT) {
            saveSteps(sensorSteps, CoolWPrefs.SOURCE_SENSOR)
        } else {
            scope.launch {
                val healthSteps = HealthConnectHelper.readTodaySteps(this@StepCounterService)
                if (healthSteps == null) {
                    saveSteps(sensorSteps, CoolWPrefs.SOURCE_SENSOR)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun syncSteps() {
        scope.launch {
            val healthSteps = HealthConnectHelper.readTodaySteps(this@StepCounterService)
            if (healthSteps != null) {
                saveSteps(healthSteps, CoolWPrefs.SOURCE_HEALTH_CONNECT)
                return@launch
            }

            if (stepSensor == null) {
                saveSteps(0, CoolWPrefs.SOURCE_NONE)
                return@launch
            }

            if (!hasActivityRecognitionPermission()) {
                saveSteps(CoolWPrefs.prefs(this@StepCounterService).getInt(CoolWPrefs.STEPS_TODAY, 0), CoolWPrefs.SOURCE_NONE)
            }
        }
    }

    private fun saveSteps(steps: Int, source: String) {
        val prefs = CoolWPrefs.prefs(this)
        ensureDailyReset(prefs)

        val previous = prefs.getInt(CoolWPrefs.STEPS_TODAY, -1)
        val previousSource = prefs.getString(CoolWPrefs.STEPS_SOURCE, CoolWPrefs.SOURCE_NONE)

        if (previous == steps && previousSource == source) return

        prefs.edit()
            .putInt(CoolWPrefs.STEPS_TODAY, steps)
            .putString(CoolWPrefs.STEPS_SOURCE, source)
            .apply()

        CoolWPrefs.notifyStepsUpdate(this)
    }

    private fun ensureDailyReset(prefs: android.content.SharedPreferences) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val lastReset = prefs.getString(CoolWPrefs.LAST_RESET_DATE, null)
        if (lastReset == today) return

        stepOffset = -1
        prefs.edit()
            .putString(CoolWPrefs.LAST_RESET_DATE, today)
            .remove(CoolWPrefs.SENSOR_STEP_OFFSET)
            .apply()
    }

    private fun registerStepSensor() {
        if (!hasActivityRecognitionPermission()) return
        stepSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Step Counter",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CoolW Steps")
            .setContentText("Tracking daily steps")
            .setSmallIcon(R.drawable.ic_headphones)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "step_counter_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
