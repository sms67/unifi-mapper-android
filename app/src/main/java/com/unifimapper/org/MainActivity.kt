package com.unifimapper.org

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // ── views ────────────────────────────────────────────────
    private lateinit var txtNotice: TextView
    private lateinit var txtActiveLabel: TextView
    private lateinit var btnSettingsToggle: Button

    private lateinit var screenSettings: LinearLayout
    private lateinit var inpSettingOrg: EditText
    private lateinit var inpSettingHeight: EditText
    private lateinit var inpSettingNorth: EditText
    private lateinit var inpSettingAccuracy: EditText
    private lateinit var btnSaveSettings: Button
    private lateinit var btnDeleteProject: Button
    private lateinit var btnConfirmDelete: Button
    private lateinit var btnCancelDelete: Button
    private lateinit var txtPermissionSummary: TextView
    private lateinit var btnCameraPerm: Button

    private lateinit var screenProjects: LinearLayout
    private lateinit var btnOpenDemo: Button
    private lateinit var inpProjName: EditText
    private lateinit var inpOrg: EditText
    private lateinit var inpSite: EditText
    private lateinit var inpBuilding: EditText
    private lateinit var inpDescription: EditText
    private lateinit var inpSurveyDate: EditText
    private lateinit var btnCreateProject: Button
    private lateinit var listProjects: LinearLayout

    private lateinit var screenSurvey: LinearLayout
    private lateinit var txtSurveyBadge: TextView
    private lateinit var statusCards: LinearLayout
    private lateinit var inpAreaName: EditText
    private lateinit var btnAreaAdd: Button
    private lateinit var selAreaSelect: Spinner
    private lateinit var inpCeiling: EditText
    private lateinit var inpReference: EditText
    private lateinit var btnCalibrate: Button
    private lateinit var btnScaleLock: Button
    private lateinit var txtCalibrationState: TextView
    private lateinit var btnViewCamera: Button
    private lateinit var btnViewTop: Button
    private lateinit var btnViewPersp: Button
    private lateinit var btnCamStart: Button
    private lateinit var paneCamera: FrameLayout
    private lateinit var paneTop: FrameLayout
    private lateinit var panePersp: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var camHud: CamHud
    private lateinit var txtCameraMessage: TextView
    private lateinit var topSurvey: TopView
    private lateinit var perspSurvey: PerspectiveView
    private lateinit var btnScanToggle: Button
    private lateinit var btnAddPoint: Button
    private lateinit var btnAddWall: Button
    private lateinit var btnAddObstacle: Button
    private lateinit var btnAddRoom: Button
    private lateinit var btnUndoPoint: Button
    private lateinit var btnSaveSurvey: Button
    private lateinit var btnFinishArea: Button
    private lateinit var txtSurveyInstructions: TextView

    private lateinit var screenMap: LinearLayout
    private lateinit var selMapArea: Spinner
    private lateinit var topPlacement: TopView
    private lateinit var chkCoverage: CheckBox
    private lateinit var txtNearestDistance: TextView
    private lateinit var selPlacementAp: Spinner
    private lateinit var txtPlacementDetails: TextView

    private lateinit var screenAps: LinearLayout
    private lateinit var inpApName: EditText
    private lateinit var inpSsid: EditText
    private lateinit var inpBssid: EditText
    private lateinit var inpApModel: EditText
    private lateinit var selBand: Spinner
    private lateinit var inpRssi: EditText
    private lateinit var inpApHeight: EditText
    private lateinit var selApStatus: Spinner
    private lateinit var inpApNote: EditText
    private lateinit var btnApAdd: Button
    private lateinit var btnWifiScan: Button
    private lateinit var listWifi: LinearLayout
    private lateinit var inpApSearch: EditText
    private lateinit var selFilterBand: Spinner
    private lateinit var selFilterSignal: Spinner
    private lateinit var selFilterArea: Spinner
    private lateinit var listAps: LinearLayout

    private lateinit var screenExport: LinearLayout
    private lateinit var listQc: LinearLayout
    private lateinit var btnExportPng: Button
    private lateinit var btnExportObj: Button
    private lateinit var btnExportJson: Button
    private lateinit var topPreview: TopView
    private lateinit var perspPreview: PerspectiveView
    private lateinit var listExportAps: LinearLayout

    private lateinit var navProjects: Button
    private lateinit var navSurvey: Button
    private lateinit var navMap: Button
    private lateinit var navAps: Button
    private lateinit var navExport: Button

    // ── state ────────────────────────────────────────────────
    private var project: Project? = null
    private var page = "projects"
    private var viewMode = "camera"
    private var scanning = false
    private var settingsOpen = false
    private var cameraStatus = "درخواست نشده"
    private var selectedApId = ""
    private var dragApId: String? = null
    private var pendingSource = "دستی"
    private var floorIds: List<String> = emptyList()
    private var mapAreaId = ""
    private var spinnerGuard = false
    private val wifi = WifiEngine(this)
    private var noticeTimer: android.os.Handler? = null

    // ── launchers ────────────────────────────────────────────
    private val pngOut = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { u ->
        val p = project ?: return@registerForActivityResult
        u?.let {
            try {
                val bmp = Exporter.pngReport(topPreview.renderToBitmap(2), p)
                contentResolver.openOutputStream(it)?.use { os -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, os) }
                tell("تصویر نقشه ذخیره شد.")
            } catch (e: Exception) {
                tell("خروجی تصویر ناموفق بود: ${e.message}")
            }
        }
    }
    private val objOut = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { u ->
        val p = project ?: return@registerForActivityResult
        u?.let {
            try {
                saveText(Exporter.obj(p), it)
                tell("مدل OBJ ساخته شد؛ در 3ds Max از مسیر Import باز کنید.")
            } catch (e: Exception) {
                tell("خروجی OBJ ناموفق بود: ${e.message}")
            }
        }
    }
    private val jsonOut = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        val p = project ?: return@registerForActivityResult
        u?.let {
            try {
                saveText(Exporter.jsonReport(p).toString(2), it)
                tell("گزارش JSON ذخیره شد.")
            } catch (e: Exception) {
                tell("خروجی JSON ناموفق بود: ${e.message}")
            }
        }
    }
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val cam = res[Manifest.permission.CAMERA] == true
        val loc = res[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (cam) initCamera()
        if (loc && page == "aps") wifi.start()
        updatePermissionSummary()
        refreshStatusCards()
    }

    // ── lifecycle ────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        bindViews()
        inpSurveyDate.setText(Project.today())
        wireStaticSpinners()
        wireUi()
        renderProjects()
        show("projects")
    }

    public override fun onPause() {
        val p = project
        if (p != null && !p.isDemo) Store.save(this, p)
        super.onPause()
    }

    override fun onDestroy() {
        wifi.stop()
        super.onDestroy()
    }

    private fun bindViews() {
        txtNotice = findViewById(R.id.txtNotice)
        txtActiveLabel = findViewById(R.id.txtActiveLabel)
        btnSettingsToggle = findViewById(R.id.btnSettingsToggle)

        screenSettings = findViewById(R.id.screenSettings)
        inpSettingOrg = findViewById(R.id.inpSettingOrg)
        inpSettingHeight = findViewById(R.id.inpSettingHeight)
        inpSettingNorth = findViewById(R.id.inpSettingNorth)
        inpSettingAccuracy = findViewById(R.id.inpSettingAccuracy)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        btnDeleteProject = findViewById(R.id.btnDeleteProject)
        btnConfirmDelete = findViewById(R.id.btnConfirmDelete)
        btnCancelDelete = findViewById(R.id.btnCancelDelete)
        txtPermissionSummary = findViewById(R.id.txtPermissionSummary)
        btnCameraPerm = findViewById(R.id.btnCameraPerm)

        screenProjects = findViewById(R.id.screenProjects)
        btnOpenDemo = findViewById(R.id.btnOpenDemo)
        inpProjName = findViewById(R.id.inpProjName)
        inpOrg = findViewById(R.id.inpOrg)
        inpSite = findViewById(R.id.inpSite)
        inpBuilding = findViewById(R.id.inpBuilding)
        inpDescription = findViewById(R.id.inpDescription)
        inpSurveyDate = findViewById(R.id.inpSurveyDate)
        btnCreateProject = findViewById(R.id.btnCreateProject)
        listProjects = findViewById(R.id.listProjects)

        screenSurvey = findViewById(R.id.screenSurvey)
        txtSurveyBadge = findViewById(R.id.txtSurveyBadge)
        statusCards = findViewById(R.id.statusCards)
        inpAreaName = findViewById(R.id.inpAreaName)
        btnAreaAdd = findViewById(R.id.btnAreaAdd)
        selAreaSelect = findViewById(R.id.selAreaSelect)
        inpCeiling = findViewById(R.id.inpCeiling)
        inpReference = findViewById(R.id.inpReference)
        btnCalibrate = findViewById(R.id.btnCalibrate)
        btnScaleLock = findViewById(R.id.btnScaleLock)
        txtCalibrationState = findViewById(R.id.txtCalibrationState)
        btnViewCamera = findViewById(R.id.btnViewCamera)
        btnViewTop = findViewById(R.id.btnViewTop)
        btnViewPersp = findViewById(R.id.btnViewPersp)
        btnCamStart = findViewById(R.id.btnCamStart)
        paneCamera = findViewById(R.id.paneCamera)
        paneTop = findViewById(R.id.paneTop)
        panePersp = findViewById(R.id.panePersp)
        previewView = findViewById(R.id.previewView)
        camHud = findViewById(R.id.camHud)
        txtCameraMessage = findViewById(R.id.txtCameraMessage)
        topSurvey = findViewById(R.id.topSurvey)
        perspSurvey = findViewById(R.id.perspSurvey)
        btnScanToggle = findViewById(R.id.btnScanToggle)
        btnAddPoint = findViewById(R.id.btnAddPoint)
        btnAddWall = findViewById(R.id.btnAddWall)
        btnAddObstacle = findViewById(R.id.btnAddObstacle)
        btnAddRoom = findViewById(R.id.btnAddRoom)
        btnUndoPoint = findViewById(R.id.btnUndoPoint)
        btnSaveSurvey = findViewById(R.id.btnSaveSurvey)
        btnFinishArea = findViewById(R.id.btnFinishArea)
        txtSurveyInstructions = findViewById(R.id.txtSurveyInstructions)

        screenMap = findViewById(R.id.screenMap)
        selMapArea = findViewById(R.id.selMapArea)
        topPlacement = findViewById(R.id.topPlacement)
        chkCoverage = findViewById(R.id.chkCoverage)
        txtNearestDistance = findViewById(R.id.txtNearestDistance)
        selPlacementAp = findViewById(R.id.selPlacementAp)
        txtPlacementDetails = findViewById(R.id.txtPlacementDetails)

        screenAps = findViewById(R.id.screenAps)
        inpApName = findViewById(R.id.inpApName)
        inpSsid = findViewById(R.id.inpSsid)
        inpBssid = findViewById(R.id.inpBssid)
        inpApModel = findViewById(R.id.inpApModel)
        selBand = findViewById(R.id.selBand)
        inpRssi = findViewById(R.id.inpRssi)
        inpApHeight = findViewById(R.id.inpApHeight)
        selApStatus = findViewById(R.id.selApStatus)
        inpApNote = findViewById(R.id.inpApNote)
        btnApAdd = findViewById(R.id.btnApAdd)
        btnWifiScan = findViewById(R.id.btnWifiScan)
        listWifi = findViewById(R.id.listWifi)
        inpApSearch = findViewById(R.id.inpApSearch)
        selFilterBand = findViewById(R.id.selFilterBand)
        selFilterSignal = findViewById(R.id.selFilterSignal)
        selFilterArea = findViewById(R.id.selFilterArea)
        listAps = findViewById(R.id.listAps)

        screenExport = findViewById(R.id.screenExport)
        listQc = findViewById(R.id.listQc)
        btnExportPng = findViewById(R.id.btnExportPng)
        btnExportObj = findViewById(R.id.btnExportObj)
        btnExportJson = findViewById(R.id.btnExportJson)
        topPreview = findViewById(R.id.topPreview)
        perspPreview = findViewById(R.id.perspPreview)
        listExportAps = findViewById(R.id.listExportAps)

        navProjects = findViewById(R.id.btnNavProjects)
        navSurvey = findViewById(R.id.btnNavSurvey)
        navMap = findViewById(R.id.btnNavMap)
        navAps = findViewById(R.id.btnNavAps)
        navExport = findViewById(R.id.btnNavExport)
    }

    // ── helpers ──────────────────────────────────────────────
    private fun tell(s: String) {
        txtNotice.text = s
        txtNotice.visibility = View.VISIBLE
        if (noticeTimer == null) noticeTimer = android.os.Handler(mainLooper)
        noticeTimer?.removeCallbacksAndMessages(null)
        noticeTimer?.postDelayed({ txtNotice.visibility = View.GONE }, 6500)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun saveText(text: String, uri: android.net.Uri) {
        contentResolver.openOutputStream(uri)?.use { os -> os.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun currentAreaId(): String {
        val p = project ?: return ""
        if (floorIds.isEmpty()) return ""
        val pos = selAreaSelect.selectedItemPosition
        return if (pos in floorIds.indices) floorIds[pos] else floorIds.firstOrNull() ?: ""
    }

    private fun setSpinner(s: Spinner, items: List<String>, selPos: Int, onSel: (Int) -> Unit) {
        val ad = ArrayAdapter(this, R.layout.spinner_item, items)
        ad.setDropDownViewResource(R.layout.spinner_drop)
        s.adapter = ad
        spinnerGuard = true
        if (selPos in items.indices) s.setSelection(selPos, false)
        spinnerGuard = false
        s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (!spinnerGuard) onSel(pos)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ── navigation ───────────────────────────────────────────
    private fun show(name: String) {
        if (name != "projects" && project == null) {
            tell("ابتدا پروژه‌ای بسازید یا پروژه نمونه را باز کنید.")
            return
        }
        page = name
        screenProjects.visibility = if (name == "projects") View.VISIBLE else View.GONE
        screenSurvey.visibility = if (name == "survey") View.VISIBLE else View.GONE
        screenMap.visibility = if (name == "map") View.VISIBLE else View.GONE
        screenAps.visibility = if (name == "aps") View.VISIBLE else View.GONE
        screenExport.visibility = if (name == "export") View.VISIBLE else View.GONE
        screenSettings.visibility = if (settingsOpen) View.VISIBLE else View.GONE
        markNav()
        if (name == "aps") {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                wifi.start()
            }
        } else {
            wifi.stop()
        }
        if (name != "survey" && viewMode == "camera" && paneCamera.visibility == View.VISIBLE) {
            // keep camera bound only while survey camera pane is visible
        }
        refresh()
        if (name == "survey") setView(viewMode)
        mainScroll()
    }

    private fun mainScroll() {
        findViewById<android.widget.ScrollView>(R.id.mainScroll).smoothScrollTo(0, 0)
    }

    private fun markNav() {
        val activeBg = ContextCompat.getDrawable(this, R.drawable.bg_btn_accent)
        val idleBg = ContextCompat.getDrawable(this, R.drawable.bg_btn)
        val activeFg = ContextCompat.getColor(this, R.color.navActive)
        val idleFg = ContextCompat.getColor(this, R.color.navIdle)
        val map = mapOf(
            "projects" to navProjects, "survey" to navSurvey,
            "map" to navMap, "aps" to navAps, "export" to navExport
        )
        for ((k, b) in map) {
            b.background = if (k == page) activeBg else idleBg
            b.setTextColor(if (k == page) activeFg else idleFg)
        }
    }

    private fun setView(v: String) {
        viewMode = v
        paneCamera.visibility = if (v == "camera") View.VISIBLE else View.GONE
        paneTop.visibility = if (v == "top") View.VISIBLE else View.GONE
        panePersp.visibility = if (v == "persp") View.VISIBLE else View.GONE
        btnCamStart.visibility = if (v == "camera") View.VISIBLE else View.GONE
        val accent = ContextCompat.getDrawable(this, R.drawable.bg_btn_accent)
        val idle = ContextCompat.getDrawable(this, R.drawable.bg_btn)
        val af = ContextCompat.getColor(this, R.color.navActive)
        val ifg = ContextCompat.getColor(this, R.color.navIdle)
        btnViewCamera.background = if (v == "camera") accent else idle
        btnViewCamera.setTextColor(if (v == "camera") af else ifg)
        btnViewTop.background = if (v == "top") accent else idle
        btnViewTop.setTextColor(if (v == "top") af else ifg)
        btnViewPersp.background = if (v == "persp") accent else idle
        btnViewPersp.setTextColor(if (v == "persp") af else ifg)
        drawAll()
    }

    // ── refresh ──────────────────────────────────────────────
    private fun refresh() {
        val p = project
        if (p == null) {
            txtActiveLabel.text = "پروژه‌ای انتخاب نشده"
            return
        }
        txtActiveLabel.text = (if (p.isDemo) "DEMO · " else "") + p.projectName
        txtSurveyBadge.text = if (p.isDemo) "DEMO · داده آزمایشی" else p.status

        // floor spinners
        floorIds = p.floors.map { it.id }
        val names = p.floors.map { it.name }
        val curPos = floorIds.indexOf(currentAreaId()).coerceAtLeast(0)
        setSpinner(selAreaSelect, names.ifEmpty { listOf("ناحیه‌ای ثبت نشده") }, curPos) { _ ->
            drawAll()
        }
        val mapPos = floorIds.indexOf(mapAreaId).coerceAtLeast(0)
        setSpinner(selMapArea, names.ifEmpty { listOf("ناحیه‌ای ثبت نشده") }, mapPos) { pos ->
            mapAreaId = floorIds.getOrNull(pos) ?: ""
            drawPlacement()
        }
        val faItems = listOf("همه نواحی") + names
        val faPos = selFilterArea.selectedItemPosition.coerceAtLeast(0)
        setSpinner(selFilterArea, faItems, faPos.coerceIn(0, faItems.size - 1)) { _ -> renderAps() }

        if (inpCeiling.text.isNullOrBlank()) inpCeiling.setText(fmt(p.ceilingHeight))
        txtCalibrationState.text = if (p.calibrationDistance > 0f)
            "مرجع: ${fmt(p.calibrationDistance)} متر · اطمینان: ${(p.calibrationConfidence * 100).toInt()}٪ · " +
            (if (p.settings.scaleLocked) "مقیاس قفل است" else "مقیاس باز است")
        else "مقیاس هنوز کالیبره نشده است."
        btnScaleLock.text = if (p.settings.scaleLocked) "باز کردن قفل مقیاس" else "قفل مقیاس"
        btnScanToggle.text = if (scanning) "توقف برداشت دستی" else "شروع برداشت دستی"

        refreshStatusCards()

        // placement AP spinner
        val apNames = ArrayList<String>()
        apNames.add("AP را انتخاب کنید")
        p.accessPoints.forEach { apNames.add(it.name + if (it.source == "دمو") " · DEMO" else "") }
        val ids = ArrayList<String>()
        ids.add("")
        p.accessPoints.forEach { ids.add(it.id) }
        val selApPos = ids.indexOf(selectedApId).coerceAtLeast(0)
        setSpinner(selPlacementAp, apNames, selApPos) { pos ->
            selectedApId = ids.getOrNull(pos) ?: ""
            updatePlacementDetails()
        }
        updatePlacementDetails()

        renderAps()
        renderQc()
        renderExportAps()
        drawAll()
    }

    private fun refreshStatusCards() {
        val p = project
        statusCards.removeAllViews()
        if (p == null) return
        val rows = listOf(
            "دوربین" to cameraStatus,
            "حسگر حرکت" to "ردیابی خودکار فعال نیست · ثبت دستی",
            "مقیاس" to (if (p.calibrationDistance > 0f) fmt(p.calibrationDistance) + " متر · تقریبی" else "کالیبره نشده"),
            "نقاط" to p.points.size.toString(),
            "AP" to p.accessPoints.size.toString(),
            "ذخیره" to (if (p.isDemo) "DEMO · ذخیره نمی‌شود" else "خودکار + دکمه ذخیره")
        )
        val line = LinearLayout(this)
        line.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        for ((i, r) in rows.withIndex()) {
            val cell = TextView(this)
            cell.setPadding(dp(8), dp(8), dp(8), dp(8))
            cell.setBackgroundResource(R.drawable.bg_soft)
            cell.textSize = 11f
            cell.setTextColor(ContextCompat.getColor(this, R.color.ink))
            cell.text = r.first + "\n" + r.second
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.marginStart = dp(6)
            lp.bottomMargin = dp(6)
            cell.layoutParams = lp
            line.addView(cell)
        }
        statusCards.addView(line)
    }

    private fun updatePlacementDetails() {
        val p = project ?: return
        val a = p.accessPoints.firstOrNull { it.id == selectedApId }
        txtPlacementDetails.text = if (a != null)
            "${a.name} · ${a.model.ifEmpty { "مدل نامشخص" }} · ${a.status} · " +
            (if (a.x == null) "بدون موقعیت" else "(${fmt(a.x!!)}، ${fmt(a.y!!)}) متر") + " · ${a.source}"
        else "برای جانمایی، ابتدا یک AP را انتخاب کنید."
        txtNearestDistance.text = nearestText(a)
    }

    private fun nearestText(a: APRecord?): String {
        val p = project ?: return ""
        if (a == null || a.x == null) return "پس از جانمایی، فاصله تا نزدیک‌ترین نقطه مرجع نمایش داده می‌شود."
        val pts = p.points.filter { it.area == a.area }
        if (pts.isEmpty()) return "نقطه مرجعی در این ناحیه ثبت نشده است."
        var d = Float.MAX_VALUE
        for (pt in pts) {
            val dd = kotlin.math.hypot((pt.x - a.x!!).toDouble(), (pt.y - a.y!!).toDouble()).toFloat()
            if (dd < d) d = dd
        }
        return "فاصله هندسی تا نزدیک‌ترین نقطه ثبت‌شده: ${fmt(d)} متر · تقریبی، وابسته به کالیبراسیون دستی."
    }

    // ── feed views ───────────────────────────────────────────
    private fun drawAll() {
        val p = project ?: return
        val areaId = currentAreaId()
        feedTop(topSurvey, p, areaId, false)
        feedTop(topPreview, p, "", false)
        feedTop(topPlacement, p, mapAreaId, chkCoverage.isChecked)
        drawPlacement()
        perspSurvey.points = topSurvey.points
        perspSurvey.walls = topSurvey.walls
        perspSurvey.obstacles = topSurvey.obstacles
        perspSurvey.aps = topSurvey.aps
        perspSurvey.ceilingHeight = p.ceilingHeight
        perspSurvey.invalidate()
        perspPreview.points = topPreview.points
        perspPreview.walls = topPreview.walls
        perspPreview.obstacles = topPreview.obstacles
        perspPreview.aps = topPreview.aps
        perspPreview.ceilingHeight = p.ceilingHeight
        perspPreview.invalidate()
    }

    private fun drawPlacement() {
        val p = project ?: return
        feedTop(topPlacement, p, mapAreaId, chkCoverage.isChecked)
        topPlacement.invalidate()
    }

    private fun feedTop(v: TopView, p: Project, areaId: String, coverage: Boolean) {
        v.points = p.points.filter { areaId.isEmpty() || it.area == areaId }
        v.walls = p.walls.filter { areaId.isEmpty() || it.area == areaId }
        v.obstacles = p.obstacles.filter { areaId.isEmpty() || it.area == areaId }
        v.aps = p.accessPoints.filter { areaId.isEmpty() || it.area == areaId }
        v.coverage = coverage
        v.invalidate()
    }

    private fun fmt(v: Float): String = String.format(Locale.US, "%.1f", v)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ── static spinners + wiring ─────────────────────────────
    private fun wireStaticSpinners() {
        setSpinner(selBand, listOf("نامشخص", "۲٫۴ GHz", "۵ GHz", "۶ GHz"), 0) { _ -> renderAps() }
        setSpinner(selApStatus, listOf("موجود", "پیشنهادی"), 0) { }
        setSpinner(selFilterBand, listOf("همه باندها", "۲٫۴ GHz", "۵ GHz", "۶ GHz"), 0) { _ -> renderAps() }
        setSpinner(selFilterSignal, listOf("همه", "قوی (≥ −۶۵ dBm)", "ضعیف (< −۶۵ dBm)", "نامشخص"), 0) { _ -> renderAps() }
    }

    private fun wireUi() {
        // nav
        navProjects.setOnClickListener { show("projects") }
        navSurvey.setOnClickListener { show("survey") }
        navMap.setOnClickListener { show("map") }
        navAps.setOnClickListener { show("aps") }
        navExport.setOnClickListener { show("export") }

        // header settings toggle
        btnSettingsToggle.setOnClickListener {
            if (project == null) { tell("ابتدا یک پروژه انتخاب کنید."); return@setOnClickListener }
            settingsOpen = !settingsOpen
            screenSettings.visibility = if (settingsOpen) View.VISIBLE else View.GONE
            if (settingsOpen) {
                val p = project!!
                inpSettingOrg.setText(p.organization)
                inpSettingHeight.setText(fmt(p.settings.installHeight))
                inpSettingNorth.setText(fmt(p.settings.north))
                inpSettingAccuracy.setText(fmt(p.settings.targetAccuracy))
                btnConfirmDelete.visibility = View.GONE
                btnCancelDelete.visibility = View.GONE
            }
            mainScroll()
        }

        // settings
        btnCameraPerm.setOnClickListener { requestPerms() }
        btnSaveSettings.setOnClickListener {
            val p = project ?: return@setOnClickListener
            p.organization = inpSettingOrg.text.toString().trim()
            p.settings.installHeight = inpSettingHeight.text.toString().toFloatOrNull() ?: 2.7f
            p.settings.north = inpSettingNorth.text.toString().toFloatOrNull() ?: 0f
            p.settings.targetAccuracy = inpSettingAccuracy.text.toString().toFloatOrNull() ?: 1f
            persist()
        }
        btnDeleteProject.setOnClickListener {
            btnConfirmDelete.visibility = View.VISIBLE
            btnCancelDelete.visibility = View.VISIBLE
            tell("برای پاک‌کردن کامل این پروژه، دکمه تأیید حذف را بزنید.")
        }
        btnCancelDelete.setOnClickListener {
            btnConfirmDelete.visibility = View.GONE
            btnCancelDelete.visibility = View.GONE
        }
        btnConfirmDelete.setOnClickListener {
            val p = project
            if (p == null) return@setOnClickListener
            if (p.isDemo) {
                tell("پروژه نمونه بسته شد.")
            } else {
                Store.delete(this, p.id)
                tell("پروژه پاک شد.")
            }
            project = null
            settingsOpen = false
            renderProjects()
            show("projects")
        }

        // projects
        btnOpenDemo.setOnClickListener {
            project = Project.demo()
            selectedApId = ""
            settingsOpen = false
            renderProjects()
            refresh()
            show("survey")
            tell("DEMO · همه اطلاعات این پروژه نمونه‌اند و اندازه‌گیری واقعی نیستند.")
        }
        btnCreateProject.setOnClickListener {
            val name = inpProjName.text.toString().trim()
            val org = inpOrg.text.toString().trim()
            if (name.isEmpty() || org.isEmpty()) { tell("نام پروژه و سازمان الزامی است."); return@setOnClickListener }
            val p = Project.create(
                name, org,
                inpSite.text.toString().trim(),
                inpBuilding.text.toString().trim(),
                inpDescription.text.toString().trim(),
                inpSurveyDate.text.toString().ifEmpty { Project.today() }
            )
            Store.save(this, p)
            renderProjects()
            inpProjName.setText("")
            inpSite.setText("")
            inpBuilding.setText("")
            inpDescription.setText("")
            tell("پروژه ساخته شد؛ آن را از فهرست باز کنید.")
        }
        inpSurveyDate.setOnClickListener { pickDate() }

        // survey
        btnScanToggle.setOnClickListener {
            scanning = !scanning
            btnScanToggle.text = if (scanning) "توقف برداشت دستی" else "شروع برداشت دستی"
            tell(if (scanning) "برداشت دستی فعال است؛ لمس نمای بالا نقطه ثبت می‌کند."
                 else "برداشت دستی متوقف شد.")
        }
        btnViewCamera.setOnClickListener { setView("camera") }
        btnViewTop.setOnClickListener { setView("top") }
        btnViewPersp.setOnClickListener { setView("persp") }
        btnCamStart.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                initCamera()
            } else {
                requestPerms()
            }
        }
        btnAreaAdd.setOnClickListener {
            val p = project ?: return@setOnClickListener
            val name = inpAreaName.text.toString().trim()
            if (name.isEmpty()) { tell("نام ناحیه را وارد کنید."); return@setOnClickListener }
            val id = Project.uid()
            p.floors.add(Floor(id, name, false))
            inpAreaName.setText("")
            if (mapAreaId.isEmpty()) mapAreaId = id
            persistQuiet()
            refresh()
            tell("ناحیه افزوده شد.")
        }
        inpCeiling.doAfterTextChanged {
            val p = project ?: return@doAfterTextChanged
            val v = it?.toString()?.toFloatOrNull()
            if (v != null && v > 0f) p.ceilingHeight = v
        }
        btnCalibrate.setOnClickListener {
            val p = project ?: return@setOnClickListener
            if (p.settings.scaleLocked) { tell("ابتدا قفل مقیاس را باز کنید."); return@setOnClickListener }
            val n = inpReference.text.toString().toFloatOrNull()
            if (n == null || n <= 0f) { tell("طول مرجع معتبر و بزرگ‌تر از صفر وارد کنید."); return@setOnClickListener }
            p.calibrationDistance = n
            p.calibrationConfidence = 0.55f
            persistQuiet()
            refresh()
            tell("مقیاس با طول واردشده ثبت شد؛ اطمینان متوسط است و نیاز به کنترل میدانی دارد.")
        }
        btnScaleLock.setOnClickListener {
            val p = project ?: return@setOnClickListener
            p.settings.scaleLocked = !p.settings.scaleLocked
            persistQuiet()
            refresh()
        }
        btnAddPoint.setOnClickListener {
            val p = project ?: return@setOnClickListener
            val areaId = currentAreaId()
            if (areaId.isEmpty()) { tell("ابتدا طبقه یا ناحیه بسازید."); return@setOnClickListener }
            val prev = p.lastPointIn(areaId)
            addPoint(
                (minOf(20f, (prev?.x ?: 1f) + 1f)),
                prev?.y ?: 2f
            )
            tell("نقطه دستی تقریبی افزوده شد؛ موقعیت را روی نقشه بررسی کنید.")
        }
        btnAddWall.setOnClickListener {
            val p = project ?: return@setOnClickListener
            val pts = p.pointsIn(currentAreaId())
            if (pts.size < 2) { tell("برای ثبت دیوار ابتدا دست‌کم دو نقطه ثبت کنید."); return@setOnClickListener }
            val a = pts[pts.size - 2]
            val b = pts[pts.size - 1]
            p.walls.add(WallSeg(Project.uid(), Pt2(a.x, a.y), Pt2(b.x, b.y), a.area,
                if (p.isDemo) "دمو" else "دستی"))
            persistQuiet()
            refresh()
            tell("دیوار میان دو نقطه آخر ثبت شد.")
        }
        btnAddObstacle.setOnClickListener { addObstacle("مانع") }
        btnAddRoom.setOnClickListener { addObstacle("اتاق") }
        btnUndoPoint.setOnClickListener {
            val p = project ?: return@setOnClickListener
            val pts = p.pointsIn(currentAreaId())
            val last = pts.lastOrNull()
            if (last == null) { tell("نقطه‌ای برای حذف وجود ندارد."); return@setOnClickListener }
            p.points.remove(last)
            val pi = p.paths.indexOfFirst { it.id == last.id }
            if (pi >= 0) p.paths.removeAt(pi)
            persistQuiet()
            refresh()
            tell("آخرین نقطه حذف شد.")
        }
        btnSaveSurvey.setOnClickListener { persist() }
        btnFinishArea.setOnClickListener {
            val p = project ?: return@setOnClickListener
            val areaId = currentAreaId()
            val f = p.floors.firstOrNull { it.id == areaId }
            if (f == null) { tell("ابتدا ناحیه‌ای انتخاب کنید."); return@setOnClickListener }
            f.done = true
            p.status = "نیازمند بازبینی"
            persistQuiet()
            refresh()
            tell("ناحیه پایان یافت؛ برای ماندگاری، برداشت را ذخیره کنید.")
        }

        // survey map tap
        topSurvey.onTap = { x, y ->
            if (!scanning) tell("ابتدا «شروع برداشت دستی» را بزنید.")
            else addPoint(x, y)
        }

        // placement
        topPlacement.panEnabled = false
        topPlacement.onTouchDown = { x, y -> placementTouch(x, y) }
        topPlacement.onTouchMove = { x, y ->
            val p = project
            val id = dragApId
            if (p != null && id != null) {
                val a = p.accessPoints.firstOrNull { it.id == id }
                if (a != null) {
                    a.x = x; a.y = y
                    topPlacement.invalidate()
                }
            }
        }
        topPlacement.onTouchUp = {
            if (dragApId != null) {
                dragApId = null
                persistQuiet()
                updatePlacementDetails()
                tell("موقعیت دستی AP تغییر کرد؛ برای ماندگاری پروژه را ذخیره کنید.")
            }
        }
        chkCoverage.setOnCheckedChangeListener { _, checked ->
            topPlacement.coverage = checked
            topPlacement.invalidate()
        }

        // AP form
        btnApAdd.setOnClickListener { addAp() }
        inpApSearch.doAfterTextChanged { renderAps() }
        btnWifiScan.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                tell("برای اسکن وای‌فای، مجوز موقعیت لازم است.")
                requestPerms()
                return@setOnClickListener
            }
            wifi.listener = WifiEngine.Listener { list -> renderWifi(list) }
            wifi.start()
            tell("اسکن وای‌فای فعال شد؛ نتایج هر چند ثانیه به‌روز می‌شود.")
        }

        // exports
        btnExportPng.setOnClickListener {
            if (project == null) return@setOnClickListener
            pngOut.launch("network-map.png")
        }
        btnExportObj.setOnClickListener {
            if (project == null) return@setOnClickListener
            objOut.launch("approximate-network-model.obj")
        }
        btnExportJson.setOnClickListener {
            if (project == null) return@setOnClickListener
            jsonOut.launch("network-survey-report.json")
        }
    }

    // ── survey ops ───────────────────────────────────────────
    private fun addPoint(x: Float, y: Float) {
        val p = project ?: return
        val areaId = currentAreaId()
        if (areaId.isEmpty()) { tell("ابتدا طبقه یا ناحیه بسازید."); return }
        val source = if (p.isDemo) "دمو" else "دستی"
        val pt = Pt(Project.uid(),
            x.coerceIn(0f, 20f), y.coerceIn(0f, 14f), areaId, source, Project.nowIso())
        p.points.add(pt)
        p.paths.add(pt)
        persistQuiet()
        refresh()
    }

    private fun addObstacle(kind: String) {
        val p = project ?: return
        val last = p.lastPointIn(currentAreaId())
        if (last == null) { tell("ابتدا یک نقطه مرجع ثبت کنید."); return }
        p.obstacles.add(Obstacle(Project.uid(), last.x, last.y, kind, last.area,
            if (p.isDemo) "دمو" else "دستی"))
        persistQuiet()
        refresh()
        tell(kind + " در کنار آخرین نقطه ثبت شد.")
    }

    private fun placementTouch(x: Float, y: Float) {
        val p = project ?: return
        if (selectedApId.isEmpty()) { tell("ابتدا از پایین صفحه یک AP انتخاب کنید."); return }
        val near = p.accessPoints.firstOrNull { a ->
            a.area == mapAreaId && a.x != null && a.y != null &&
                kotlin.math.hypot((a.x!! - x).toDouble(), (a.y!! - y).toDouble()) < 0.65
        }
        dragApId = near?.id ?: selectedApId
        val a = p.accessPoints.firstOrNull { it.id == dragApId }
        if (a != null) {
            a.area = mapAreaId
            a.x = x.coerceIn(0f, 20f)
            a.y = y.coerceIn(0f, 14f)
            topPlacement.invalidate()
            updatePlacementDetails()
        }
    }

    // ── AP list / wifi ───────────────────────────────────────
    private fun addAp() {
        val p = project ?: return
        if (p.floors.isEmpty()) { tell("ابتدا یک ناحیه بسازید."); return }
        val name = inpApName.text.toString().trim()
        if (name.isEmpty()) { tell("نام AP الزامی است."); return }
        val rssi = inpRssi.text.toString().toFloatOrNull()
        val areaId = currentAreaId().ifEmpty { p.floors[0].id }
        p.accessPoints.add(APRecord(
            id = Project.uid(),
            name = name,
            ssid = inpSsid.text.toString().trim(),
            bssid = inpBssid.text.toString().trim(),
            model = inpApModel.text.toString().trim(),
            band = if (selBand.selectedItemPosition > 0) selBand.selectedItem.toString() else "",
            rssi = rssi,
            distance = null,
            height = inpApHeight.text.toString().toFloatOrNull() ?: 2.7f,
            status = selApStatus.selectedItem?.toString() ?: "موجود",
            note = inpApNote.text.toString().trim(),
            area = areaId,
            x = null, y = null,
            source = pendingSource,
            time = Project.nowIso()
        ))
        inpApName.setText(""); inpSsid.setText(""); inpBssid.setText("")
        inpApModel.setText(""); inpRssi.setText(""); inpApNote.setText("")
        pendingSource = "دستی"
        persistQuiet()
        refresh()
        tell("AP افزوده شد. برای جانمایی به بخش نقشه بروید.")
    }

    private fun renderAps() {
        val p = project ?: run { listAps.removeAllViews(); return }
        val q = inpApSearch.text.toString().trim().lowercase(Locale.US)
        val band = if (selFilterBand.selectedItemPosition > 0) selFilterBand.selectedItem.toString() else ""
        val sigPos = selFilterSignal.selectedItemPosition
        val filterAreaPos = selFilterArea.selectedItemPosition
        val filterAreaId = if (filterAreaPos > 0) floorIds.getOrNull(filterAreaPos - 1) ?: "" else ""
        val arr = p.accessPoints.filter { a ->
            val matchQ = q.isEmpty() || listOf(a.name, a.ssid, a.model).any {
                (it ?: "").lowercase(Locale.US).contains(q)
            }
            val matchBand = band.isEmpty() || a.band == band
            val matchArea = filterAreaId.isEmpty() || a.area == filterAreaId
            val matchSig = when (sigPos) {
                1 -> a.rssi != null && a.rssi >= -65f
                2 -> a.rssi != null && a.rssi < -65f
                3 -> a.rssi == null
                else -> true
            }
            matchQ && matchBand && matchArea && matchSig
        }
        listAps.removeAllViews()
        if (arr.isEmpty()) {
            listAps.addView(smallText("اکسس‌پوینتی مطابق فیلتر یافت نشد."))
            return
        }
        for (a in arr) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(0, dp(10), 0, dp(10))
            val title = TextView(this)
            title.text = a.name + "  [" + a.source + "]"
            title.textSize = 14f
            title.setTypeface(null, android.graphics.Typeface.BOLD)
            title.setTextColor(ContextCompat.getColor(this, R.color.ink))
            row.addView(title)
            row.addView(smallText(
                "SSID: " + a.ssid.ifEmpty { "نامشخص" } + " · BSSID: " + a.bssid.ifEmpty { "در دسترس نیست" } + "\n" +
                "باند: " + a.band.ifEmpty { "نامشخص" } + " · RSSI: " + (a.rssi?.let { "${it.toInt()} dBm" } ?: "اندازه‌گیری نشده") +
                " · ارتفاع: " + fmt(a.height) + " م\n" +
                p.areaName(a.area) + " · " + a.status
            ))
            val del = Button(this)
            del.setText("حذف AP")
            del.setBackgroundResource(R.drawable.bg_btn_danger)
            del.setTextColor(ContextCompat.getColor(this, R.color.danger))
            del.textSize = 12f
            del.setPadding(dp(12), dp(6), dp(12), dp(6))
            del.setOnClickListener {
                p.accessPoints.remove(a)
                if (selectedApId == a.id) selectedApId = ""
                persistQuiet()
                refresh()
                tell("AP حذف شد.")
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(6)
            del.layoutParams = lp
            row.addView(del)
            listAps.addView(row)
        }
    }

    private fun renderWifi(list: List<WifiEngine.ScanRes>) {
        listWifi.removeAllViews()
        if (list.isEmpty()) {
            listWifi.addView(smallText("شبکه‌ای یافت نشد؛ چند لحظه صبر کنید."))
            return
        }
        for (r in list.take(12)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(6), 0, dp(6))
            val txt = TextView(this)
            txt.textSize = 12.5f
            txt.setTextColor(ContextCompat.getColor(this, R.color.ink))
            txt.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            txt.text = (r.ssid.ifEmpty { "(شبکه مخفی)" }) + "\n" + r.bssid + " · " + r.rssi + " dBm · " + r.bandLabel()
            row.addView(txt)
            val use = Button(this)
            use.setText("انتخاب")
            use.setBackgroundResource(R.drawable.bg_btn)
            use.setTextColor(ContextCompat.getColor(this, R.color.ink))
            use.textSize = 12f
            use.setPadding(dp(12), dp(6), dp(12), dp(6))
            use.setOnClickListener { prefillAp(r) }
            row.addView(use)
            listWifi.addView(row)
        }
    }

    private fun prefillAp(r: WifiEngine.ScanRes) {
        inpApName.setText(r.ssid.ifEmpty { "AP-" + r.bssid.takeLast(5).replace(":", "") })
        inpSsid.setText(r.ssid)
        inpBssid.setText(r.bssid)
        inpRssi.setText(r.rssi.toString())
        when (r.bandLabel()) {
            "۲٫۴ GHz" -> selBand.setSelection(1, true)
            "۵ GHz" -> selBand.setSelection(2, true)
            "۶ GHz" -> selBand.setSelection(3, true)
        }
        inpApHeight.setText(fmt(project?.settings?.installHeight ?: 2.7f))
        pendingSource = "اسکن"
        tell("فرم AP از نتیجه اسکن پر شد؛ مدل و یادداشت را تکمیل و ثبت کنید.")
    }

    // ── QC / export lists ────────────────────────────────────
    private fun renderQc() {
        val p = project ?: run { listQc.removeAllViews(); return }
        listQc.removeAllViews()
        val q = p.quality()
        if (q.isEmpty()) {
            listQc.addView(smallText("هشدار ثبت‌شده‌ای نیست؛ کنترل میدانی همچنان لازم است."))
            return
        }
        for (item in q) {
            val t = TextView(this)
            t.textSize = 12f
            t.setTextColor(ContextCompat.getColor(this, R.color.danger))
            t.text = item.text + "  اقدام: " + item.action
            t.setPadding(dp(8), dp(6), dp(8), dp(6))
            t.setBackgroundResource(R.drawable.bg_soft)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(6)
            t.layoutParams = lp
            listQc.addView(t)
        }
    }

    private fun renderExportAps() {
        val p = project ?: run { listExportAps.removeAllViews(); return }
        listExportAps.removeAllViews()
        if (p.accessPoints.isEmpty()) {
            listExportAps.addView(smallText("AP ثبت نشده است."))
            return
        }
        for (a in p.accessPoints) {
            listExportAps.addView(smallText(
                a.name + " | " + a.source + " | RSSI: " + (a.rssi?.let { it.toInt().toString() } ?: "نامشخص") +
                " | " + p.areaName(a.area) + " | موقعیت: " +
                (if (a.x == null) "ثبت نشده" else fmt(a.x!!) + ", " + fmt(a.y!!) + " متر")
            ))
        }
    }

    // ── projects list ────────────────────────────────────────
    private fun renderProjects() {
        listProjects.removeAllViews()
        val all = Store.list(this)
        if (all.isEmpty()) {
            listProjects.addView(smallText("هنوز پروژه واقعی ثبت نشده است."))
            return
        }
        for (p in all) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(10), 0, dp(10))
            val txt = TextView(this)
            txt.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            txt.textSize = 13.5f
            txt.setTextColor(ContextCompat.getColor(this, R.color.ink))
            txt.text = p.projectName + "\n" + p.organization.ifEmpty { "—" } + " · " +
                p.building.ifEmpty { p.site.ifEmpty { "بدون ساختمان" } } + " · " + p.status
            row.addView(txt)
            val open = Button(this)
            open.setText("ادامه پروژه")
            open.setBackgroundResource(R.drawable.bg_btn)
            open.setTextColor(ContextCompat.getColor(this, R.color.ink))
            open.textSize = 12f
            open.setPadding(dp(12), dp(6), dp(12), dp(6))
            open.setOnClickListener {
                project = Store.load(this, p.id) ?: p
                selectedApId = ""
                mapAreaId = project?.floors?.firstOrNull()?.id ?: ""
                settingsOpen = false
                refresh()
                show("survey")
            }
            row.addView(open)
            listProjects.addView(row)
        }
    }

    private fun smallText(s: String): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = 12f
        t.setTextColor(ContextCompat.getColor(this, R.color.muted))
        t.setPadding(0, dp(4), 0, dp(4))
        return t
    }

    // ── date picker ──────────────────────────────────────────
    private fun pickDate() {
        val cal = Calendar.getInstance()
        try {
            val cur = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(inpSurveyDate.text.toString())
            if (cur != null) cal.time = cur
        } catch (_: Exception) {
        }
        android.app.DatePickerDialog(this, { _, y, m, d ->
            inpSurveyDate.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    // ── permissions + camera ─────────────────────────────────
    private fun requestPerms() {
        permLauncher.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    private fun updatePermissionSummary() {
        val cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val loc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        txtPermissionSummary.text =
            "دوربین: " + (if (cam) "مجاز" else "درخواست نشده") +
            " · اسکن وای‌فای: " + (if (loc) "مجاز" else "نیازمند مجوز موقعیت")
    }

    private fun initCamera() {
        try {
            val future = ProcessCameraProvider.getInstance(this)
            future.addListener({
                try {
                    val provider = future.get()
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    provider.unbindAll()
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                    cameraStatus = "فعال · فقط تصویر زنده، بدون تشخیص سطح"
                    txtCameraMessage.text = "تصویر زنده دوربین · تشخیص سطح و ردیابی خودکار در دسترس نیست."
                } catch (e: Exception) {
                    cameraStatus = "خطای دوربین"
                    txtCameraMessage.text = "دسترسی دوربین برقرار نشد: ${e.message}"
                }
                camHud.lines = listOf("● شروع مسیر · شبکه راهنما")
                refreshStatusCards()
                updatePermissionSummary()
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            cameraStatus = "در دسترس نیست"
            txtCameraMessage.text = "دوربین در این دستگاه در دسترس نیست: ${e.message}"
        }
    }

    // ── persistence ──────────────────────────────────────────
    private fun persist() {
        val p = project ?: return
        if (p.isDemo) {
            tell("DEMO · داده نمونه ذخیره نمی‌شود. برای کار واقعی پروژه جدید بسازید.")
            return
        }
        p.updatedAt = Project.nowIso()
        Store.save(this, p)
        renderProjects()
        tell("پروژه ذخیره شد.")
    }

    private fun persistQuiet() {
        val p = project ?: return
        if (p.isDemo) return
        p.updatedAt = Project.nowIso()
        Store.save(this, p)
    }
}



