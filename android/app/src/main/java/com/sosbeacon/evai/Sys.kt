package com.sosbeacon.evai

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock

/** Phone health readings for the status panels and Jarvis. */
object Sys {
    fun batteryPct(c: Context): Int =
        (c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    fun charging(c: Context): Boolean = (c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).isCharging

    fun ramPct(c: Context): Int {
        val mi = ActivityManager.MemoryInfo()
        (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        if (mi.totalMem <= 0) return 0
        return (100 - (mi.availMem * 100 / mi.totalMem)).toInt()
    }

    fun ramText(c: Context): String {
        val mi = ActivityManager.MemoryInfo()
        (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return "%.1f of %.1f GB used".format((mi.totalMem - mi.availMem) / 1e9, mi.totalMem / 1e9)
    }

    fun storagePct(): Int {
        val st = StatFs(Environment.getDataDirectory().path)
        if (st.totalBytes <= 0) return 0
        return (100 - st.availableBytes * 100 / st.totalBytes).toInt()
    }

    fun storageText(): String {
        val st = StatFs(Environment.getDataDirectory().path)
        return "%.1f GB free of %.0f GB".format(st.availableBytes / 1e9, st.totalBytes / 1e9)
    }

    fun network(c: Context): String {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
    }

    fun uptime(): String {
        val m = SystemClock.elapsedRealtime() / 60000
        return if (m >= 1440) "${m / 1440}d ${(m % 1440) / 60}h" else "${m / 60}h ${m % 60}m"
    }

    fun report(c: Context): String =
        "Battery ${batteryPct(c)}%${if (charging(c)) " (charging)" else ""}. Memory ${ramText(c)}. Storage ${storageText()}. " +
            "Network ${network(c)}. Phone has been on for ${uptime()}."
}
