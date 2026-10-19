package com.unifimapper.org

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 60° perspective view — port of the reference three.js scene:
 * ground grid, path line + point markers, thin wall boxes, obstacle cubes and
 * AP markers at their install height. Drag horizontally → rotate (yaw),
 * vertically → zoom distance.
 */
class PerspectiveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var points: List<Pt> = emptyList()
    var walls: List<WallSeg> = emptyList()
    var obstacles: List<Obstacle> = emptyList()
    var aps: List<APRecord> = emptyList()
    var ceilingHeight = 2.7f

    // world centre (10,7) — same as the reference vector mapping
    private val cx = 10f
    private val cy = 7f

    private var yaw = 35f
    private var dist = 26f
    private val pitchDeg = 34f

    private val density = resources.displayMetrics.density
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity

    private class Cam {
        var ex = 0f; var ey = 0f; var ez = 0f
        var rx = 0f; var ry = 0f; var rz = 0f
        var ux = 0f; var uy = 0f; var uz = 0f
        var fx = 0f; var fy = 0f; var fz = 0f
        var f = 900f

        fun proj(px: Float, py: Float, pz: Float, w: Float, h: Float, out: FloatArray): Boolean {
            val vx = px - ex; val vy = py - ey; val vz = pz - ez
            val zc = vx * fx + vy * fy + vz * fz
            if (zc < 0.25f) return false
            val xc = vx * rx + vy * ry + vz * rz
            val yc = vx * ux + vy * uy + vz * uz
            out[0] = w / 2f + f * xc / zc
            out[1] = h / 2f - f * yc / zc
            out[2] = zc
            return true
        }
    }

    private val cam = Cam()
    private val pa = FloatArray(3)
    private val pb = FloatArray(3)

    private class Face(val pts: FloatArray) {
        var scr = FloatArray(8)
        var ok = false
        var d = 0f
    }

    private class Box(val faces: ArrayList<Face>, var dcam: Float)

    // warm palette (reference three.js scene)
    private val pBg = Paint().apply { color = 0xFFFAF6EC.toInt() }
    private val pGrid = Paint().apply { color = 0xFFE0D5C3.toInt(); strokeWidth = 1f; isAntiAlias = true }
    private val pFace = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val pEdge = Paint().apply {
        color = 0xFFB29B77.toInt(); strokeWidth = 1f; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val pPath = Paint().apply {
        color = 0xFF9B5933.toInt(); strokeWidth = 2.2f * density; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val pDot = Paint().apply { isAntiAlias = true }
    private val pPole = Paint().apply { strokeWidth = 1.5f * density; isAntiAlias = true }
    private val pLabel = Paint().apply {
        color = 0xFF302E28.toInt(); textSize = sp(10f); isAntiAlias = true; textAlign = Paint.Align.CENTER
        setShadowLayer(3f, 0f, 0f, 0xFFFFFFFF.toInt())
    }
    private val pHud = Paint().apply { color = 0xFF635E53.toInt(); textSize = sp(10f); isAntiAlias = true }

    fun renderToBitmap(mul: Int = 2): Bitmap {
        val b = Bitmap.createBitmap((width * mul).coerceAtLeast(64), (height * mul).coerceAtLeast(64), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.scale(mul.toFloat(), mul.toFloat())
        drawScene(c, width.toFloat(), height.toFloat())
        return b
    }

    override fun onDraw(canvas: Canvas) {
        drawScene(canvas, width.toFloat(), height.toFloat())
    }

    private fun drawScene(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(0xFFFAF6EC.toInt())

        val pitch = Math.toRadians(pitchDeg.toDouble())
        val yawR = Math.toRadians(yaw.toDouble())
        val cp = cos(pitch).toFloat(); val spch = sin(pitch).toFloat()
        cam.ex = cx + dist * cp * sin(yawR).toFloat()
        cam.ey = cy - dist * cp * cos(yawR).toFloat()
        cam.ez = 1.2f + dist * spch
        var fx = cx - cam.ex; var fy = cy - cam.ey; var fz = 1.2f - cam.ez
        val flen = sqrt(fx * fx + fy * fy + fz * fz)
        fx /= flen; fy /= flen; fz /= flen
        var rx = fy; var ry = -fx; var rz = 0f
        val rl = sqrt(rx * rx + ry * ry)
        if (rl < 1e-4f) { rx = 1f; ry = 0f } else { rx /= rl; ry /= rl }
        val ux = ry * fz
        val uy = -rx * fz
        val uz = rx * fy - ry * fx
        cam.fx = fx; cam.fy = fy; cam.fz = fz
        cam.rx = rx; cam.ry = ry; cam.rz = rz
        cam.ux = ux; cam.uy = uy; cam.uz = uz
        cam.f = 0.9f * max(w, h)

        // ground grid 0..20 × 0..14, 1m cells
        var n = 0
        var gx = 0f
        while (gx <= 20f && n < 40) {
            if (cam.proj(gx, 0f, 0f, w, h, pa) && cam.proj(gx, 14f, 0f, w, h, pb)) {
                canvas.drawLine(pa[0], pa[1], pb[0], pb[1], pGrid); n++
            }
            gx += 1f
        }
        var gy = 0f
        while (gy <= 14f && n < 80) {
            if (cam.proj(0f, gy, 0f, w, h, pa) && cam.proj(20f, gy, 0f, w, h, pb)) {
                canvas.drawLine(pa[0], pa[1], pb[0], pb[1], pGrid); n++
            }
            gy += 1f
        }

        // walls (painter's algorithm) — thin boxes like the reference (0.12m)
        val boxes = ArrayList<Box>()
        val wallH = min(3f, if (ceilingHeight > 0f) ceilingHeight else 2.7f)
        for (wl in walls) {
            val t = 0.06f
            var dx = wl.b.x - wl.a.x
            var dy = wl.b.y - wl.a.y
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1e-4f) continue
            dx /= len; dy /= len
            val px = -dy * t; val py = dx * t
            val z0 = 0f; val z1 = wallH
            val a1x = wl.a.x + px; val a1y = wl.a.y + py
            val a2x = wl.a.x - px; val a2y = wl.a.y - py
            val b1x = wl.b.x + px; val b1y = wl.b.y + py
            val b2x = wl.b.x - px; val b2y = wl.b.y - py
            val faces = ArrayList<Face>()
            faces.add(Face(floatArrayOf(a1x, a1y, z0, b1x, b1y, z0, b1x, b1y, z1, a1x, a1y, z1)))
            faces.add(Face(floatArrayOf(b2x, b2y, z0, a2x, a2y, z0, a2x, a2y, z1, b2x, b2y, z1)))
            faces.add(Face(floatArrayOf(a2x, a2y, z0, a1x, a1y, z0, a1x, a1y, z1, a2x, a2y, z1)))
            faces.add(Face(floatArrayOf(b1x, b1y, z0, b2x, b2y, z0, b2x, b2y, z1, b1x, b1y, z1)))
            faces.add(Face(floatArrayOf(a1x, a1y, z1, b1x, b1y, z1, b2x, b2y, z1, a2x, a2y, z1)))
            val ccx = (wl.a.x + wl.b.x) / 2f
            val ccy = (wl.a.y + wl.b.y) / 2f
            val dc = (cam.ex - ccx) * (cam.ex - ccx) + (cam.ey - ccy) * (cam.ey - ccy)
            boxes.add(Box(faces, dc))
        }
        boxes.sortByDescending { it.dcam }

        val lx = 0.4f; val ly = -0.5f; val lz = 0.75f
        for (box in boxes) {
            for (f in box.faces) {
                f.ok = true
                var zsum = 0f
                var i = 0
                while (i < 12) {
                    if (!cam.proj(f.pts[i], f.pts[i + 1], f.pts[i + 2], w, h, pa)) { f.ok = false; break }
                    f.scr[i / 3 * 2] = pa[0]
                    f.scr[i / 3 * 2 + 1] = pa[1]
                    zsum += pa[2]
                    i += 3
                }
                if (!f.ok) continue
                f.d = zsum / 4f
            }
            box.faces.sortByDescending { it.d }
            for (f in box.faces) {
                if (!f.ok) continue
                val e1x = f.pts[3] - f.pts[0]; val e1y = f.pts[4] - f.pts[1]; val e1z = f.pts[5] - f.pts[2]
                val e2x = f.pts[6] - f.pts[0]; val e2y = f.pts[7] - f.pts[1]; val e2z = f.pts[8] - f.pts[2]
                var nx = e1y * e2z - e1z * e2y
                var ny = e1z * e2x - e1x * e2z
                var nz = e1x * e2y - e1y * e2x
                val nl = sqrt(nx * nx + ny * ny + nz * nz)
                if (nl < 1e-6f) continue
                nx /= nl; ny /= nl; nz /= nl
                val dot = max(0f, nx * lx + ny * ly + nz * lz)
                val br = 0.66f + 0.34f * dot
                pFace.color = Color.rgb((200 * br).toInt(), (188 * br).toInt(), (168 * br).toInt())
                val path = Path()
                path.moveTo(f.scr[0], f.scr[1])
                path.lineTo(f.scr[2], f.scr[3])
                path.lineTo(f.scr[4], f.scr[5])
                path.lineTo(f.scr[6], f.scr[7])
                path.close()
                canvas.drawPath(path, pFace)
                canvas.drawPath(path, pEdge)
            }
        }

        // obstacle cubes
        for (o in obstacles) {
            val s = 0.35f
            val z0 = 0f; val z1 = 0.8f
            val x0 = o.x - s; val x1 = o.x + s
            val y0 = o.y - s; val y1 = o.y + s
            val faces = ArrayList<Face>()
            faces.add(Face(floatArrayOf(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1)))
            faces.add(Face(floatArrayOf(x1, y1, z0, x0, y1, z0, x0, y1, z1, x1, y1, z1)))
            faces.add(Face(floatArrayOf(x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1)))
            faces.add(Face(floatArrayOf(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1)))
            faces.add(Face(floatArrayOf(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1)))
            for (f in faces) {
                f.ok = true
                var zsum = 0f
                var i = 0
                while (i < 12) {
                    if (!cam.proj(f.pts[i], f.pts[i + 1], f.pts[i + 2], w, h, pa)) { f.ok = false; break }
                    f.scr[i / 3 * 2] = pa[0]
                    f.scr[i / 3 * 2 + 1] = pa[1]
                    zsum += pa[2]
                    i += 3
                }
                if (!f.ok) continue
                f.d = zsum / 4f
            }
            faces.sortByDescending { it.d }
            for (f in faces) {
                if (!f.ok) continue
                val path = Path()
                path.moveTo(f.scr[0], f.scr[1])
                path.lineTo(f.scr[2], f.scr[3])
                path.lineTo(f.scr[4], f.scr[5])
                path.lineTo(f.scr[6], f.scr[7])
                path.close()
                pFace.color = 0xFFBC8155.toInt()
                canvas.drawPath(path, pFace)
                canvas.drawPath(path, pEdge)
            }
        }

        // walked path
        if (points.size > 1) {
            val path = Path()
            var ok = true
            for ((i, p) in points.withIndex()) {
                if (!cam.proj(p.x, p.y, 0.03f, w, h, pa)) { ok = false; break }
                if (i == 0) path.moveTo(pa[0], pa[1]) else path.lineTo(pa[0], pa[1])
            }
            if (ok) canvas.drawPath(path, pPath)
        }

        // survey point markers
        for (p in points) {
            if (!cam.proj(p.x, p.y, 0.12f, w, h, pa)) continue
            val r = (cam.f * 0.1f / pa[2]).coerceIn(2.5f * density, 10f * density)
            pDot.color = 0xFF9B5933.toInt()
            canvas.drawCircle(pa[0], pa[1], r, pDot)
        }

        // access points at install height
        for (a in aps) {
            val ax = a.x ?: continue
            val ay = a.y ?: continue
            val topZ = min(4f, if (a.height > 0f) a.height else 2.7f)
            if (!cam.proj(ax, ay, 0f, w, h, pa)) continue
            if (!cam.proj(ax, ay, topZ, w, h, pb)) continue
            val col = if (a.source == "دمو") 0xFF9A7030.toInt() else 0xFF426D82.toInt()
            pPole.color = col
            canvas.drawLine(pa[0], pa[1], pb[0], pb[1], pPole)
            val r = (cam.f * 0.2f / pb[2]).coerceIn(4f * density, 24f * density)
            pDot.color = col
            canvas.drawCircle(pb[0], pb[1], r, pDot)
            if (pb[2] < dist * 1.6f) {
                canvas.drawText(a.name, pb[0], pb[1] - r - 5f * density, pLabel)
            }
        }

        // HUD
        canvas.drawText("چرخش ${yaw.toInt()}° · فاصله ${dist.toInt()}m · پرسپکتیو ۶۰°", 12f * density, h - 10f * density, pHud)
    }

    private val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            yaw += dx * 0.4f
            yaw = ((yaw % 360f) + 360f) % 360f
            dist = (dist + dy * 0.06f).coerceIn(4f, 80f)
            invalidate()
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gesture.onTouchEvent(event)
        return true
    }
}
