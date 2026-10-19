package com.unifimapper.org

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * 2D top view on a metric grid (world 20m × 14m, centred at 10,7 — same as the
 * reference app). Draws path, walls, obstacles/rooms, AP pins and the optional
 * estimated-coverage layer. Touch callbacks feed survey taps and AP placement.
 */
class TopView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var points: List<Pt> = emptyList()
    var walls: List<WallSeg> = emptyList()
    var obstacles: List<Obstacle> = emptyList()
    var aps: List<APRecord> = emptyList()
    var coverage = false
    var interactive = true
    var panEnabled = true

    var onTap: ((x: Float, y: Float) -> Unit)? = null
    var onTouchDown: ((x: Float, y: Float) -> Unit)? = null
    var onTouchMove: ((x: Float, y: Float) -> Unit)? = null
    var onTouchUp: (() -> Unit)? = null

    // camera: world centre (10,7) like the reference; scale fits 22×15 box
    private var camX = 10f
    private var camY = 7f
    private var scale = 60f
    private var fitted = false

    private val density = resources.displayMetrics.density
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity

    // warm palette of the reference design
    private val pBg = Paint().apply { color = 0xFFFAF6EC.toInt() }
    private val pGrid = Paint().apply { color = 0xFFE5DCCC.toInt(); strokeWidth = 1f }
    private val pPath = Paint().apply {
        color = 0xFF9B5933.toInt(); strokeWidth = 3f; strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND; isAntiAlias = true
    }
    private val pWall = Paint().apply {
        color = 0xFF786A54.toInt(); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val pObst = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val pPt0 = Paint().apply { color = 0xFF775C26.toInt(); isAntiAlias = true }
    private val pPt = Paint().apply { color = 0xFF9B5933.toInt(); isAntiAlias = true }
    private val pAp = Paint().apply { isAntiAlias = true }
    private val pCover = Paint().apply { style = Paint.Style.FILL }
    private val pText = Paint().apply {
        color = 0xFF302E28.toInt(); textSize = sp(10.5f); isAntiAlias = true
        setShadowLayer(3f, 0f, 0f, 0xFFFFFFFF.toInt())
    }
    private val pHud = Paint().apply { color = 0xFF41392C.toInt(); textSize = sp(10f); isAntiAlias = true }
    private val pNorth = Paint().apply { color = 0xFF635E53.toInt(); strokeWidth = 1.6f * density; isAntiAlias = true }

    private fun w2sX(wx: Float, w: Int) = w / 2f + (wx - camX) * scale
    private fun w2sY(wy: Float, h: Int) = h / 2f + (wy - camY) * scale
    private fun s2wX(sx: Float, w: Int) = (sx - w / 2f) / scale + camX
    private fun s2wY(sy: Float, h: Int) = (sy - h / 2f) / scale + camY

    private fun fitDefault() {
        camX = 10f
        camY = 7f
        if (width > 0 && height > 0) {
            scale = min(width / 22f, height / 15f).coerceAtLeast(8f)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (!fitted && w > 0) { fitDefault(); fitted = true }
    }

    fun renderToBitmap(mul: Int = 2): Bitmap {
        val b = Bitmap.createBitmap((width * mul).coerceAtLeast(64), (height * mul).coerceAtLeast(64), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.scale(mul.toFloat(), mul.toFloat())
        drawScene(c, width, height)
        return b
    }

    override fun onDraw(canvas: Canvas) {
        drawScene(canvas, width, height)
    }

    private fun drawScene(canvas: Canvas, w: Int, h: Int) {
        val fw = w.toFloat(); val fh = h.toFloat()
        canvas.drawRect(0f, 0f, fw, fh, pBg)

        // metric grid
        val x0 = camX - w / (2f * scale); val x1 = camX + w / (2f * scale)
        val y0 = camY - h / (2f * scale); val y1 = camY + h / (2f * scale)
        val step = if (scale >= 16f) 1f else 5f
        if ((x1 - x0) / step < 400) {
            var gx = floor(x0 / step) * step
            while (gx <= x1) {
                val sx = w2sX(gx, w)
                canvas.drawLine(sx, 0f, sx, fh, pGrid)
                gx += step
            }
            var gy = floor(y0 / step) * step
            while (gy <= y1) {
                val sy = w2sY(gy, h)
                canvas.drawLine(0f, sy, fw, sy, pGrid)
                gy += step
            }
        }

        // coverage layer (estimated, around placed APs)
        if (coverage) {
            for (a in aps) {
                val ax = a.x ?: continue
                val ay = a.y ?: continue
                val sx = w2sX(ax, w); val sy = w2sY(ay, h)
                val r = scale * 4f
                pCover.shader = RadialGradient(sx, sy, r,
                    intArrayOf(0x66A66135, 0x33CBA36A, 0x00CBA36A),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(sx - r, sy - r, sx + r, sy + r, pCover)
            }
            pCover.shader = null
        }

        // path
        if (points.size > 1) {
            val pp = Path()
            for ((i, p) in points.withIndex()) {
                val sx = w2sX(p.x, w); val sy = w2sY(p.y, h)
                if (i == 0) pp.moveTo(sx, sy) else pp.lineTo(sx, sy)
            }
            canvas.drawPath(pp, pPath)
        }

        // walls
        for (wl in walls) {
            canvas.drawLine(
                w2sX(wl.a.x, w), w2sY(wl.a.y, h),
                w2sX(wl.b.x, w), w2sY(wl.b.y, h), pWall
            )
        }

        // obstacles / rooms
        for (o in obstacles) {
            pObst.color = if (o.kind == "اتاق") 0xFF777D91.toInt() else 0xFFBC8155.toInt()
            canvas.drawRect(w2sX(o.x, w) - 8f, w2sY(o.y, h) - 8f, w2sX(o.x, w) + 8f, w2sY(o.y, h) + 8f, pObst)
        }

        // points
        for ((i, p) in points.withIndex()) {
            canvas.drawCircle(w2sX(p.x, w), w2sY(p.y, h), 5f, if (i == 0) pPt0 else pPt)
        }

        // AP pins
        for (a in aps) {
            val ax = a.x ?: continue
            val ay = a.y ?: continue
            val sx = w2sX(ax, w); val sy = w2sY(ay, h)
            pAp.color = if (a.source == "دمو") 0xFF9A7030.toInt() else 0xFF426D82.toInt()
            canvas.drawCircle(sx, sy, 10f, pAp)
            canvas.drawText(
                if (a.source == "دمو") "DEMO · " + a.name else a.name,
                sx + 12f, sy - 8f, pText
            )
        }

        // north arrow
        val nx = 18f * density; val ny = 30f * density
        canvas.drawLine(nx, ny + 11f * density, nx, ny - 11f * density, pNorth)
        canvas.drawText("شمال ↑", nx - 6f * density, ny + 26f * density, pHud)

        // hint
        canvas.drawText("هر خانه ≈ ۱ متر (پس از کالیبراسیون)", fw - 14f * density - pText.measureText("هر خانه ≈ ۱ متر (پس از کالیبراسیون)"), fh - 12f * density, pHud)
    }

    private val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (interactive) onTap?.invoke(s2wX(e.x, width), s2wY(e.y, height))
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            camX -= dx / scale
            camY -= dy / scale
            invalidate()
            return true
        }
    })

    private val scaleDet = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val f = d.scaleFactor
            val wx = s2wX(d.focusX, width)
            val wy = s2wY(d.focusY, height)
            scale = (scale * f).coerceIn(8f, 400f)
            camX = wx - (d.focusX - width / 2f) / scale
            camY = wy - (d.focusY - height / 2f) / scale
            invalidate()
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onTouchDown?.invoke(s2wX(event.x, width), s2wY(event.y, height))
            MotionEvent.ACTION_MOVE -> onTouchMove?.invoke(s2wX(event.x, width), s2wY(event.y, height))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTouchUp?.invoke()
        }
        if (panEnabled) {
            scaleDet.onTouchEvent(event)
            if (!scaleDet.isInProgress) gesture.onTouchEvent(event)
        }
        return true
    }
}
