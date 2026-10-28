package com.sosbeacon.evai

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PANEL = Color(0xA50E121C)
private val TITLE = Color(0xFF9FB4CC)
private val TEXT = Color(0xFFD8ECF4)

private fun leftAccent() = if (Ui.jarvis) Color(0xFFFFC400) else Color(0xFFF0465A)
private fun rightAccent() = if (Ui.jarvis) Color(0xFFFFC400) else Color(0xFF3C8CF0)

@Composable
fun Glass(modifier: Modifier = Modifier, border: Color, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.clip(shape).background(PANEL).border(1.dp, border.copy(alpha = 0.3f), shape).padding(12.dp),
        content = content,
    )
}

@Composable
fun PanelTitle(text: String) {
    Text(text, fontSize = 10.sp, color = TITLE, letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
fun EvButton(text: String, modifier: Modifier = Modifier, accent: Color = Color(0xFF5A96DC), selected: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.fillMaxWidth().padding(bottom = 6.dp).heightIn(min = 40.dp).clip(shape)
            .background(accent.copy(alpha = if (selected) 0.35f else 0.12f))
            .border(1.dp, accent.copy(alpha = if (selected) 0.8f else 0.35f), shape)
            .clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), fontSize = 10.sp, letterSpacing = 1.sp, color = if (selected) Color.White else Color(0xFFC6DCF4))
    }
}

@Composable
fun MiniBar(label: String, pct: Int, detail: String, accent: Color) {
    Column(Modifier.padding(bottom = 7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 10.sp, color = TITLE, letterSpacing = 1.sp)
            Text(detail, fontSize = 10.sp, color = TEXT)
        }
        Spacer(Modifier.height(3.dp))
        val warn = pct >= 90
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x40000000))) {
            Box(Modifier.fillMaxWidth(pct.coerceIn(0, 100) / 100f).fillMaxHeight().background(if (warn) Color(0xFFFF5252) else accent))
        }
    }
}

fun toggleJarvis(scope: CoroutineScope) {
    val on = !Ui.jarvis
    Ui.jarvis = on
    Prefs.jarvis = on
    val msg = if (on) "Switched to Jarvis." else "Switched back to EV."
    Ui.messages.add(Ui.Msg(false, msg))
    if (Prefs.speak) Voice.speak(msg)
    scope.launch { Ui.coreState = CoreState.SPEAKING; delay(1500); if (Ui.coreState == CoreState.SPEAKING) Ui.coreState = CoreState.IDLE }
}

@Composable
fun Dashboard(openScreen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val portrait = maxWidth < maxHeight
        Column(Modifier.fillMaxSize()) {
            TopBar(openScreen)
            if (portrait) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CorePanel(Modifier.fillMaxWidth().height(270.dp), scope)
                    ChatPanel(Modifier.fillMaxWidth().height(330.dp), scope)
                    StatusPanel()
                    SystemPanel()
                    QuickPanel(scope)
                    if (!Ui.jarvis) ToolsPanel(openScreen)
                }
            } else {
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(0.26f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPanel()
                        SystemPanel()
                        if (!Ui.jarvis) ToolsPanel(openScreen)
                    }
                    Column(Modifier.weight(0.48f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CorePanel(Modifier.fillMaxWidth().weight(1f), scope)
                        ChatPanel(Modifier.fillMaxWidth().weight(1f), scope)
                    }
                    Column(Modifier.weight(0.26f).fillMaxHeight().verticalScroll(rememberScrollState())) { QuickPanel(scope) }
                }
            }
        }
    }
}

@Composable
fun TopBar(openScreen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(Color(0xD20A0C14)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ev_logo), null, Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (Ui.jarvis) "J.A.R.V.I.S" else "EV AI", fontSize = 18.sp,
            color = if (Ui.jarvis) Color(0xFFFFC400) else Color(0xFFE8F6FC), letterSpacing = 4.sp, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Text(if (Ui.jarvis) "SYSTEM MONITOR ACTIVE" else "SYSTEM STATUS: ONLINE", fontSize = 10.sp,
            color = if (Ui.jarvis) Color(0xFFFFC400) else Color(0xFF5FD0E8), letterSpacing = 2.sp,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, Color(0x555A96DC), RoundedCornerShape(16.dp))
                .clickable { openScreen("settings") }.padding(horizontal = 14.dp, vertical = 6.dp)
        ) { Text("SETTINGS", fontSize = 10.sp, letterSpacing = 1.sp, color = Color(0xFFC6DCF4), maxLines = 1) }
    }
}

/** State + provider. Shown in both EV and Jarvis modes. */
@Composable
fun StatusPanel() {
    val accent = leftAccent()
    Glass(Modifier.fillMaxWidth(), accent) {
        PanelTitle("STATUS")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(if (Ui.jarvis) Color(0xFFFFC400) else Color(Ui.coreState.hex)))
            Spacer(Modifier.width(8.dp))
            Text(Ui.coreState.label, fontSize = 13.sp, color = Color(0xFFEAF4FA), letterSpacing = 2.sp)
        }
        Text(if (Ui.busy && Ui.status.isNotEmpty()) Ui.status else "Ready", fontSize = 11.sp, color = TEXT, modifier = Modifier.padding(top = 6.dp))
        Text("MODE: ${if (Ui.jarvis) "JARVIS" else "EV"}", fontSize = 10.sp, color = if (Ui.jarvis) Color(0xFFFFC400) else TITLE, letterSpacing = 1.sp, modifier = Modifier.padding(top = 4.dp))
        Text("AI: ${Prefs.provider}", fontSize = 10.sp, color = TITLE, letterSpacing = 1.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun SystemPanel() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(3000); tick++ } }
    val accent = leftAccent()
    Glass(Modifier.fillMaxWidth(), accent) {
        PanelTitle(if (Ui.jarvis) "JARVIS SYSTEM MONITOR" else "SYSTEM")
        key(tick) {
            if (Ui.jarvis) {
                MiniBar("BATTERY", Sys.batteryPct(ctx), "${Sys.batteryPct(ctx)}%${if (Sys.charging(ctx)) " ⚡" else ""}", accent)
                MiniBar("MEMORY", Sys.ramPct(ctx), Sys.ramText(ctx), accent)
                MiniBar("STORAGE", Sys.storagePct(), Sys.storageText(), accent)
                Text("NETWORK: ${Sys.network(ctx)}", fontSize = 10.sp, color = TEXT, modifier = Modifier.padding(top = 2.dp))
                Text("UPTIME: ${Sys.uptime()}", fontSize = 10.sp, color = TEXT, modifier = Modifier.padding(top = 2.dp))
            } else {
                MiniBar("BATTERY", Sys.batteryPct(ctx), "${Sys.batteryPct(ctx)}%", accent)
                MiniBar("MEMORY", Sys.ramPct(ctx), "${Sys.ramPct(ctx)}%", accent)
                MiniBar("STORAGE", Sys.storagePct(), "${Sys.storagePct()}%", accent)
            }
        }
    }
}

@Composable
fun ToolsPanel(openScreen: (String) -> Unit) {
    val red = leftAccent()
    Glass(Modifier.fillMaxWidth(), red) {
        PanelTitle("TOOLS")
        EvButton("⚡ Quick Actions", accent = red) { openScreen("actions") }
        EvButton("🎯 Focus", accent = red) { openScreen("focus") }
        EvButton("📊 App Insights", accent = red) { openScreen("insights") }
        EvButton("🔎 File Finder", accent = red) { openScreen("files") }
        EvButton("💻 Computer", accent = red) { openScreen("computer") }
        EvButton("🩺 Error Doctor", accent = red) { openScreen("doctor") }
    }
}

@Composable
fun QuickPanel(scope: CoroutineScope) {
    val blue = rightAccent()
    Glass(Modifier.fillMaxWidth(), blue) {
        if (Ui.jarvis) {
            PanelTitle("JARVIS CHECKS")
            for ((label, cmd) in listOf(
                "Full Status Report" to "give me a full status report of this phone",
                "Battery Check" to "check my battery",
                "Memory & Storage" to "how much memory and storage is left",
                "Network Check" to "check my network connection",
                "Screen Time" to "which apps did I use the most today",
                "Health Check" to "run a health check on my phone and tell me if anything needs attention",
            )) EvButton(label, accent = blue) { sendMessage(cmd, scope) }
        } else {
            PanelTitle("QUICK ACTIONS")
            for ((label, cmd) in listOf(
                "Open YouTube" to "open youtube",
                "Search the Web" to "",
                "Phone Info" to "what is my battery and storage",
                "Morning Briefing" to "morning briefing",
            )) EvButton(label, accent = blue) { if (cmd.isEmpty()) Ui.input = "search the web for " else sendMessage(cmd, scope) }
            EvButton("Clear Conversation", accent = blue) { Ai.reset(); Ui.messages.clear() }
        }
    }
}

@Composable
fun CorePanel(modifier: Modifier, scope: CoroutineScope) {
    Glass(modifier, Color(0xFF8C96B4)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            EvButton(if (Ui.jarvis) "⚡ Switch back to EV" else "⚡ Switch to Jarvis",
                modifier = Modifier.fillMaxWidth(0.7f), accent = if (Ui.jarvis) Color(0xFFFFC400) else Color(0xFF5A96DC),
                selected = Ui.jarvis) { toggleJarvis(scope) }
            AiCore(Modifier.weight(1f).fillMaxWidth())
            Text(if (Ui.jarvis && Ui.coreState == CoreState.IDLE) "MONITORING" else Ui.coreState.label, fontSize = 10.sp, letterSpacing = 2.sp,
                color = if (Ui.jarvis) Color(0xFFFFC400) else Color(Ui.coreState.hex))
        }
    }
}

@Composable
fun ChatPanel(modifier: Modifier, scope: CoroutineScope) {
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!said.isNullOrBlank()) sendMessage(said, scope) else Ui.coreState = CoreState.IDLE
    }
    LaunchedEffect(Ui.messages.size) { if (Ui.messages.isNotEmpty()) list.animateScrollToItem(Ui.messages.size - 1) }
    LaunchedEffect(Ui.input) { if (Ui.input.isNotEmpty()) { input = Ui.input; Ui.input = "" } }
    val accent = rightAccent()

    Glass(modifier, Color(0xFF8C96B4)) {
        PanelTitle("CONVERSATION")
        if (Ui.messages.isEmpty()) {
            Text("Try: call mom · set a timer for 10 minutes · open YouTube · what's my battery",
                fontSize = 12.sp, color = TITLE, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(Ui.messages) { m ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
                        Box(
                            Modifier.widthIn(max = 420.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (m.fromUser) accent.copy(alpha = 0.18f) else Color(0x22FFFFFF))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) { Text(m.text, fontSize = 12.sp, color = if (m.fromUser) Color(0xFFBFE0FF) else TEXT) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, Modifier.weight(1f).heightIn(min = 48.dp),
                singleLine = true, placeholder = { Text("Ask EV…", fontSize = 12.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = TEXT),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent, unfocusedBorderColor = Color(0x555A96DC), cursorColor = accent),
            )
            Spacer(Modifier.width(6.dp))
            EvButton("Mic", Modifier.width(58.dp)) {
                try {
                    Ui.coreState = CoreState.LISTENING
                    mic.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
                } catch (e: Exception) {
                    Ui.coreState = CoreState.IDLE
                    Ui.messages.add(Ui.Msg(false, "Voice input isn't available on this phone."))
                }
            }
            Spacer(Modifier.width(6.dp))
            EvButton("Send", Modifier.width(62.dp), accent = Color(0xFFF0465A)) { sendMessage(input, scope); input = "" }
        }
    }
}

/** Header + back button around the other screens. */
@Composable
fun ScreenFrame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(40.dp).background(Color(0xD20A0C14)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, Color(0x555A96DC), RoundedCornerShape(16.dp))
                .clickable(onClick = onBack).padding(horizontal = 14.dp, vertical = 5.dp)) {
                Text("← BACK", fontSize = 10.sp, letterSpacing = 1.sp, color = Color(0xFFC6DCF4))
            }
            Spacer(Modifier.width(12.dp))
            Text(title.uppercase(), fontSize = 12.sp, letterSpacing = 3.sp, color = Color(0xFFE8F6FC))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}
