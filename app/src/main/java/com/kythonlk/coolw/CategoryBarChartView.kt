package com.kythonlk.coolw

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.Locale

class CategoryBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var data: List<CategoryTotal> = emptyList()

    private val density = resources.displayMetrics.density
    private val rowHeightPx = 40 * density
    private val trackHeightPx = 6 * density

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        textSize = 12 * density
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        letterSpacing = 0.05f
    }
    private val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12 * density
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#15FFFFFF")
    }
    private val fillPaintRed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFF3B30")
    }
    private val fillPaintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAFFFFFF")
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FFFFFF")
        textSize = 12 * density
        textAlign = Paint.Align.CENTER
    }

    fun setData(newData: List<CategoryTotal>) {
        data = newData
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = if (data.isEmpty()) 1 else data.size
        val desiredHeight = (paddingTop + paddingBottom + rows * rowHeightPx).toInt()
        setMeasuredDimension(width, desiredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (data.isEmpty()) {
            canvas.drawText(
                "NO EXPENSES THIS MONTH",
                width / 2f,
                paddingTop + rowHeightPx / 2f,
                emptyPaint
            )
            return
        }

        val maxValue = data.maxOf { it.total }.coerceAtLeast(0.01)
        val trackLeft = paddingLeft.toFloat()
        val trackRight = (width - paddingRight).toFloat()
        val trackWidth = trackRight - trackLeft

        data.forEachIndexed { index, item ->
            val rowTop = paddingTop + index * rowHeightPx
            val labelBaseline = rowTop + 14 * density
            canvas.drawText(item.category.uppercase(Locale.getDefault()), trackLeft, labelBaseline, labelPaint)
            canvas.drawText(formatAmount(item.total), trackRight, labelBaseline, amountPaint)

            val trackTop = rowTop + 20 * density
            val trackBottom = trackTop + trackHeightPx
            val trackRect = RectF(trackLeft, trackTop, trackRight, trackBottom)
            canvas.drawRoundRect(trackRect, trackHeightPx / 2f, trackHeightPx / 2f, trackPaint)

            val fillWidth = (trackWidth * (item.total / maxValue)).toFloat().coerceAtLeast(trackHeightPx)
            val fillRect = RectF(trackLeft, trackTop, trackLeft + fillWidth, trackBottom)
            canvas.drawRoundRect(fillRect, trackHeightPx / 2f, trackHeightPx / 2f, if (index == 0) fillPaintRed else fillPaintWhite)
        }
    }

    private fun formatAmount(value: Double): String {
        return String.format(Locale.getDefault(), "%,.2f", value)
    }
}
