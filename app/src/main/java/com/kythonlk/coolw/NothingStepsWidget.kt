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
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews

class NothingStepsWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_STEP_UPDATE = "com.kythonlk.coolw.ACTION_STEP_UPDATE"
    }

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

        val stepsRingBitmap = drawStepsRing(context, steps, goal)

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

    private fun drawStepsRing(context: Context, steps: Int, goal: Int): Bitmap {
        val size = 200
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw track circle
        val margin = 15f
        val rect = RectF(margin, margin, size - margin, size - margin)
        
        val bgPaint = Paint().apply {
            color = Color.parseColor("#15FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 10f
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - margin, bgPaint)

        // Draw progress arc
        val progressPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        val sweepAngle = (steps.toFloat() / goal.toFloat() * 360f).coerceAtMost(360f)
        canvas.drawArc(rect, -90f, sweepAngle, false, progressPaint)

        // Draw steps count inside
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
        
        val left = (size - stepsBitmap.width) / 2f
        val top = (size - stepsBitmap.height) / 2f
        canvas.drawBitmap(stepsBitmap, left, top, null)

        return bitmap
    }
}
