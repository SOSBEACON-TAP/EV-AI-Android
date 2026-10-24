package com.sosbeacon.evai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/** Device code: same format the computer's Settings -> Connected device expects. */
object DeviceCode {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private fun check(body: String): Char {
        var total = 0
        body.forEachIndexed { i, c -> total += (i + 1) * ALPHABET.indexOf(c) }
        return ALPHABET[total % 36]
    }

    fun create(): String {
        val r = SecureRandom()
        val body = buildString { repeat(10) { append(ALPHABET[r.nextInt(36)]) } }
        return "EV-${body.substring(0, 5)}-${body.substring(5)}-${check(body)}"
    }

    /** The 10-character secret id inside a valid code, or null. */
    fun decode(code: String): String? {
        var c = code.uppercase().filter { it.isLetterOrDigit() }
        if (c.length == 13 && c.startsWith("EV")) c = c.substring(2)
        if (c.length != 11 || c.any { it !in ALPHABET }) return null
        val body = c.substring(0, 10)
        return if (check(body) == c[10]) body else null
    }

    fun topic(id: String) = "ev-" + id.lowercase()

    /** This phone's code; made once and kept. */
    fun mine(): String {
        if (decode(Prefs.deviceCode) == null) Prefs.deviceCode = create()
        return Prefs.deviceCode
    }
}

/** Rings the phone: ringtone + vibration until answered, declined or 45 seconds pass. */
object Ringer {
    private var ringtone: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopper = Runnable { stop() }

    fun start(c: Context) {
        stop()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(c, uri)?.also {
                if (Build.VERSION.SDK_INT >= 28) it.isLooping = true
                it.play()
            }
        } catch (e: Exception) { }
        try {
            val pattern = longArrayOf(0, 800, 600)
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                (c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createWaveform(pattern, 0))
            }
        } catch (e: Exception) { }
        handler.postDelayed(stopper, 45_000)
    }

    fun stop() {
        handler.removeCallbacks(stopper)
        try { ringtone?.stop() } catch (e: Exception) { }
        ringtone = null
        try {
            val c = EvApp.ctx
            if (Build.VERSION.SDK_INT >= 31) {
                (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator.cancel()
            } else {
                (c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
            }
        } catch (e: Exception) { }
    }
}

/** Shows the incoming "EV is calling" screen and starts ringing. */
object IncomingCall {
    private const val CHANNEL = "evai_call"

    fun show(c: Context, message: String) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL, "EV is calling", NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(null, null)
        ch.enableVibration(false)
        nm.createNotificationChannel(ch)
        val i = Intent(c, CallActivity::class.java)
            .putExtra("message", message)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(c, 7, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("EV is calling")
            .setContentText(message)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        try { nm.notify(900, n) } catch (e: SecurityException) { }
        Ringer.start(c)
    }

    fun dismiss(c: Context) {
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(900)
        Ringer.stop()
    }
}

/** Keeps a connection to the alert channel so the computer can ring this phone. */
class CallService : Service() {
    @Volatile private var running = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("evai_listen", "EV is listening for your computer", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "evai_listen")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("EV AI is connected")
            .setContentText("Ready to ring you if your computer switches off mid-task")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        startForeground(78, n)
        if (!running) {
            running = true
            Thread { loop() }.also { it.isDaemon = true; it.start() }
            Thread { updateLoop() }.also { it.isDaemon = true; it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() { running = false; super.onDestroy() }

    private fun loop() {
        var wait = 2000L
        while (running) {
            try {
                listen()
                wait = 2000L
            } catch (e: Exception) { }
            try { Thread.sleep(wait) } catch (e: InterruptedException) { return }
            wait = minOf(wait * 2, 60_000L)
        }
    }

    /** Every few hours, look for a newer build and install it (when auto-update is on). */
    private fun updateLoop() {
        while (running) {
            try {
                val now = System.currentTimeMillis()
                if (Prefs.autoUpdate && now - Prefs.lastUpdateCheck > 6 * 3600 * 1000L) {
                    Prefs.lastUpdateCheck = now
                    Updater.run(applicationContext)
                }
                Thread.sleep(30 * 60 * 1000L)
            } catch (e: InterruptedException) {
                return
            } catch (e: Exception) { }
        }
    }

    private fun listen() {
        val id = DeviceCode.decode(DeviceCode.mine()) ?: return
        val since = if (Prefs.lastSeen > 0) Prefs.lastSeen else System.currentTimeMillis() / 1000
        val url = URL("${Prefs.server.trimEnd('/')}/${DeviceCode.topic(id)}/sse?since=$since")
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 120000
        c.setRequestProperty("Accept", "text/event-stream")
        try {
            c.inputStream.bufferedReader().use { r ->
                while (running) {
                    val line = r.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val j = try { JSONObject(line.substring(5).trim()) } catch (e: Exception) { continue }
                    if (j.optString("event") != "message") continue
                    val t = j.optLong("time", 0)
                    if (t > 0) Prefs.lastSeen = t + 1
                    val ageSeconds = System.currentTimeMillis() / 1000 - t
                    if (t > 0 && ageSeconds > 900) continue   // ignore old calls
                    val msg = j.optString("message", "Your computer needs you.")
                    Handler(Looper.getMainLooper()).post { IncomingCall.show(applicationContext, msg) }
                }
            }
        } finally {
            c.disconnect()
        }
    }

    companion object {
        fun start(c: Context) {
            try { c.startForegroundService(Intent(c, CallService::class.java)) } catch (e: Exception) { }
        }
        fun stop(c: Context) { c.stopService(Intent(c, CallService::class.java)) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Reminders.restoreAll(context)
        if (Prefs.listen) CallService.start(context)
    }
}
