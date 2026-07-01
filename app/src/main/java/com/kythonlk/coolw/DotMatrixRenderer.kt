package com.kythonlk.coolw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object DotMatrixRenderer {

    private val DIGITS = mapOf(
        '0' to arrayOf(
            " ### ",
            "#   #",
            "#   #",
            "#   #",
            "#   #",
            "#   #",
            " ### "
        ),
        '1' to arrayOf(
            "  #  ",
            " ##  ",
            "  #  ",
            "  #  ",
            "  #  ",
            "  #  ",
            " ### "
        ),
        '2' to arrayOf(
            " ### ",
            "#   #",
            "    #",
            "   # ",
            "  #  ",
            " #   ",
            "#####"
        ),
        '3' to arrayOf(
            " ### ",
            "#   #",
            "    #",
            "  ## ",
            "    #",
            "#   #",
            " ### "
        ),
        '4' to arrayOf(
            "   # ",
            "  ## ",
            " # # ",
            "#  # ",
            "#####",
            "   # ",
            "   # "
        ),
        '5' to arrayOf(
            "#####",
            "#    ",
            "#### ",
            "    #",
            "    #",
            "#   #",
            " ### "
        ),
        '6' to arrayOf(
            "  ## ",
            " #   ",
            "#### ",
            "#   #",
            "#   #",
            "#   #",
            " ### "
        ),
        '7' to arrayOf(
            "#####",
            "    #",
            "   # ",
            "  #  ",
            " #   ",
            " #   ",
            " #   "
        ),
        '8' to arrayOf(
            " ### ",
            "#   #",
            "#   #",
            " ### ",
            "#   #",
            "#   #",
            " ### "
        ),
        '9' to arrayOf(
            " ### ",
            "#   #",
            "#   #",
            " ####",
            "    #",
            "   # ",
            " ##  "
        ),
        ':' to arrayOf(
            "     ",
            "  #  ",
            "     ",
            "     ",
            "     ",
            "  #  ",
            "     "
        ),
        ' ' to arrayOf(
            "     ",
            "     ",
            "     ",
            "     ",
            "     ",
            "     ",
            "     "
        ),
        'S' to arrayOf(
            " ####",
            "#    ",
            " ### ",
            "    #",
            "    #",
            "#   #",
            " ### "
        ),
        'T' to arrayOf(
            "#####",
            "  #  ",
            "  #  ",
            "  #  ",
            "  #  ",
            "  #  ",
            "  #  "
        ),
        'E' to arrayOf(
            "#####",
            "#    ",
            "#### ",
            "#    ",
            "#    ",
            "#    ",
            "#####"
        ),
        'P' to arrayOf(
            "#### ",
            "#   #",
            "#   #",
            "#### ",
            "#    ",
            "#    ",
            "#    "
        )
    )

    fun renderText(
        text: String,
        activeColor: Int = Color.WHITE,
        inactiveColor: Int = Color.parseColor("#1e1e1e"),
        dotRadius: Float = 6f,
        dotSpacing: Float = 16f,
        charSpacing: Float = 14f,
        drawInactive: Boolean = true
    ): Bitmap {
        val rows = 7
        val cols = 5
        val numChars = text.length

        // Calculate size of bitmap
        val charWidth = cols * dotSpacing
        val totalWidth = (numChars * charWidth) + ((numChars - 1) * charSpacing) + (dotSpacing * 2)
        val totalHeight = (rows * dotSpacing) + (dotSpacing * 2)

        val bitmap = Bitmap.createBitmap(totalWidth.toInt(), totalHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val paintActive = Paint().apply {
            color = activeColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val paintInactive = Paint().apply {
            color = inactiveColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        var startX = dotSpacing

        for (c in text) {
            val glyph = DIGITS[c.uppercaseChar()] ?: DIGITS[' ']!!
            for (r in 0 until rows) {
                val rowString = glyph[r]
                for (col in 0 until cols) {
                    val isActive = rowString[col] == '#'
                    val x = startX + col * dotSpacing
                    val y = dotSpacing + r * dotSpacing

                    if (isActive) {
                        canvas.drawCircle(x, y, dotRadius, paintActive)
                    } else if (drawInactive) {
                        canvas.drawCircle(x, y, dotRadius * 0.4f, paintInactive)
                    }
                }
            }
            startX += charWidth + charSpacing
        }

        return bitmap
    }
}
