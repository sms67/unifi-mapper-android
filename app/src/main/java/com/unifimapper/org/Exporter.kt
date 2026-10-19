package com.unifimapper.org

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.min

/** Exports: PNG map image (with title block), OBJ 3D model (3ds Max), JSON report. */
object Exporter {

    // ── PNG ─────────────────────────────────────────────────

    /** Compose the map render with a title block + QC note (reference style). */
    fun pngReport(map: Bitmap, p: Project): Bitmap {
        val header = 120f
        val w = map.width
        val h = map.height + header.toInt()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(0xFFFFFFFF.toInt())
        c.drawBitmap(map, 0f, 0f, null)

        val d = w / 1080f
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF302E28.toInt() }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF635E53.toInt() }
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFA66135.toInt() }
        val line = Paint().apply { color = 0xFFDDD4C4.toInt(); strokeWidth = 1.5f * d }

        ink.textSize = 34f * d; ink.isFakeBoldText = true
        muted.textSize = 24f * d
        accent.textSize = 24f * d; accent.isFakeBoldText = true

        val top = map.height.toFloat()
        c.drawLine(0f, top, w.toFloat(), top, line)
        c.drawText("نقشه‌بردار شبکه UniFi", 24f * d, top + 42f * d, ink)
        c.drawText(p.projectName + (if (p.isDemo) " · DEMO" else ""), 24f * d, top + 78f * d, accent)
        c.drawText(
            p.organization.ifEmpty { "—" } + " · " + p.site.ifEmpty { "—" } + " · " + p.surveyDate,
            24f * d, top + 110f * d, muted
        )
        val stats = "نقاط: " + p.points.size + " · دیوار: " + p.walls.size + " · AP: " + p.accessPoints.size +
            " · مقیاس: " + (if (p.calibrationDistance > 0f) p.calibrationDistance.toString() + " م" else "کالیبره نشده")
        c.drawText(stats, w - 24f * d - muted.measureText(stats), top + 42f * d, muted)
        val note = "فاصله‌ها و موقعیت‌ها تقریبی‌اند · خروجی دستی"
        c.drawText(note, w - 24f * d - muted.measureText(note), top + 110f * d, muted)
        return out
    }

    // ── OBJ (3ds Max friendly) ──────────────────────────────

    /**
     * Metric OBJ, Z-up: survey path polyline, point markers, wall boxes,
     * obstacle cubes and named AP octahedra groups — importable via
     * File → Import → Geometry → OBJ in 3ds Max.
     */
    fun obj(p: Project): String {
        val sb = StringBuilder()
        sb.append("# UniFi Network Mapper — approximate 3D survey model\n")
        sb.append("# units: meters — Z axis is up\n")
        sb.append("# 3ds Max: File > Import > Geometry > OBJ\n\n")
        sb.append("o UniFiSurvey\n")
        var vc = 0

        fun v(x: Float, y: Float, z: Float): Int {
            vc++
            sb.append(String.format(Locale.US, "v %.3f %.3f %.3f%n", x, y, z))
            return vc
        }

        fun f4(a: Int, b: Int, c: Int, d: Int) {
            sb.append("f ").append(a).append(' ').append(b).append(' ').append(c).append(' ').append(d).append('\n')
        }

        fun f3(a: Int, b: Int, c: Int) {
            sb.append("f ").append(a).append(' ').append(b).append(' ').append(c).append('\n')
        }

        val zc = 0f

        // survey path polyline
        sb.append("g SurveyPath\n")
        val pathIdx = ArrayList<Int>()
        for (pt in p.points) pathIdx.add(v(pt.x, pt.y, zc + 0.02f))
        for (i in 1 until pathIdx.size) sb.append("l ").append(pathIdx[i - 1]).append(' ').append(pathIdx[i]).append('\n')

        // point markers (small octahedra)
        sb.append("g SurveyPoints\n")
        for (pt in p.points) {
            val r = 0.09f
            val top = v(pt.x, pt.y, zc + 0.02f + r)
            val bot = v(pt.x, pt.y, zc + 0.02f - r)
            val n = v(pt.x, pt.y + r, zc + 0.02f)
            val e = v(pt.x + r, pt.y, zc + 0.02f)
            val s = v(pt.x, pt.y - r, zc + 0.02f)
            val wv = v(pt.x - r, pt.y, zc + 0.02f)
            f3(top, n, e); f3(top, e, s); f3(top, s, wv); f3(top, wv, n)
            f3(bot, e, n); f3(bot, s, e); f3(bot, wv, s); f3(bot, n, wv)
        }

        // walls as boxes
        sb.append("g Walls\n")
        val wallH = min(3f, if (p.ceilingHeight > 0f) p.ceilingHeight else 2.7f)
        for (wl in p.walls) {
            val t = 0.06f
            var dx = wl.b.x - wl.a.x
            var dy = wl.b.y - wl.a.y
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            if (len < 1e-4f) continue
            dx /= len; dy /= len
            val px = -dy * t; val py = dx * t
            val z0 = zc; val z1 = zc + wallH
            val a1 = v(wl.a.x + px, wl.a.y + py, z0)
            val b1 = v(wl.b.x + px, wl.b.y + py, z0)
            val b2 = v(wl.b.x - px, wl.b.y - py, z0)
            val a2 = v(wl.a.x - px, wl.a.y - py, z0)
            val a1t = v(wl.a.x + px, wl.a.y + py, z1)
            val b1t = v(wl.b.x + px, wl.b.y + py, z1)
            val b2t = v(wl.b.x - px, wl.b.y - py, z1)
            val a2t = v(wl.a.x - px, wl.a.y - py, z1)
            f4(a1, b1, b1t, a1t)
            f4(b2, a2, a2t, b2t)
            f4(a2, a1, a1t, a2t)
            f4(b1, b2, b2t, b1t)
            f4(a1t, b1t, b2t, a2t)
        }

        // obstacles / room markers
        for ((oi, o) in p.obstacles.withIndex()) {
            sb.append("g ").append(if (o.kind == "اتاق") "Room" else "Obstacle").append(oi + 1).append('\n')
            val s = 0.35f
            val z0 = zc; val z1 = zc + 0.8f
            val x0 = o.x - s; val x1 = o.x + s
            val y0 = o.y - s; val y1 = o.y + s
            val v00 = v(x0, y0, z0); val v10 = v(x1, y0, z0); val v11 = v(x1, y1, z0); val v01 = v(x0, y1, z0)
            val t00 = v(x0, y0, z1); val t10 = v(x1, y0, z1); val t11 = v(x1, y1, z1); val t01 = v(x0, y1, z1)
            f4(v00, v10, t10, t00)
            f4(v11, v01, t01, t11)
            f4(v01, v00, t00, t01)
            f4(v10, v11, t11, t10)
            f4(t00, t10, t11, t01)
        }

        // access points — named octahedra at install height with drop line
        for ((ai, a) in p.accessPoints.withIndex()) {
            if (a.x == null || a.y == null) continue
            val label = sanitizeAscii(if (a.ssid.isNotEmpty()) a.ssid else a.name).ifEmpty { "AP" + (ai + 1) }
            val rssiTxt = if (a.rssi != null) "_RSSI" + a.rssi!!.toInt() + "dBm" else ""
            sb.append("g AP_").append(label).append(rssiTxt)
                .append("_H").append(String.format(Locale.US, "%.1f", a.height)).append("m\n")
            val cz = zc + min(4f, if (a.height > 0f) a.height else 2.7f)
            val r = 0.28f
            val ax = a.x!!
            val ay = a.y!!
            val top = v(ax, ay, cz + r)
            val bot = v(ax, ay, cz - r)
            val n = v(ax, ay + r, cz)
            val e = v(ax + r, ay, cz)
            val s = v(ax, ay - r, cz)
            val wv = v(ax - r, ay, cz)
            f3(top, n, e); f3(top, e, s); f3(top, s, wv); f3(top, wv, n)
            f3(bot, e, n); f3(bot, s, e); f3(bot, wv, s); f3(bot, n, wv)
            val lw = 0.04f
            val l1 = v(ax - lw, ay, zc)
            val l2 = v(ax + lw, ay, zc)
            val l3 = v(ax + lw, ay, cz - r)
            val l4 = v(ax - lw, ay, cz - r)
            f4(l1, l2, l3, l4)
        }
        return sb.toString()
    }

    private fun sanitizeAscii(s: String): String {
        val sb = StringBuilder()
        for (c in s) if (c.code in 33..126 && c != ' ') sb.append(c)
        return sb.toString().take(24)
    }

    // ── JSON report ─────────────────────────────────────────

    fun jsonReport(p: Project): JSONObject {
        val o = JSONObject()
        o.put("notice", "نقشه و فاصله‌ها تقریبی‌اند؛ ثبت‌ها دستی یا اسکن دستگاه هستند و جایگزین اندازه‌گیری مهندسی نیستند.")
        o.put("project", p.toJson())
        val areas = JSONArray()
        for (f in p.floors) areas.put(JSONObject().put("id", f.id).put("name", f.name).put("done", f.done))
        o.put("areas", areas)
        val pts = JSONArray()
        for (pt in p.points) pts.put(JSONObject()
            .put("id", pt.id).put("x", pt.x.toDouble()).put("y", pt.y.toDouble())
            .put("area", pt.area).put("area_name", p.areaName(pt.area))
            .put("source", pt.source).put("time", pt.time))
        o.put("points", pts)
        val aps = JSONArray()
        for (a in p.accessPoints) aps.put(JSONObject()
            .put("id", a.id).put("name", a.name).put("ssid", a.ssid).put("bssid", a.bssid)
            .put("model", a.model).put("band", a.band)
            .put("rssi", a.rssi?.toDouble() ?: JSONObject.NULL)
            .put("distance", a.distance?.toDouble() ?: JSONObject.NULL)
            .put("height", a.height.toDouble()).put("status", a.status).put("note", a.note)
            .put("area", a.area).put("area_name", p.areaName(a.area))
            .put("x", a.x?.toDouble() ?: JSONObject.NULL)
            .put("y", a.y?.toDouble() ?: JSONObject.NULL)
            .put("distance_label", "تقریبی").put("signal_source", a.source)
            .put("time", a.time))
        o.put("access_points", aps)
        val q = JSONArray()
        for (item in p.quality()) q.put(JSONObject().put("text", item.text).put("action", item.action))
        o.put("quality", q)
        return o
    }
}
