package com.kythonlk.coolw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object DotMatrixRenderer {

    init {
        System.loadLibrary("coolw")
    }

    // Native JNI methods
    private external fun nativeCalcSize(numChars: Int, dotSpacing: Float, charSpacing: Float): IntArray
    private external fun nativeRenderText(
        bitmap: Bitmap, text: String,
        activeColor: Int, inactiveColor: Int,
        dotRadius: Float, dotSpacing: Float, charSpacing: Float,
        drawInactive: Boolean
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
        // Calculate bitmap dimensions in native code
        val size = nativeCalcSize(text.length, dotSpacing, charSpacing)
        val width = size[0].coerceAtLeast(1)
        val height = size[1].coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Render directly into bitmap pixel buffer from C++
        nativeRenderText(bitmap, text, activeColor, inactiveColor, dotRadius, dotSpacing, charSpacing, drawInactive)

        return bitmap
    }
}
