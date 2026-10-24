package com.sosbeacon.evai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Self-update: finds the newest build on GitHub, downloads it and hands it to Android's installer. */
object Updater {
    private const val API = "https://api.github.com/repos/SOSBEACON-TAP/EV-AI-Android/releases/tags/android-latest"

    class Info(val code: Long, val url: String)

    fun installedCode(c: Context): Long =
        c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode

    private fun get(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.setRequestProperty("User-Agent", "EV-AI-Android")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        return c
    }

    /** The newest published build, or null if it can't be read. */
    fun latest(): Info? {
        val c = get(API)
        try {
            if (c.responseCode != 200) return null
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val code = Regex("versionCode:\\s*(\\d+)").find(j.optString("body", ""))?.groupValues?.get(1)?.toLongOrNull() ?: return null
            val assets = j.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) return Info(code, a.getString("browser_download_url"))
            }
            return null
        } finally {
            c.disconnect()
        }
    }

    private fun download(c: Context, info: Info): File {
        val dir = File(c.cacheDir, "updates").also { it.mkdirs() }
        val out = File(dir, "EV-AI-update.apk")
        val conn = get(info.url)
        try {
            conn.inputStream.use { inp -> out.outputStream().use { inp.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        return out
    }

    private fun install(c: Context, apk: File) {
        val installer = c.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            apk.inputStream().use { inp ->
                s.openWrite("EV-AI.apk", 0, apk.length()).use { out ->
                    inp.copyTo(out)
                    s.fsync(out)
                }
            }
            val pi = PendingIntent.getBroadcast(
                c, id, Intent(c, InstallReceiver::class.java).setAction("com.sosbeacon.evai.INSTALL"),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            s.commit(pi.intentSender)
        }
    }

    fun canInstall(c: Context): Boolean = c.packageManager.canRequestPackageInstalls()

    /** Checks, downloads and installs. Returns a message for the screen. */
    fun run(c: Context): String {
        return try {
            val info = latest() ?: return "I couldn't check for updates right now."
            val mine = installedCode(c)
            if (info.code <= mine) return "You're up to date (build $mine)."
            if (!canInstall(c)) return "A new build (${info.code}) is ready. Allow EV AI to install updates, then check again."
            val apk = download(c, info)
            install(c, apk)
            "Updating to build ${info.code}…"
        } catch (e: Exception) {
            "Update failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}

@Suppress("DEPRECATION")
private fun legacyIntentExtra(i: Intent): Intent? = i.getParcelableExtra(Intent.EXTRA_INTENT)

/** Receives the installer's progress. Asks the user to confirm when Android needs that. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else
                    legacyIntentExtra(intent)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.createNotificationChannel(NotificationChannel("evai_update", "App updates", NotificationManager.IMPORTANCE_HIGH))
                    val pi = PendingIntent.getActivity(context, 11, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    val n = Notification.Builder(context, "evai_update")
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("EV AI update ready")
                        .setContentText("Tap to install the new version")
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build()
                    try { nm.notify(901, n) } catch (e: SecurityException) { }
                    try { context.startActivity(confirm) } catch (e: Exception) { }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Prefs.updateNote = "Updated."
            else -> Prefs.updateNote = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "The update didn't install."
        }
    }
}
