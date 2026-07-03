package com.kythonlk.coolw

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NothingTodoWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_TODO_WIDGET_UPDATE = "com.kythonlk.coolw.ACTION_TODO_WIDGET_UPDATE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TODO_WIDGET_UPDATE || intent.action == "com.kythonlk.coolw.UPDATE_ALL_WIDGETS") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context.packageName, NothingTodoWidget::class.java.name)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val dbHelper = TodoDatabaseHelper(context)
        val now = System.currentTimeMillis()
        val nextTodo = dbHelper.getAllTodos()
            .filter { !it.isCompleted && it.timeInMillis >= now }
            .minByOrNull { it.timeInMillis }

        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_nothing_todo)
            if (nextTodo != null) {
                views.setTextViewText(R.id.todo_title, nextTodo.title.uppercase(Locale.getDefault()))
                views.setTextViewText(R.id.todo_time, sdf.format(Date(nextTodo.timeInMillis)).uppercase(Locale.getDefault()))
                views.setImageViewResource(R.id.todo_dot, R.drawable.nothing_red_dot)
            } else {
                views.setTextViewText(R.id.todo_title, "ALL CLEAR")
                views.setTextViewText(R.id.todo_time, "No upcoming tasks")
                views.setImageViewResource(R.id.todo_dot, R.drawable.nothing_red_dot_dim)
            }

            val appIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 5, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.todo_widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
