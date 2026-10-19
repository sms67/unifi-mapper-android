package com.unifimapper.org

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Camera overlay: guide crosshair grid + status lines (like the reference app). */
class CamHud @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var lines: List<String> = emptyList()
    var crosshair = true

    private val density = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 12f * resources.displayMetrics.scaledDensity
        setShadowLayer(4f, 0f, 0f, 0xE6000000.toInt())
    }
    private val pCross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80A66135.toInt()
        strokeWidth = 1.2f * density
    }

    override fun onDraw(canvas: Canvas) {
        if (crosshair) {
            val cx = width / 2f
            val cy = height / 2f
            canvas.drawLine(cx, 0f, cx, height.toFloat(), pCross)
            canvas.drawLine(0f, cy, width.toFloat(), cy, pCross)
        }
        var y = 28f * density
        val x = 12f * density
        for (l in lines) {
            canvas.drawText(l, x, y, p)
            y += 18f * density
        }
    }
}
