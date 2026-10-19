package com.unifimapper.org

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Real Wi-Fi scanning (WifiManager). Every tick delivers the cached scan list.
 * Results prefill the manual AP form: SSID, BSSID, RSSI and band — the surveyor
 * stays in control and confirms every registration.
 */
class WifiEngine(private val ctx: Context) {

    class ScanRes(val ssid: String, val bssid: String, val rssi: Int, val freq: Int) {
        fun bandLabel(): String = when {
            freq in 5925..7125 -> "۶ GHz"
            freq > 4900 -> "۵ GHz"
            else -> "۲٫۴ GHz"
        }
    }

    fun interface Listener {
        fun onScan(list: List<ScanRes>)
    }

    var listener: Listener? = null
    var lastResults: List<ScanRes> = emptyList()
        private set

    private val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var running = false
    private val handler = Handler(Looper.getMainLooper())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = deliver()
    }

    private fun deliver() {
        val list = ArrayList<ScanRes>()
        try {
            for (r in wm.scanResults) {
                val b = r.BSSID ?: continue
                if (b == "02:00:00:00:00:00") continue
                list.add(ScanRes(r.SSID ?: "", b, r.level, r.frequency))
            }
        } catch (_: SecurityException) {
        }
        list.sortByDescending { it.rssi }
        lastResults = list
        listener?.onScan(list)
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                @Suppress("DEPRECATION")
                wm.startScan()
            } catch (_: Exception) {
            }
            deliver()
            handler.postDelayed(this, 4000)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun start() {
        if (running) return
        running = true
        val f = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
            } else {
                ctx.registerReceiver(receiver, f)
            }
        } catch (_: Exception) {
        }
        handler.post(loop)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(loop)
        try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
    }
}
