package com.kythonlk.coolw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.util.Calendar
import kotlin.math.ceil

object DotCalendarRenderer {

    /**
     * Draws the current month as a 7-column dot grid (Nothing-style "dot calendar"):
     * past days are filled dots, today is a larger red dot, future days are hollow rings.
     */
    fun renderMonth(
        year: Int,
        month: Int,
        today: Int,
        activeColor: Int = Color.WHITE,
        todayColor: Int = Color.parseColor("#FFFF3B30"),
        inactiveColor: Int = Color.parseColor("#40FFFFFF"),
        dotRadius: Float = 6f,
        spacing: Float = 20f
    ): Bitmap {
        val calendar = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val firstWeekdayOffset = calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val rows = ceil((firstWeekdayOffset + daysInMonth) / 7.0).toInt().coerceAtLeast(1)

        val cellPadding = spacing / 2f
        val width = (spacing * 7 + cellPadding).toInt().coerceAtLeast(1)
        val height = (spacing * rows + cellPadding).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dotRadius * 0.35f
        }

        for (day in 1..daysInMonth) {
            val index = firstWeekdayOffset + day - 1
            val col = index % 7
            val row = index / 7
            val cx = cellPadding + col * spacing + spacing / 2f
            val cy = cellPadding + row * spacing + spacing / 2f

            when {
                day == today -> {
                    fillPaint.color = todayColor
                    canvas.drawCircle(cx, cy, dotRadius * 1.4f, fillPaint)
                }
                day < today -> {
                    fillPaint.color = activeColor
                    canvas.drawCircle(cx, cy, dotRadius, fillPaint)
                }
                else -> {
                    strokePaint.color = inactiveColor
                    canvas.drawCircle(cx, cy, dotRadius, strokePaint)
                }
            }
        }

        return bitmap
    }
}
