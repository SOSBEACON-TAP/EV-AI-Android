package com.sosbeacon.evai

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/** Presses Send in WhatsApp / Gmail after EV prepares a message, and goes Home for Focus mode. */
class EvAccess : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { if (instance === this) instance = null; return super.onUnbind(intent) }
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = pendingPackage ?: return
        if (System.currentTimeMillis() > pendingDeadline) { pendingPackage = null; return }
        if (event?.packageName?.toString() != pkg) return
        val root = rootInActiveWindow ?: return
        val send = findSend(root) ?: return
        var n: AccessibilityNodeInfo? = send
        while (n != null && !n.isClickable) n = n.parent
        if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            pendingPackage = null
            Handler(Looper.getMainLooper()).postDelayed({ performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }, 900)
        }
    }

    private fun findSend(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val id = node.viewIdResourceName ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        if (id.endsWith(":id/send") || desc.equals("send", true)) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            findSend(c)?.let { return it }
        }
        return null
    }

    companion object {
        var instance: EvAccess? = null
        @Volatile var pendingPackage: String? = null
        @Volatile var pendingDeadline = 0L

        fun armAutoSend(pkg: String) { pendingPackage = pkg; pendingDeadline = System.currentTimeMillis() + 15000 }
        fun goHome(): Boolean = instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false

        fun enabled(c: Context): Boolean {
            val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return s.contains(c.packageName)
        }
    }
}

/** Focus mode: watches the foreground app and sends the user Home when a blocked app opens. */
class FocusService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var blocked = setOf<String>()
    private var lastWarn = 0L
    private var lastPkg = ""

    private val tick = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("evai_focus", "Focus mode", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "evai_focus")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("EV AI Focus mode is on")
            .setContentText("Blocked apps will send you back to the home screen")
            .setOngoing(true)
            .build()
        startForeground(77, n)
        blocked = Focus.blockedPackages(this)
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() { handler.removeCallbacks(tick); super.onDestroy() }

    private fun check() {
        val seen = Usage.foregroundPackage(this)
        if (seen != null) lastPkg = seen
        val pkg = lastPkg
        if (pkg.isEmpty()) return
        if (pkg !in blocked) return
        val now = System.currentTimeMillis()
        if (now - lastWarn < 2500) return
        lastWarn = now
        Toast.makeText(this, "Focus mode: ${Usage.label(this, pkg)} is blocked", Toast.LENGTH_SHORT).show()
        EvAccess.goHome()
    }
}

object Focus {
    fun terms(): List<String> = Prefs.focusBlocked.split(",", "\n").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    fun blockedPackages(c: Context): Set<String> {
        val pm = c.packageManager
        val out = mutableSetOf<String>()
        val t = terms()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        for (ri in pm.queryIntentActivities(intent, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == c.packageName) continue
            val label = ri.loadLabel(pm).toString().lowercase()
            if (t.any { label.contains(it) || pkg.lowercase().contains(it) }) out.add(pkg)
        }
        return out
    }

    fun start(c: Context) {
        Prefs.focusActive = true
        c.startForegroundService(Intent(c, FocusService::class.java))
    }

    fun stop(c: Context) {
        Prefs.focusActive = false
        c.stopService(Intent(c, FocusService::class.java))
    }
}

object Usage {
    fun hasAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun label(c: Context, pkg: String): String = try {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    fun foregroundPackage(c: Context): String? {
        if (!hasAccess(c)) return null
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(now - 15000, now)
        val e = UsageEvents.Event()
        var last: String? = null
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = e.packageName
        }
        return last
    }

    private fun launchable(c: Context): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return c.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
    }

    /** Foreground milliseconds per app between [from] and [to], biggest first. */
    fun perApp(c: Context, from: Long, to: Long): List<Pair<String, Long>> {
        if (!hasAccess(c)) return emptyList()
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val map = usm.queryAndAggregateUsageStats(from, to)
        val ok = launchable(c)
        return map.values.filter { it.totalTimeInForeground > 60_000 && it.packageName in ok && it.packageName != c.packageName }
            .map { it.packageName to it.totalTimeInForeground }
            .sortedByDescending { it.second }
    }

    fun startOfDay(daysAgo: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
        return cal.timeInMillis
    }

    fun fmt(ms: Long): String {
        val m = ms / 60000
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    fun openSettings(c: Context) {
        c.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
