package com.sosbeacon.evai

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Lets EV lock the screen. The only permission it asks for is "lock the screen": it cannot wipe or read anything. */
class LockAdmin : DeviceAdminReceiver()

object PhoneLock {
    private fun comp(c: Context) = ComponentName(c, LockAdmin::class.java)

    private fun say(t: String) { Ui.messages.add(Ui.Msg(false, t)) }

    /** Locks the screen. Uses the device-admin permission; if it is not given yet, uses the accessibility service when it is on, else asks for it. */
    fun lockNow() {
        val c = EvApp.ctx
        try {
            val dpm = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isAdminActive(comp(c))) {
                dpm.lockNow()
                return
            }
        } catch (e: Exception) {
            say("The phone refused the lock request (${e.javaClass.simpleName}). Trying another way.")
        }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            val acc = EvAccess.instance
            if (acc != null && acc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) return
        }
        say("To lock the phone, tap Activate on the next screen (EV AI can only lock the screen, nothing else). Then press Lock phone now again.")
        try {
            c.startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp(c))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "EV AI uses this only to lock your screen when you ask.")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            say("This phone would not open the permission screen. Open Settings > Security > Device admin apps, switch on EV AI, then try again.")
            try { c.startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }
    }

    fun isReady(): Boolean = try {
        (EvApp.ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(comp(EvApp.ctx))
    } catch (e: Exception) { false }
}

/**
 * Context awareness: notices which kind of app you have been in for a while (from the phone's usage access, so only the
 * app's name is read) and offers one short, relevant suggestion at most every five minutes. Off until you turn it on.
 */
object ContextWatch {
    var on by mutableStateOf(Prefs.sp.getBoolean("context_aware", false))
    fun toggle() {
        on = !on
        Prefs.sp.edit().putBoolean("context_aware", on).apply()
        if (on && !Usage.hasAccess(EvApp.ctx)) {
            Ui.messages.add(Ui.Msg(false, "Context awareness needs usage access. Turn it on for EV AI on the next screen."))
            Usage.openSettings(EvApp.ctx)
        } else {
            Ui.messages.add(Ui.Msg(false, if (on) "Context awareness is ON: I notice which app you are using (its name only, nothing is sent anywhere) and offer help now and then."
                                           else "Context awareness is OFF."))
        }
    }

    private val CATS = listOf(
        Triple("chat", listOf("whatsapp", "telegram", "discord", "messaging", "signal", "slack", "messenger"),
            "You are chatting. I can draft a reply for you."),
        Triple("video", listOf("youtube", "netflix", "hotstar", "primevideo", "spotify", "mxtech", "vlc"),
            "Relaxing? I can set a sleep timer so the evening does not disappear."),
        Triple("game", listOf("game", "pubg", "minecraft", "roblox", "supercell", "garena", "bgmi"),
            "Game on. Want a break reminder in 45 minutes?"),
        Triple("study", listOf("docs", "office", "pdf", "classroom", "notes", "keep", "drive", "word", "sheets"),
            "Studying or writing? I can summarize a document or start a focus session."),
        Triple("social", listOf("instagram", "facebook", "twitter", "snapchat", "reddit", "threads"),
            "Scrolling for a while? I can set a 15 minute limit."),
    )

    fun category(pkg: String): Pair<String, String> {
        val p = pkg.lowercase()
        for ((cat, keys, offer) in CATS) if (keys.any { p.contains(it) }) return cat to offer
        return "" to ""
    }
}

@Composable
fun ContextWatcher() {
    if (!ContextWatch.on) return
    LaunchedEffect(ContextWatch.on) {
        var cat = ""; var count = 0; var lastOffered = ""; var lastTime = 0L
        while (true) {
            delay(5000)
            val pkg = Usage.foregroundPackage(EvApp.ctx)
            if (pkg != null && pkg != EvApp.ctx.packageName) {
                val (c, offer) = ContextWatch.category(pkg)
                if (c != cat) { cat = c; count = 0 }
                count++
                val now = System.currentTimeMillis()
                if (c.isNotEmpty() && count >= 4 && c != lastOffered && now - lastTime > 300_000L) {
                    lastOffered = c; lastTime = now
                    Ui.messages.add(Ui.Msg(false, offer))
                }
            }
        }
    }
}
