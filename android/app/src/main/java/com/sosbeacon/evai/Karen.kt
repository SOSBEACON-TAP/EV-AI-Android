package com.sosbeacon.evai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Karen mode: parent control. A parent sets a PIN, a daily screen-time limit, a bedtime and apps to block.
 * It is visible (a permanent notification) and never hidden. Honest limit: a child who knows how to uninstall
 * an app can remove it, so for strong protection also use Google Family Link.
 */
object KarenStore {
    private val sp get() = EvApp.ctx.getSharedPreferences("karen", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }
    var limitMin: Int
        get() = sp.getInt("limit", 0)
        set(v) { sp.edit().putInt("limit", v).apply() }
    var bedOn: Boolean
        get() = sp.getBoolean("bed_on", false)
        set(v) { sp.edit().putBoolean("bed_on", v).apply() }
    var bedStart: String
        get() = sp.getString("bed_start", "21:00") ?: "21:00"
        set(v) { sp.edit().putString("bed_start", v).apply() }
    var bedEnd: String
        get() = sp.getString("bed_end", "07:00") ?: "07:00"
        set(v) { sp.edit().putString("bed_end", v).apply() }
    var blockedApps: String
        get() = sp.getString("blocked", "") ?: ""
        set(v) { sp.edit().putString("blocked", v).apply() }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    var extraMin: Int
        get() = if (sp.getString("extra_day", "") == today()) sp.getInt("extra", 0) else 0
        set(v) { sp.edit().putString("extra_day", today()).putInt("extra", v).apply() }

    // ---- PIN (stored as a salted PBKDF2 hash, never in plain text)
    val hasPin: Boolean get() = sp.getString("pin_hash", "") != ""

    private fun hash(pin: String, salt: ByteArray): String {
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, 100_000, 256))
        return key.encoded.joinToString("") { "%02x".format(it) }
    }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp.edit().putString("pin_salt", salt.joinToString("") { "%02x".format(it) }).putString("pin_hash", hash(pin, salt)).putInt("fails", 0).apply()
    }

    fun lockedForSec(): Long = ((sp.getLong("locked_until", 0L) - System.currentTimeMillis()) / 1000).coerceAtLeast(0)

    /** 5 wrong tries lock the PIN for 5 minutes. */
    fun checkPin(pin: String): Boolean {
        if (lockedForSec() > 0 || !hasPin) return false
        val salt = (sp.getString("pin_salt", "") ?: "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val ok = hash(pin, salt) == sp.getString("pin_hash", "")
        if (ok) { sp.edit().putInt("fails", 0).apply(); return true }
        val fails = sp.getInt("fails", 0) + 1
        if (fails >= 5) sp.edit().putInt("fails", 0).putLong("locked_until", System.currentTimeMillis() + 300_000).apply()
        else sp.edit().putInt("fails", fails).apply()
        return false
    }

    // ---- rules
    fun parse(hhmm: String): Int? {
        val p = hhmm.trim().split(":")
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null; val m = p[1].toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) h * 60 + m else null
    }

    fun inBedtime(nowMin: Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }): Boolean {
        if (!bedOn) return false
        val s = parse(bedStart) ?: return false; val e = parse(bedEnd) ?: return false
        return if (s <= e) nowMin in s until e else nowMin >= s || nowMin < e        // overnight window such as 21:00 to 07:00
    }

    fun usedTodayMin(c: Context): Int = (Usage.perApp(c, Usage.startOfDay(0), System.currentTimeMillis()).sumOf { it.second } / 60000).toInt()

    /** Minutes left today, or null when there is no limit. */
    fun remainingMin(c: Context): Int? = if (limitMin <= 0) null else limitMin + extraMin - usedTodayMin(c)

    fun blockedPackages(c: Context): Set<String> {
        val terms = blockedApps.split(",", "\n").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptySet()
        val pm = c.packageManager
        val out = mutableSetOf<String>()
        for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == c.packageName) continue
            val label = ri.loadLabel(pm).toString().lowercase()
            if (terms.any { label.contains(it) || pkg.lowercase().contains(it) }) out.add(pkg)
        }
        return out
    }

    fun statusLine(c: Context): String {
        if (!enabled) return "Karen mode is OFF."
        val parts = mutableListOf("Karen mode is ON")
        remainingMin(c)?.let { parts += if (it > 0) "$it min of screen time left today" else "today's screen time is used up" }
        if (bedOn) parts += "bedtime $bedStart to $bedEnd"
        return parts.joinToString(". ") + "."
    }

    fun startService(c: Context) { ContextCompat.startForegroundService(c, Intent(c, KarenService::class.java)) }
    fun stopService(c: Context) { c.stopService(Intent(c, KarenService::class.java)) }
}

/** Watches the foreground app. At the limit, at bedtime, or in a blocked app it sends the phone Home. */
class KarenService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastAct = 0L
    private var warnedDay = ""
    private val tick = object : Runnable { override fun run() { check(); handler.postDelayed(this, 5000) } }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("evai_karen", "Karen mode", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "evai_karen")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("EV AI Karen mode is on")
            .setContentText("Screen-time rules are active. A parent PIN is needed to change them.")
            .setOngoing(true)
            .build()
        startForeground(78, n)
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() { handler.removeCallbacks(tick); super.onDestroy() }

    private fun exempt(): Set<String> {
        val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        return setOfNotNull(packageName, home, "com.android.systemui", "com.android.dialer", "com.google.android.dialer",
            "com.samsung.android.dialer", "com.android.phone", "com.android.settings")
    }

    private fun check() {
        if (!KarenStore.enabled) { stopSelf(); return }
        val fg = Usage.foregroundPackage(this) ?: return
        val left = KarenStore.remainingMin(this)
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (left != null && left in 1..5 && warnedDay != day) {
            warnedDay = day
            Toast.makeText(this, "Karen mode: $left minutes of screen time left today", Toast.LENGTH_LONG).show()
            Voice.speakAs("karen", "$left minutes of screen time left today.")
        }
        if (fg in exempt()) return
        val reason = when {
            KarenStore.inBedtime() -> "it is bedtime"
            left != null && left <= 0 -> "today's screen time is used up"
            fg in KarenStore.blockedPackages(this) -> "${Usage.label(this, fg)} is blocked"
            else -> null
        } ?: return
        val now = System.currentTimeMillis()
        if (now - lastAct < 2500) return
        lastAct = now
        Toast.makeText(this, "Karen mode: $reason", Toast.LENGTH_SHORT).show()
        EvAccess.goHome()
    }
}

@Composable
fun KarenScreen() {
    val ctx = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var hasPin by remember { mutableStateOf(KarenStore.hasPin) }
    var enabled by remember { mutableStateOf(KarenStore.enabled) }
    var limit by remember { mutableStateOf(KarenStore.limitMin.toString()) }
    var bedOn by remember { mutableStateOf(KarenStore.bedOn) }
    var bedStart by remember { mutableStateOf(KarenStore.bedStart) }
    var bedEnd by remember { mutableStateOf(KarenStore.bedEnd) }
    var apps by remember { mutableStateOf(KarenStore.blockedApps) }
    var report by remember { mutableStateOf("") }
    val note = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Section("Karen mode") {
            Text(KarenStore.statusLine(ctx))
            if (msg.isNotEmpty()) Text(msg, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.primary)
        }
        if (!hasPin) {
            Section("Create a parent PIN") {
                Text("Choose 4 to 8 digits. You will need it to change any rule.", color = note)
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("New PIN") }, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(pin2, { pin2 = it.filter(Char::isDigit).take(8) }, Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true, label = { Text("Repeat PIN") }, visualTransformation = PasswordVisualTransformation())
                Button(onClick = {
                    if (pin.length < 4) msg = "The PIN needs at least 4 digits."
                    else if (pin != pin2) msg = "The two PINs do not match."
                    else { KarenStore.setPin(pin); hasPin = true; unlocked = true; msg = "PIN saved."; pin = ""; pin2 = "" }
                }, modifier = Modifier.padding(top = 8.dp)) { Text("Save PIN") }
            }
        } else if (!unlocked) {
            Section("Parent PIN") {
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation())
                Button(onClick = {
                    val wait = KarenStore.lockedForSec()
                    if (wait > 0) msg = "Too many wrong tries. Wait $wait seconds."
                    else if (KarenStore.checkPin(pin)) { unlocked = true; msg = "" } else msg = "Wrong PIN."
                    pin = ""
                }, modifier = Modifier.padding(top = 8.dp)) { Text("Unlock") }
            }
        } else {
            Section("Switch") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(enabled, {
                        enabled = it; KarenStore.enabled = it
                        if (it) KarenStore.startService(ctx) else KarenStore.stopService(ctx)
                    })
                    Spacer(Modifier.width(10.dp)); Text(if (enabled) "Karen mode is ON" else "Karen mode is off")
                }
                if (!Usage.hasAccess(ctx)) {
                    Text("Usage access is needed to measure screen time.", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { Usage.openSettings(ctx) }) { Text("Allow usage access") }
                }
                if (!EvAccess.enabled(ctx)) {
                    Text("Turn on the EV AI Accessibility setting so EV can send the phone Home when time is up.", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.error)
                }
            }
            Section("Rules") {
                OutlinedTextField(limit, { limit = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Daily screen time in minutes (0 = no limit)") })
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(bedOn, { bedOn = it }); Spacer(Modifier.width(10.dp)); Text("Bedtime")
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(bedStart, { bedStart = it.take(5) }, Modifier.weight(1f), singleLine = true, label = { Text("From (HH:MM)") })
                    OutlinedTextField(bedEnd, { bedEnd = it.take(5) }, Modifier.weight(1f), singleLine = true, label = { Text("Until (HH:MM)") })
                }
                OutlinedTextField(apps, { apps = it }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Apps to block, comma separated (e.g. youtube, tiktok)") })
                Button(onClick = {
                    if (bedOn && (KarenStore.parse(bedStart) == null || KarenStore.parse(bedEnd) == null)) msg = "Bedtime times must look like 21:00."
                    else {
                        KarenStore.limitMin = limit.toIntOrNull() ?: 0
                        KarenStore.bedOn = bedOn; KarenStore.bedStart = bedStart.trim(); KarenStore.bedEnd = bedEnd.trim()
                        KarenStore.blockedApps = apps
                        msg = "Rules saved."
                    }
                }, modifier = Modifier.padding(top = 8.dp)) { Text("Save rules") }
            }
            Section("Extra time today") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in listOf(15, 30, 60)) OutlinedButton(onClick = { KarenStore.extraMin = KarenStore.extraMin + m; msg = "Added $m minutes for today." }) { Text("+$m min") }
                }
            }
            Section("Usage today") {
                Button(onClick = {
                    val top = Usage.perApp(ctx, Usage.startOfDay(0), System.currentTimeMillis()).take(8)
                    report = if (top.isEmpty()) "No usage yet (or Usage access is off)." else
                        "Total ${KarenStore.usedTodayMin(ctx)} min\n" + top.joinToString("\n") { "${Usage.label(ctx, it.first)}: ${it.second / 60000} min" }
                }) { Text("Show report") }
                if (report.isNotEmpty()) Text(report, Modifier.padding(top = 8.dp))
            }
            Section("Change PIN") {
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("New PIN") }, visualTransformation = PasswordVisualTransformation())
                Button(onClick = { if (pin.length >= 4) { KarenStore.setPin(pin); msg = "PIN changed."; pin = "" } else msg = "The PIN needs at least 4 digits." }, modifier = Modifier.padding(top = 6.dp)) { Text("Change PIN") }
            }
        }
        Text("Karen mode is always visible: it shows a permanent notification and never hides. A child who knows how to uninstall apps could remove EV AI, so for strong protection also use Google Family Link.", color = note)
    }
}
