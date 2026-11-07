package com.sosbeacon.evai

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Counts short videos you swipe through in YouTube Shorts, Instagram Reels and TikTok. Counts stay on this phone. */
object ScrollStore {
    private val sp get() = EvApp.ctx.getSharedPreferences("scrolls", Context.MODE_PRIVATE)
    private fun day(daysAgo: Int = 0): String {
        val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }

    fun count(app: String, daysAgo: Int = 0): Int = sp.getInt("${day(daysAgo)}|$app", 0)
    val APPS = listOf("YouTube Shorts", "Instagram Reels", "TikTok")
    fun total(daysAgo: Int = 0): Int = APPS.sumOf { count(it, daysAgo) }

    /** Adds one swipe and returns today's total. Old days are cleaned up. */
    fun add(app: String): Int {
        val k = "${day()}|$app"
        val e = sp.edit().putInt(k, sp.getInt(k, 0) + 1)
        val keep = (0..13).map { day(it) }.toSet()
        for (key in sp.all.keys) if (key != "enabled" && key.substringBefore('|') !in keep) e.remove(key)
        e.apply()
        return total()
    }

    fun summary(): String {
        val t = total()
        if (t == 0) return "No short videos counted yet today."
        return "Today you scrolled $t short videos: " + APPS.filter { count(it) > 0 }.joinToString(", ") { "${count(it)} on $it" } + "."
    }
}

object ShortsCounter {
    private var last = 0L
    private val handler = Handler(Looper.getMainLooper())

    private fun appFor(pkg: String): String? = when (pkg) {
        "com.google.android.youtube" -> "YouTube Shorts"
        "com.instagram.android" -> "Instagram Reels"
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> "TikTok"
        else -> null
    }

    /** Called for every accessibility event. A swipe is a vertical scroll inside the short-video pager. */
    fun onEvent(c: Context, e: AccessibilityEvent) {
        if (!ScrollStore.enabled || e.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val app = appFor(e.packageName?.toString() ?: return) ?: return
        if (e.scrollDeltaY == 0 && e.scrollY == 0) return                      // sideways or no real scroll
        if (app != "TikTok") {                                                // the other two apps also scroll normal feeds
            val id = (e.source?.viewIdResourceName ?: "") + " " + (e.className ?: "")
            if (!Regex("reel|short|clip|pager", RegexOption.IGNORE_CASE).containsMatchIn(id)) return
        }
        val now = System.currentTimeMillis()
        if (now - last < 700) return                                          // one swipe makes several events
        last = now
        val total = ScrollStore.add(app)
        if (total % 25 == 0) {
            val msg = "You have scrolled $total short videos today."
            handler.post { Toast.makeText(c, msg, Toast.LENGTH_LONG).show(); if (Prefs.speak) Voice.speak(msg) }
        }
    }
}

@Composable
fun ScrollsScreen() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var on by remember { mutableStateOf(ScrollStore.enabled) }
    val access = remember(tick) { EvAccess.enabled(ctx) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(2000); tick++ } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Section("Short-video scroll counter") {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(on, { on = it; ScrollStore.enabled = it })
                Spacer(Modifier.width(10.dp))
                Text(if (on) "Counting" else "Off")
            }
            key(tick) {
                Text("${ScrollStore.total()}", fontSize = 48.sp, color = MaterialTheme.colorScheme.primary)
                Text("short videos today")
                ScrollStore.APPS.forEach { Text("$it: ${ScrollStore.count(it)}", Modifier.padding(top = 4.dp)) }
            }
            if (!access) {
                Text("Turn on the EV AI Accessibility setting so EV can see swipes.", Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.error)
                Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, modifier = Modifier.padding(top = 6.dp)) { Text("Open Accessibility settings") }
            }
            OutlinedButton(onClick = { tick++ }, modifier = Modifier.padding(top = 6.dp)) { Text("Refresh") }
        }
        Section("Last 7 days") {
            key(tick) { for (d in 0..6) {
                val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -d)
                Text(SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(cal.timeInMillis)) + ":  ${ScrollStore.total(d)}")
            } }
        }
        Text("EV counts a swipe when the short-video feed scrolls. It does not read what you watch. Apps change their screens, so the count can be a little off. EV says a number every 25 videos.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}
