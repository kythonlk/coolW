package com.kythonlk.coolw

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.*

class NothingClockWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_TICK = "com.kythonlk.coolw.ACTION_TICK"
        const val UPDATE_ALL_WIDGETS = "com.kythonlk.coolw.UPDATE_ALL_WIDGETS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK || intent.action == Intent.ACTION_TIME_TICK ||
            intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == UPDATE_ALL_WIDGETS) {
            
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context.packageName, NothingClockWidget::class.java.name)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        
        val currentTime = timeFormat.format(Date())
        val currentDate = dateFormat.format(Date()).uppercase(Locale.getDefault())

        // Render the time as a dot-matrix bitmap
        val timeBitmap = DotMatrixRenderer.renderText(
            text = currentTime,
            activeColor = Color.WHITE,
            inactiveColor = Color.parseColor("#15FFFFFF"),
            dotRadius = 9f,
            dotSpacing = 24f,
            charSpacing = 16f,
            drawInactive = true
        )

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_nothing_clock)
            views.setImageViewBitmap(R.id.clock_image, timeBitmap)
            views.setTextViewText(R.id.clock_date, currentDate)

            // Click pending intent to open the app
            val appIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.clock_image, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        scheduleNextTick(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNextTick(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelTick(context)
    }

    private fun scheduleNextTick(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NothingClockWidget::class.java).apply {
            action = ACTION_TICK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, calendar.timeInMillis, pendingIntent)
        } catch (e: SecurityException) {
            // Fallback for newer Android versions if exact alarms aren't permitted
            alarmManager.set(AlarmManager.RTC, calendar.timeInMillis, pendingIntent)
        }
    }

    private fun cancelTick(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NothingClockWidget::class.java).apply {
            action = ACTION_TICK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
