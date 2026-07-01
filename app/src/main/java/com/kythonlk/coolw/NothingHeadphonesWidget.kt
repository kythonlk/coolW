package com.kythonlk.coolw

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class NothingHeadphonesWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_HEADPHONE_UPDATE = "com.kythonlk.coolw.ACTION_HEADPHONE_UPDATE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_HEADPHONE_UPDATE || intent.action == "com.kythonlk.coolw.UPDATE_ALL_WIDGETS") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context.packageName, NothingHeadphonesWidget::class.java.name)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        BluetoothHeadphoneService.start(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = CoolWPrefs.prefs(context)
        val deviceName = prefs.getString(CoolWPrefs.BT_DEVICE_NAME, "NOT CONNECTED") ?: "NOT CONNECTED"
        val battery = prefs.getInt(CoolWPrefs.BT_BATTERY, -1)
        val batteryLeft = prefs.getInt(CoolWPrefs.BT_BATTERY_LEFT, -1)
        val batteryRight = prefs.getInt(CoolWPrefs.BT_BATTERY_RIGHT, -1)
        val mode = prefs.getString(CoolWPrefs.BT_MODE, "—") ?: "—"

        val batteryLabel = formatBatteryLabel(battery, batteryLeft, batteryRight)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_nothing_headphones)
            views.setTextViewText(R.id.headphone_mode, mode.uppercase())
            views.setTextViewText(R.id.headphone_battery_text, batteryLabel)
            
            if (batteryLeft >= 0 && batteryRight >= 0) {
                views.setViewVisibility(R.id.battery_divider, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.battery_divider, View.GONE)
            }

            views.setImageViewResource(R.id.headphone_icon, R.drawable.ic_headphones)

            val appIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 2, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.headphone_icon, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun formatBatteryLabel(main: Int, left: Int, right: Int): String {
        if (main < 0 && left < 0 && right < 0) return "—"
        if (left >= 0 && right >= 0) return "L $left% · R $right%"
        if (main >= 0) return "$main%"
        if (left >= 0) return "L $left%"
        if (right >= 0) return "R $right%"
        return "—"
    }
}
