package com.kythonlk.coolw

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getIntExtra("todo_id", -1)
        val todoTitle = intent.getStringExtra("todo_title") ?: "Todo Alarm"
        val todoDesc = intent.getStringExtra("todo_desc") ?: "Time's up!"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "coolw_alarms"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Todo Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for scheduled tasks"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Pending Intent to launch the Main App
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            todoId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(todoTitle)
            .setContentText(todoDesc)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSound)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(todoId, notification)

        // Clear the alarm status since it went off
        val dbHelper = TodoDatabaseHelper(context)
        dbHelper.updateAlarmStatus(todoId, false)
        
        // Trigger widget update and app UI update
        val updateIntent = Intent(context, NothingClockWidget::class.java).apply {
            action = "com.kythonlk.coolw.UPDATE_ALL_WIDGETS"
        }
        context.sendBroadcast(updateIntent)
    }
}
