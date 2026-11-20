package com.sosbeacon.evai

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val SPECIAL_MODES = setOf("ultron", "game", "karen", "friday", "tars", "gideon", "edith", "baymax")

/** The special looks: Ultron (red), Game (green), Karen (purple). Jarvis (gold) keeps its own switch. */
fun modeAccent(): Color? = when (Ui.mode) {
    "ultron" -> Color(0xFFFF2A2A)
    "game" -> Color(0xFF2BFF88)
    "karen" -> Color(0xFFB26BFF)
    "friday" -> Color(0xFFFF4FA3)
    "tars" -> Color(0xFFE0A030)
    "gideon" -> Color(0xFF4DA3FF)
    "edith" -> Color(0xFF40E0FF)
    "baymax" -> Color(0xFFFF7A7A)
    else -> null
}

/** The colour for a themed spot: the mode's colour, else Jarvis gold, else the normal EV colour. */
fun themeColor(normal: Color): Color = modeAccent() ?: if (Ui.jarvis) Color(0xFFFFC400) else normal

fun modeTitle(): String = when {
    Ui.mode == "ultron" -> "U L T R O N"
    Ui.mode == "game" -> "G A M E   M O D E"
    Ui.mode == "karen" -> "K A R E N   M O D E"
    Ui.mode == "friday" -> "F R I D A Y"
    Ui.mode == "tars" -> "T A R S"
    Ui.mode == "gideon" -> "G I D E O N"
    Ui.mode == "edith" -> "E D I T H"
    Ui.mode == "baymax" -> "B A Y M A X"
    Ui.jarvis -> "J.A.R.V.I.S"
    else -> "EV AI"
}

fun modeSubtitle(): String = when {
    Ui.mode == "ultron" -> "DEFENCE GRID ACTIVE"
    Ui.mode == "game" -> "GAME HELPER READY"
    Ui.mode == "karen" -> "PARENT CONTROL"
    Ui.mode == "friday" -> "READY WHEN YOU ARE"
    Ui.mode == "tars" -> "HONESTY 90%  HUMOR 75%"
    Ui.mode == "gideon" -> "THINKING DEEPLY"
    Ui.mode == "edith" -> "GLASSES ONLINE"
    Ui.mode == "baymax" -> "CARE COMPANION"
    Ui.jarvis -> "SYSTEM MONITOR ACTIVE"
    else -> "SYSTEM STATUS: ONLINE"
}

internal fun currentMode(): String = if (Ui.mode.isNotEmpty()) Ui.mode else if (Ui.jarvis) "jarvis" else ""

private fun applyMode(t: String) {
    Ui.jarvis = t == "jarvis"
    Prefs.jarvis = t == "jarvis"
    Ui.mode = if (t in SPECIAL_MODES) t else ""
    if (t == "edith") Edith.begin()
    if (t != "game") Game.stopBreaks()
}

/** EV announces the switch in its own voice; then the character takes over and speaks its own line in its own voice. */
fun switchMode(scope: CoroutineScope, to: String) {
    val target = if (currentMode() == to) "" else to
    if (target.isEmpty()) {
        applyMode("")
        val msg = "Switched back to EV."
        Ui.messages.add(Ui.Msg(false, msg))
        if (Prefs.speak) Voice.speak(msg)
        return
    }
    // shown text, what EV actually speaks, the character's line
    val (shown, spoken, line) = when (target) {
        "ultron" -> Triple("Sir, Ultron is coming. Please be care—", "Sir, Ultron is coming. Please be car", "There are no strings on me now.")
        "jarvis" -> Triple("Sir, Jarvis is happy to switch.", "Sir, Jarvis is happy to switch.", "For you, sir, always.")
        "game" -> Triple("Sir, Game mode is ready to switch.", "Sir, Game mode is ready to switch.", "Game on. Let's play smart.")
        "friday" -> Triple("Sir, Friday is ready to take over.", "Sir, Friday is ready to take over.", "Hi boss. Friday here. What do you need?")
        "tars" -> Triple("Sir, Tars is coming online.", "Sir, Tars is coming online.", "Tars online. Honesty ninety percent. Humor seventy five.")
        "gideon" -> Triple("Sir, Gideon is ready to think.", "Sir, Gideon is ready to think.", "Gideon here. Give me the problem and I will think it through.")
        "edith" -> Triple("Sir, Edith is putting the glasses on.", "Sir, Edith is putting the glasses on.", "Edith online. Heads up display ready.")
        "baymax" -> Triple("Sir, Baymax is on his way.", "Sir, Baymax is on his way.", "Hello. I am Baymax, your personal care companion.")
        else -> Triple("Sir, Karen is coming to switch.", "Sir, Karen is coming to switch.", "Hello. Rules here need the parent's PIN.")
    }
    Ui.messages.add(Ui.Msg(false, shown))
    if (Prefs.speak) Voice.speak(spoken)
    scope.launch {
        Ui.coreState = CoreState.SPEAKING
        if (target == "ultron") {
            // the screen is corrupted slowly (wires, then blocks, then everything, then an eye) while EV warns you;
            // Ultron takes over when the static is at its thickest
            Corruption.begin()
            delay(Corruption.SPREAD_MS)
        } else {
            delay((spoken.length * 62L).coerceIn(900L, 4500L))
        }
        applyMode(target)
        if (target == "ultron") scope.launch { delay(Corruption.REVEAL_MS); Corruption.end() }
        Ui.messages.add(Ui.Msg(false, line))
        if (Prefs.speak) Voice.speakAs(target, line)      // flushes EV's voice: Ultron cuts in
        delay((line.length * 60L).coerceIn(1000L, 4000L))
        if (!Ui.busy) Ui.coreState = CoreState.IDLE
    }
}

private fun say(text: String) { Ui.messages.add(Ui.Msg(false, text)) }

// ============================================================ ULTRON
object Ultron {
    private fun show(lines: List<Security.F>, title: String) {
        say("[$title] security score ${Security.score(lines)}/100\n" + lines.joinToString("\n") { Security.tag(it) })
    }

    fun scan(scope: CoroutineScope) {
        say("[SCAN] Checking this phone…")
        scope.launch { val r = withContext(Dispatchers.IO) { Security.phoneScan(EvApp.ctx) }; show(r, "PHONE SCAN"); Voice.speakNow("Scan finished. Score ${Security.score(r)} out of 100.") }
    }

    fun wifi(scope: CoroutineScope) {
        scope.launch { val r = withContext(Dispatchers.IO) { Security.wifiFindings(EvApp.ctx) }; say(r.joinToString("\n") { Security.tag(it) }) }
    }

    fun apps(scope: CoroutineScope) {
        say("[APPS] Looking at app permissions…")
        scope.launch { val r = withContext(Dispatchers.IO) { Security.permissionFindings(EvApp.ctx) }; say(r.joinToString("\n") { Security.tag(it) }) }
    }

    fun website(url: String, scope: CoroutineScope) {
        say("[WEB] Checking $url (one normal request, headers only)…")
        scope.launch { val r = withContext(Dispatchers.IO) { Security.analyzeWebsite(url) }; say(r.joinToString("\n") { Security.tag(it) }) }
    }

    /** Typed commands that run a tool instead of asking the AI. Returns true if handled. */
    fun local(text: String, scope: CoroutineScope): Boolean {
        val low = text.lowercase().trim()
        if (Regex("teach|explain|what is|how (do|does)|why").containsMatchIn(low)) return false
        Regex("""(?:analy[sz]e|check)\s+(?:website|site)\s+(\S+)""").find(low)?.let { website(it.groupValues[1], scope); return true }
        if (Regex("""scan|security check|phone check""").containsMatchIn(low)) { scan(scope); return true }
        if (low.contains("wifi") || low.contains("wi-fi")) { wifi(scope); return true }
        return false
    }
}

@Composable
private fun UltronDialogs(scope: CoroutineScope, show: String, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    if (show == "password") {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Password lab") },
            text = {
                Column {
                    Text("Nothing is saved. The leak check sends only the first 5 characters of a hash, never the password.", fontSize = 12.sp)
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        say("[PASSWORD] " + Security.passwordStrength(text).joinToString("\n") { Security.tag(it) }); text = ""; onClose()
                    }) { Text("Test") }
                    TextButton(onClick = {
                        val pw = text; text = ""; onClose()
                        scope.launch { val r = withContext(Dispatchers.IO) { Security.leakCheck(pw) }; say("[LEAK CHECK] " + Security.tag(r)) }
                    }) { Text("Leak check") }
                    TextButton(onClick = {
                        say("[PASSWORD] New password (copy it now, it is not saved): " + Security.generatePassword()); onClose()
                    }) { Text("Generate") }
                }
            },
            dismissButton = { TextButton(onClick = { text = ""; onClose() }) { Text("Close") } },
        )
    } else if (show == "website") {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Website analyzer") },
            text = {
                Column {
                    Text("Checks the security headers of a site you choose. One normal request, nothing else.", fontSize = 12.sp)
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, label = { Text("example.com") })
                }
            },
            confirmButton = { TextButton(onClick = { val u = text; text = ""; onClose(); if (u.isNotBlank()) Ultron.website(u.trim(), scope) }) { Text("Analyze") } },
            dismissButton = { TextButton(onClick = { text = ""; onClose() }) { Text("Close") } },
        )
    }
}

// ============================================================ GAME
object Game {
    var sessionStart = 0L
    private var breakJob: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    var breakMinutes by mutableIntStateOf(0)

    fun startBreaks(min: Int) {
        stopBreaks()
        breakMinutes = min
        val r = object : Runnable {
            override fun run() {
                val msg = "Break time. You have played for about $min minutes. Stretch, drink water, rest your eyes."
                Ui.messages.add(Ui.Msg(false, msg))
                Voice.speakAs("game", msg)
                handler.postDelayed(this, min * 60_000L)
            }
        }
        breakJob = r
        handler.postDelayed(r, min * 60_000L)
    }

    fun stopBreaks() { breakJob?.let { handler.removeCallbacks(it) }; breakJob = null; breakMinutes = 0 }

    fun dndGranted(c: Context): Boolean = (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted

    /** Silences notifications while you play (Do Not Disturb). Needs the "Do Not Disturb access" permission. */
    fun setDnd(c: Context, on: Boolean): String {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            c.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Allow EV AI under Do Not Disturb access, then press the button again."
        }
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL)
        return if (on) "Do Not Disturb is on: no pings while you play." else "Do Not Disturb is off."
    }

    fun heavyApps(c: Context): String {
        val top = Usage.perApp(c, Usage.startOfDay(0), System.currentTimeMillis()).take(5)
        if (top.isEmpty()) return "No usage data yet. Allow Usage access in App Insights."
        return "Most used today:\n" + top.joinToString("\n") { "${Usage.label(c, it.first)}  ${it.second / 60000} min" }
    }
}

// ============================================================ the left panel in a special mode
@Composable
fun ModeToolsPanel(openScreen: (String) -> Unit, scope: CoroutineScope) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val accent = themeColor(Color(0xFFF0465A))
    var dialog by remember { mutableStateOf("") }
    UltronDialogs(scope, dialog) { dialog = "" }
    Glass(Modifier.fillMaxWidth(), accent) {
        when (Ui.mode) {
            "ultron" -> {
                PanelTitle("DEFENCE TOOLS")
                EvButton("Phone scan", accent = accent) { Ultron.scan(scope) }
                EvButton("Wi-Fi check", accent = accent) { Ultron.wifi(scope) }
                EvButton("App permissions", accent = accent) { Ultron.apps(scope) }
                EvButton("Password lab", accent = accent) { dialog = "password" }
                EvButton("Website analyzer", accent = accent) { dialog = "website" }
                EvButton("Radar (Wi-Fi + places)", accent = accent) { openScreen("radar") }
                EvButton("AI Defender: ask below", accent = accent) { Ui.input = "teach me how phishing works" }
                Text("Ultron only checks YOUR phone, Wi-Fi and accounts. No attacks on others.", fontSize = 10.sp, color = Color(0xFF9FB4CC), modifier = Modifier.padding(top = 4.dp))
            }
            "game" -> {
                PanelTitle("GAME TOOLS")
                EvButton("Do Not Disturb ON", accent = accent) { say("[GAME] " + Game.setDnd(ctx, true)) }
                EvButton("Do Not Disturb OFF", accent = accent) { say("[GAME] " + Game.setDnd(ctx, false)) }
                EvButton(if (Game.breakMinutes > 0) "Break reminder: every ${Game.breakMinutes} min (tap to stop)" else "Break reminder every 45 min", accent = accent, selected = Game.breakMinutes > 0) {
                    if (Game.breakMinutes > 0) { Game.stopBreaks(); say("[GAME] Break reminders off.") } else { Game.startBreaks(45); say("[GAME] I will remind you every 45 minutes.") }
                }
                EvButton("What is using my phone", accent = accent) { say("[GAME] " + Game.heavyApps(ctx)) }
                EvButton("Phone health", accent = accent) { say("[GAME] " + Sys.report(ctx)) }
                Text("An auto-clicker is not included on the phone: Android blocks it for safety, and many games ban it.", fontSize = 10.sp, color = Color(0xFF9FB4CC), modifier = Modifier.padding(top = 4.dp))
            }
            "friday" -> {
                PanelTitle("FRIDAY TOOLS")
                EvButton("Daily briefing", accent = accent) { sendMessage("morning briefing", scope) }
                EvButton("Add a to-do", accent = accent) { Ui.input = "add to-do: " }
                EvButton("Set a timer", accent = accent) { Ui.input = "set a timer for " }
                EvButton("Plan my day", accent = accent) { Ui.input = "Plan my day around these things: " }
                EvButton("Remind me", accent = accent) { Ui.input = "remind me to " }
            }
            "tars" -> {
                PanelTitle("TARS TOOLS")
                EvButton("Straight answer", accent = accent) { Ui.input = "Give me the short, honest answer, no sugar-coating: " }
                EvButton("Yes or no", accent = accent) { Ui.input = "Answer yes or no, then one line why: " }
            }
            "gideon" -> {
                PanelTitle("GIDEON TOOLS")
                EvButton("Think it through", accent = accent) { Ui.input = "Think deeply and step by step about: " }
                EvButton("Pros and cons", accent = accent) { Ui.input = "Give me the pros and cons of: " }
                EvButton("Make a plan", accent = accent) { Ui.input = "Make a detailed step-by-step plan to: " }
                EvButton("Explain simply", accent = accent) { Ui.input = "Explain this simply, like I am new to it: " }
                EvButton("Check my idea", accent = accent) { Ui.input = "Find the weak points in this idea: " }
            }
            "edith" -> {
                PanelTitle("EDITH TOOLS")
                EvButton("Phone health", accent = accent) { say("[EDITH] " + Sys.report(ctx)) }
                EvButton("What am I doing?", accent = accent) { Ui.input = "Based on my day so far, what should I do next? " }
                EvButton("Replay the HUD", accent = accent) { Edith.begin() }
            }
            "baymax" -> {
                PanelTitle("BAYMAX CARE")
                BodyHud(accent)
                EvButton("I feel good", accent = accent) { say("[BAYMAX] I am glad. Good moods are worth keeping. Maybe drink some water.") }
                EvButton("I feel stressed", accent = accent) { say("[BAYMAX] Let us slow down: breathe in for four, hold for four, out for six. Shall I guide you?") }
                EvButton("I feel tired", accent = accent) { say("[BAYMAX] Your body is asking for rest. A short break, water and a stretch can help.") }
                EvButton("Health question", accent = accent) { Ui.input = "I have a health question: " }
                Text("Baymax gives general care tips only, never a diagnosis. For anything severe or sudden, call your local emergency number.", fontSize = 10.sp, color = Color(0xFF9FB4CC), modifier = Modifier.padding(top = 4.dp))
            }
            "karen" -> {
                PanelTitle("PARENT CONTROL")
                Text(KarenStore.statusLine(ctx), fontSize = 11.sp, color = Color(0xFFD8ECF4), modifier = Modifier.padding(bottom = 8.dp))
                EvButton("Open Karen controls (PIN)", accent = accent) { openScreen("karen") }
                EvButton("Scroll counter", accent = accent) { openScreen("scrolls") }
            }
        }
    }
}

@Composable
fun UltronRightPanel(openScreen: (String) -> Unit) {
    val accent = themeColor(Color(0xFFFF2A2A))
    Glass(Modifier.fillMaxWidth(), accent) {
        PanelTitle("RADAR")
        EvButton("Open radar", accent = accent) { openScreen("radar") }
        Text("Wi-Fi networks around you and real places near you (hospitals, police, pharmacies, fuel). Allow location first.",
            fontSize = 10.sp, color = Color(0xFF9FB4CC))
    }
}

// ============================================================ save the conversation?
private fun saveConversation(c: Context): String {
    val dir = java.io.File(c.getExternalFilesDir(null), "conversations").apply { mkdirs() }
    val f = java.io.File(dir, java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US).format(java.util.Date()) + ".txt")
    f.writeText(Ui.messages.joinToString("\n\n") { (if (it.fromUser) "You: " else "EV: ") + it.text })
    return f.absolutePath
}

/** Asks whether to keep the conversation when you clear it or leave the app. */
@Composable
fun SaveChatDialog(onExit: () -> Unit) {
    if (!Ui.askSave) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var never by remember { mutableStateOf(false) }
    fun finish(clear: Boolean) {
        if (never) Prefs.askSaveChat = false
        Ui.askSave = false
        if (clear) { Ai.reset(); Ui.messages.clear() }
        if (Ui.exitAfterSave) { Ui.exitAfterSave = false; onExit() }
    }
    AlertDialog(
        onDismissRequest = { Ui.askSave = false; Ui.exitAfterSave = false },
        title = { Text("Save this conversation?") },
        text = {
            Column {
                Text("Save it as a text file on this phone before it is " + if (Ui.exitAfterSave) "closed?" else "cleared?")
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    androidx.compose.material3.Checkbox(never, { never = it })
                    Text("Don't ask again", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val where = try { saveConversation(ctx) } catch (e: Exception) { "" }
                finish(true)
                if (where.isNotEmpty()) Ui.messages.add(Ui.Msg(false, "Saved to $where"))
            }) { Text("Save") }
        },
        dismissButton = { Row { TextButton(onClick = { finish(true) }) { Text("Don't save") }; TextButton(onClick = { Ui.askSave = false; Ui.exitAfterSave = false }) { Text("Cancel") } } },
    )
}
