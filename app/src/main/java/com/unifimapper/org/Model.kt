package com.unifimapper.org

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Data model — a faithful port of the reference UniFi network survey app. */

class Pt2(val x: Float, val y: Float)

class Pt(
    val id: String,
    val x: Float,
    val y: Float,
    val area: String,
    val source: String,
    val time: String
)

class WallSeg(
    val id: String,
    val a: Pt2,
    val b: Pt2,
    val area: String,
    val source: String
)

class Obstacle(
    val id: String,
    val x: Float,
    val y: Float,
    val kind: String,      // «مانع» یا «اتاق»
    val area: String,
    val source: String
)

class Floor(
    val id: String,
    val name: String,
    var done: Boolean
)

class APRecord(
    val id: String,
    val name: String,
    val ssid: String,
    val bssid: String,
    val model: String,
    val band: String,       // «۲٫۴ GHz» / «۵ GHz» / «۶ GHz» / ""
    val rssi: Float?,       // dBm — ممکن است ثبت نشده باشد
    val distance: Float?,   // تخمینی
    val height: Float,      // ارتفاع نصب (متر)
    val status: String,     // «موجود» / «پیشنهادی»
    val note: String,
    var area: String,       // ناحیه — با جانمایی تغییر می‌کند
    var x: Float?,          // موقعیت دستی روی نقشه
    var y: Float?,
    val source: String,     // «دستی» / «اسکن» / «دمو»
    val time: String
)

class ProjSettings(
    var north: Float = 0f,
    var targetAccuracy: Float = 1f,
    var installHeight: Float = 2.7f,
    var scaleLocked: Boolean = false
)

class QcItem(val text: String, val action: String)

class Project(
    val id: String,
    var projectName: String,
    var organization: String,
    var site: String,
    var building: String,
    var description: String,
    var surveyDate: String,
    var status: String,
    val unit: String,
    var ceilingHeight: Float,
    var calibrationDistance: Float,
    var calibrationConfidence: Float,
    val floors: ArrayList<Floor>,
    val points: ArrayList<Pt>,
    val walls: ArrayList<WallSeg>,
    val obstacles: ArrayList<Obstacle>,
    val accessPoints: ArrayList<APRecord>,
    val paths: ArrayList<Pt>,
    var settings: ProjSettings,
    var isDemo: Boolean,
    val createdAt: String,
    var updatedAt: String
) {
    fun areaName(areaId: String): String {
        for (f in floors) if (f.id == areaId) return f.name
        return "بدون ناحیه"
    }

    fun pointsIn(areaId: String): ArrayList<Pt> {
        val r = ArrayList<Pt>()
        for (p in points) if (p.area == areaId) r.add(p)
        return r
    }

    fun lastPointIn(areaId: String): Pt? = pointsIn(areaId).lastOrNull()

    /** کنترل کیفیت — port of quality() from the reference app. */
    fun quality(): ArrayList<QcItem> {
        val q = ArrayList<QcItem>()
        if (calibrationDistance <= 0f) {
            q.add(QcItem("مقیاس کالیبره نشده است.", "طول مرجع را در برداشت وارد کنید."))
        } else if (calibrationConfidence < 0.7f) {
            q.add(QcItem("اطمینان مقیاس پایین است.", "فاصله مرجع را با متر واقعی دوباره بررسی کنید."))
        }
        if (points.size < 3) {
            q.add(QcItem("نقاط ثبت‌شده برای ترسیم محیط کم هستند.", "چند نقطه مرجع دیگر ثبت کنید."))
        }
        if (floors.isEmpty() || floors.any { f -> points.none { it.area == f.id } }) {
            q.add(QcItem("یک یا چند ناحیه بدون برداشت است.", "ناحیه را انتخاب و نقطه ثبت کنید."))
        }
        if (accessPoints.any { it.x == null }) {
            q.add(QcItem("AP بدون موقعیت وجود دارد.", "از بخش نقشه AP را انتخاب و جانمایی کنید."))
        }
        if (isDemo) {
            q.add(QcItem("داده‌های این پروژه DEMO هستند.", "برای ثبت داده واقعی پروژه جدید بسازید."))
        }
        if (accessPoints.any { it.rssi != null && it.rssi < -75f }) {
            q.add(QcItem("سیگنال ثبت‌شده ضعیف است؛ پوشش یا تداخل بررسی شود.", "با ابزار تأییدشده در محل دوباره اندازه‌گیری کنید."))
        }
        return q
    }

    // ── JSON ────────────────────────────────────────────────

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("project_name", projectName)
        o.put("organization", organization)
        o.put("site", site)
        o.put("building", building)
        o.put("description", description)
        o.put("survey_date", surveyDate)
        o.put("status", status)
        o.put("unit", unit)
        o.put("ceiling_height", ceilingHeight.toDouble())
        o.put("calibration_distance", calibrationDistance.toDouble())
        o.put("calibration_confidence", calibrationConfidence.toDouble())
        o.put("floors", JSONArray().apply {
            floors.forEach { f -> put(JSONObject().put("id", f.id).put("name", f.name).put("done", f.done)) }
        })
        o.put("points", JSONArray().apply {
            points.forEach { p -> put(JSONObject()
                .put("id", p.id).put("x", p.x.toDouble()).put("y", p.y.toDouble())
                .put("area", p.area).put("source", p.source).put("time", p.time)) }
        })
        o.put("walls", JSONArray().apply {
            walls.forEach { w -> put(JSONObject()
                .put("id", w.id)
                .put("a", JSONObject().put("x", w.a.x.toDouble()).put("y", w.a.y.toDouble()))
                .put("b", JSONObject().put("x", w.b.x.toDouble()).put("y", w.b.y.toDouble()))
                .put("area", w.area).put("source", w.source)) }
        })
        o.put("obstacles", JSONArray().apply {
            obstacles.forEach { ob -> put(JSONObject()
                .put("id", ob.id).put("x", ob.x.toDouble()).put("y", ob.y.toDouble())
                .put("kind", ob.kind).put("area", ob.area).put("source", ob.source)) }
        })
        o.put("access_points", JSONArray().apply {
            accessPoints.forEach { a -> put(JSONObject()
                .put("id", a.id).put("name", a.name).put("ssid", a.ssid).put("bssid", a.bssid)
                .put("model", a.model).put("band", a.band)
                .put("rssi", a.rssi?.toDouble() ?: JSONObject.NULL)
                .put("distance", a.distance?.toDouble() ?: JSONObject.NULL)
                .put("height", a.height.toDouble()).put("status", a.status).put("note", a.note)
                .put("area", a.area)
                .put("x", a.x?.toDouble() ?: JSONObject.NULL)
                .put("y", a.y?.toDouble() ?: JSONObject.NULL)
                .put("source", a.source).put("time", a.time)) }
        })
        o.put("paths", JSONArray().apply {
            paths.forEach { p -> put(JSONObject()
                .put("id", p.id).put("x", p.x.toDouble()).put("y", p.y.toDouble())
                .put("area", p.area).put("source", p.source).put("time", p.time)) }
        })
        o.put("settings", JSONObject()
            .put("north", settings.north.toDouble())
            .put("target_accuracy", settings.targetAccuracy.toDouble())
            .put("install_height", settings.installHeight.toDouble())
            .put("scale_locked", settings.scaleLocked))
        o.put("is_demo", isDemo)
        o.put("created_at", createdAt)
        o.put("updated_at", updatedAt)
        return o
    }

    companion object {
        fun nowIso(): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

        fun today(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        fun uid(): String =
            java.util.UUID.randomUUID().toString().replace("-", "").take(20)

        private fun optF(o: JSONObject, k: String): Float? =
            if (o.isNull(k)) null else o.getDouble(k).toFloat()

        fun fromJson(o: JSONObject): Project {
            val s = o.optJSONObject("settings") ?: JSONObject()
            fun pts(a: JSONArray?): ArrayList<Pt> {
                val r = ArrayList<Pt>()
                if (a != null) for (i in 0 until a.length()) {
                    val x = a.getJSONObject(i)
                    r.add(Pt(x.optString("id"), x.getDouble("x").toFloat(), x.getDouble("y").toFloat(),
                        x.optString("area", ""), x.optString("source", "دستی"), x.optString("time", "")))
                }
                return r
            }
            val floors = ArrayList<Floor>()
            val fArr = o.optJSONArray("floors")
            if (fArr != null) for (i in 0 until fArr.length()) {
                val f = fArr.getJSONObject(i)
                floors.add(Floor(f.optString("id"), f.optString("name", "ناحیه"), f.optBoolean("done", false)))
            }
            val walls = ArrayList<WallSeg>()
            val wArr = o.optJSONArray("walls")
            if (wArr != null) for (i in 0 until wArr.length()) {
                val w = wArr.getJSONObject(i)
                val ja = w.optJSONObject("a") ?: JSONObject()
                val jb = w.optJSONObject("b") ?: JSONObject()
                walls.add(WallSeg(w.optString("id"), Pt2(ja.getDouble("x").toFloat(), ja.getDouble("y").toFloat()),
                    Pt2(jb.getDouble("x").toFloat(), jb.getDouble("y").toFloat()),
                    w.optString("area", ""), w.optString("source", "دستی")))
            }
            val obstacles = ArrayList<Obstacle>()
            val oArr = o.optJSONArray("obstacles")
            if (oArr != null) for (i in 0 until oArr.length()) {
                val ob = oArr.getJSONObject(i)
                obstacles.add(Obstacle(ob.optString("id"), ob.getDouble("x").toFloat(), ob.getDouble("y").toFloat(),
                    ob.optString("kind", "مانع"), ob.optString("area", ""), ob.optString("source", "دستی")))
            }
            val aps = ArrayList<APRecord>()
            val aArr = o.optJSONArray("access_points")
            if (aArr != null) for (i in 0 until aArr.length()) {
                val a = aArr.getJSONObject(i)
                aps.add(APRecord(
                    a.optString("id"), a.optString("name", "AP"), a.optString("ssid", ""),
                    a.optString("bssid", ""), a.optString("model", ""), a.optString("band", ""),
                    optF(a, "rssi"), optF(a, "distance"),
                    a.optDouble("height", 2.7).toFloat(), a.optString("status", "موجود"),
                    a.optString("note", ""), a.optString("area", ""),
                    optF(a, "x"), optF(a, "y"),
                    a.optString("source", "دستی"), a.optString("time", "")
                ))
            }
            return Project(
                id = o.optString("id", uid()),
                projectName = o.optString("project_name", "پروژه"),
                organization = o.optString("organization", ""),
                site = o.optString("site", ""),
                building = o.optString("building", ""),
                description = o.optString("description", ""),
                surveyDate = o.optString("survey_date", today()),
                status = o.optString("status", "در حال برداشت"),
                unit = o.optString("unit", "متر"),
                ceilingHeight = o.optDouble("ceiling_height", 2.7).toFloat(),
                calibrationDistance = o.optDouble("calibration_distance", 0.0).toFloat(),
                calibrationConfidence = o.optDouble("calibration_confidence", 0.0).toFloat(),
                floors = floors, points = pts(o.optJSONArray("points")),
                walls = walls, obstacles = obstacles, accessPoints = aps,
                paths = pts(o.optJSONArray("paths")),
                settings = ProjSettings(
                    north = s.optDouble("north", 0.0).toFloat(),
                    targetAccuracy = s.optDouble("target_accuracy", 1.0).toFloat(),
                    installHeight = s.optDouble("install_height", 2.7).toFloat(),
                    scaleLocked = s.optBoolean("scale_locked", false)
                ),
                isDemo = o.optBoolean("is_demo", false),
                createdAt = o.optString("created_at", nowIso()),
                updatedAt = o.optString("updated_at", nowIso())
            )
        }

        fun create(name: String, org: String = "", site: String = "", building: String = "",
                   desc: String = "", date: String = today()): Project =
            Project(
                id = uid(), projectName = name, organization = org, site = site,
                building = building, description = desc, surveyDate = date,
                status = "در حال برداشت", unit = "متر", ceilingHeight = 2.7f,
                calibrationDistance = 0f, calibrationConfidence = 0f,
                floors = ArrayList(), points = ArrayList(), walls = ArrayList(),
                obstacles = ArrayList(), accessPoints = ArrayList(), paths = ArrayList(),
                settings = ProjSettings(), isDemo = false,
                createdAt = nowIso(), updatedAt = nowIso()
            )

        fun demo(): Project {
            val p = create(
                "DEMO · نمونه برداشت", "DEMO · سازمان نمونه", "DEMO · سایت آموزشی",
                "DEMO · ساختمان نمونه", "", today()
            )
            p.isDemo = true
            p.calibrationDistance = 5f
            p.calibrationConfidence = 0.55f
            val areaId = "demo-area"
            p.floors.add(Floor(areaId, "DEMO · ناحیه نمونه", false))
            val t = nowIso()
            p.points.add(Pt("d1", 2f, 2f, areaId, "دمو", t))
            p.points.add(Pt("d2", 7f, 2f, areaId, "دمو", t))
            p.points.add(Pt("d3", 7f, 6f, areaId, "دمو", t))
            p.walls.add(WallSeg("dw", Pt2(2f, 2f), Pt2(7f, 2f), areaId, "دمو"))
            p.obstacles.add(Obstacle("do", 4f, 4f, "مانع", areaId, "دمو"))
            p.accessPoints.add(
                APRecord("da", "DEMO · AP نمونه", "DEMO-NET", "", "UniFi · نمونه",
                    "۵ GHz", null, null, 2.7f, "پیشنهادی",
                    "داده آزمایشی؛ اندازه‌گیری واقعی نیست", areaId, 5f, 3f, "دمو", t)
            )
            p.paths.addAll(p.points)
            return p
        }
    }
}
