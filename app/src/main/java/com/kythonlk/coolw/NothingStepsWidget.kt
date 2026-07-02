package com.kythonlk.coolw

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.widget.RemoteViews

class NothingStepsWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_STEP_UPDATE = "com.kythonlk.coolw.ACTION_STEP_UPDATE"

        init {
            System.loadLibrary("coolw")
        }
    }

    // Native JNI method for ring drawing
    private external fun nativeDrawStepsRing(
        bitmap: Bitmap, steps: Int, goal: Int,
        ringColor: Int, trackColor: Int, strokeWidth: Float
    )

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        StepCounterService.start(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_STEP_UPDATE || intent.action == "com.kythonlk.coolw.UPDATE_ALL_WIDGETS") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context.packageName, NothingStepsWidget::class.java.name)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = CoolWPrefs.prefs(context)
        val steps = prefs.getInt(CoolWPrefs.STEPS_TODAY, 0)
        val goal = prefs.getInt(CoolWPrefs.STEPS_GOAL, 10000)
        val source = prefs.getString(CoolWPrefs.STEPS_SOURCE, CoolWPrefs.SOURCE_NONE)
            ?: CoolWPrefs.SOURCE_NONE

        val stepsRingBitmap = drawStepsRing(steps, goal)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_nothing_steps)
            views.setImageViewBitmap(R.id.steps_image, stepsRingBitmap)
            views.setTextViewText(R.id.steps_goal, "GOAL: $goal")
            views.setTextViewText(R.id.steps_source, HealthConnectHelper.sourceLabel(source))

            // On click, open the app
            val appIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 1, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.steps_image, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun drawStepsRing(steps: Int, goal: Int): Bitmap {
        val size = 200
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        // Draw ring track + progress arc natively in C++
        nativeDrawStepsRing(
            bitmap, steps, goal,
            Color.WHITE,                         // ring progress color
            Color.parseColor("#15FFFFFF"),        // track color
            10f                                   // stroke width
        )

        // Overlay the step count text using native dot matrix renderer
        val stepsStr = steps.toString()
        val stepsBitmap = DotMatrixRenderer.renderText(
            text = stepsStr,
            activeColor = Color.WHITE,
            inactiveColor = Color.parseColor("#0A121212"),
            dotRadius = 1.8f,
            dotSpacing = 6f,
            charSpacing = 4f,
            drawInactive = false
        )

        val canvas = Canvas(bitmap)
        val left = (size - stepsBitmap.width) / 2f
        val top = (size - stepsBitmap.height) / 2f
        canvas.drawBitmap(stepsBitmap, left, top, null)

        return bitmap
    }
}
