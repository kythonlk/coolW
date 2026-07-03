package com.kythonlk.coolw

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class NothingFinanceWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_FINANCE_UPDATE = "com.kythonlk.coolw.ACTION_FINANCE_UPDATE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_FINANCE_UPDATE || intent.action == "com.kythonlk.coolw.UPDATE_ALL_WIDGETS") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context.packageName, NothingFinanceWidget::class.java.name)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val dbHelper = FinanceDatabaseHelper(context)
        val calendar = Calendar.getInstance()
        val summary = dbHelper.getMonthSummary(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))

        val balanceText = (if (summary.balance < 0) "-" else "") + format(abs(summary.balance))
        val breakdownText = "+${format(summary.income)}  ·  -${format(summary.expense)}"

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_nothing_finance)
            views.setTextViewText(R.id.finance_balance, balanceText)
            views.setTextColor(
                R.id.finance_balance,
                if (summary.balance < 0) Color.parseColor("#FFFF3B30") else Color.WHITE
            )
            views.setTextViewText(R.id.finance_breakdown, breakdownText)

            val appIntent = Intent(context, FinanceActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 4, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.finance_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun format(value: Double): String = String.format(Locale.getDefault(), "%,.2f", value)
}
