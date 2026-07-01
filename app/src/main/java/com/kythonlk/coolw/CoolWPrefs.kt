package com.kythonlk.coolw

import android.content.Context
import android.content.Intent

object CoolWPrefs {
    const val NAME = "CoolWPrefs"

    const val STEPS_TODAY = "steps_today"
    const val STEPS_GOAL = "steps_goal"
    const val STEPS_SOURCE = "steps_source"
    const val SENSOR_STEP_OFFSET = "sensor_step_offset"
    const val LAST_RESET_DATE = "last_reset_date"

    const val BT_DEVICE_NAME = "bt_device_name"
    const val BT_BATTERY = "bt_battery"
    const val BT_BATTERY_LEFT = "bt_battery_left"
    const val BT_BATTERY_RIGHT = "bt_battery_right"
    const val BT_MODE = "bt_mode"
    const val BT_CONNECTED = "bt_connected"

    const val SOURCE_HEALTH_CONNECT = "health_connect"
    const val SOURCE_SENSOR = "device_sensor"
    const val SOURCE_NONE = "none"

    fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun notifyStepsUpdate(context: Context) {
        context.sendBroadcast(
            Intent(context, NothingStepsWidget::class.java).apply {
                action = NothingStepsWidget.ACTION_STEP_UPDATE
            }
        )
    }

    fun notifyHeadphonesUpdate(context: Context) {
        context.sendBroadcast(
            Intent(context, NothingHeadphonesWidget::class.java).apply {
                action = NothingHeadphonesWidget.ACTION_HEADPHONE_UPDATE
            }
        )
    }
}
