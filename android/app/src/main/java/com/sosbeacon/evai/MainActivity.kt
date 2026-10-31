package com.sosbeacon.evai

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val askPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Voice.init()
        val wanted = mutableListOf(
            Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS,
        )
        if (Build.VERSION.SDK_INT >= 33) {
            wanted += listOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else wanted += Manifest.permission.READ_EXTERNAL_STORAGE
        if (!Prefs.introDone) { askPerms.launch(wanted.toTypedArray()); Prefs.introDone = true }
        if (Prefs.listen) CallService.start(this)
        if (Prefs.autoUpdate && Updater.canInstall(this)) {
            Thread { try { Updater.run(applicationContext) } catch (e: Exception) { } }.also { it.isDaemon = true; it.start() }
        }
        setContent { EvRoot() }
    }
}

@Composable
fun LaunchSplash(onDone: () -> Unit) {
    val steps = listOf("BOOTING CORE SYSTEMS", "INITIALIZING NEURAL LINK", "CALIBRATING SENSORS",
        "LOADING AI MODULES", "SYNCING MEMORY BANKS", "ALL SYSTEMS ONLINE")
    var p by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (p < 1f) { kotlinx.coroutines.delay(35); p = (p + 0.04f).coerceAtMost(1f) }
        kotlinx.coroutines.delay(150); onDone()
    }
    val accent = Ui.accentColor()
    Box(Modifier.fillMaxSize().background(Color(0xFF05060A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Image(painterResource(R.drawable.ev_logo), null, Modifier.size(96.dp))
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val on = (p * 20).toInt()
                for (i in 0 until 20) Box(Modifier.width(12.dp).height(10.dp)
                    .background(if (i < on) accent else Color(0xFF1B2233)))
            }
            Spacer(Modifier.height(14.dp))
            Text(steps[((p * (steps.size - 1)).toInt()).coerceIn(0, steps.lastIndex)] + ".".repeat((p * 40).toInt() % 4),
                color = accent, fontSize = 13.sp, letterSpacing = 3.sp)
        }
    }
}

@Composable
fun EvRoot() {
    val accent = Ui.accentColor()
    val dark = Ui.theme != "light"
    val bg = when (Ui.theme) { "amoled" -> Color.Black; "light" -> Color(0xFFF5F7FA); else -> Color(0xFF0A0F1C) }
    val surface = when (Ui.theme) { "amoled" -> Color(0xFF0D0D0D); "light" -> Color.White; else -> Color(0xFF131B2E) }
    val scheme = if (dark) darkColorScheme(primary = accent, background = bg, surface = surface, onPrimary = Color.Black)
    else lightColorScheme(primary = accent, background = bg, surface = surface, onPrimary = Color.Black)
    val d = LocalDensity.current
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            val ctl = androidx.core.view.WindowCompat.getInsetsController(window, view)
            ctl.isAppearanceLightStatusBars = !dark
            ctl.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * Ui.fontScale / 100f)) {
        MaterialTheme(colorScheme = scheme) {
            var screen by rememberSaveable { mutableStateOf("home") }
            val act = view.context as? android.app.Activity
            LaunchedEffect(Ui.orientation) {
                act?.requestedOrientation = when (Ui.orientation) {
                    "portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    "auto" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
            val dashBg = androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(if (Ui.jarvis) Color(0xFF1A1405) else Color(0xFF10131E),
                    if (Ui.jarvis) Color(0xFF0E0A02) else Color(0xFF0A0C15), Color(0xFF05060A)))
            val frameBg = if (screen == "home") Modifier.background(dashBg) else Modifier.background(bg)
            var splash by rememberSaveable { mutableStateOf(true) }
            if (splash) { LaunchSplash { splash = false }; return@MaterialTheme }
            Column(Modifier.fillMaxSize().then(frameBg).safeDrawingPadding()) {
                if (screen == "home") Dashboard { screen = it }
                else {
                    androidx.activity.compose.BackHandler { screen = "home" }
                    val title = when (screen) {
                        "actions" -> "Quick Actions"; "focus" -> "Focus"; "insights" -> "App Insights"
                        "files" -> "File Finder"; "computer" -> "Computer"; "doctor" -> "Error Doctor"; else -> "Settings"
                    }
                    ScreenFrame(title, { screen = "home" }) {
                        when (screen) {
                            "actions" -> ActionsScreen()
                            "focus" -> FocusScreen()
                            "insights" -> InsightsScreen()
                            "files" -> FilesScreen()
                            "computer" -> ComputerScreen()
                            "doctor" -> DoctorScreen { screen = "settings" }
                            else -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}

fun sendMessage(text: String, scope: CoroutineScope) {
    if (text.isBlank() || Ui.busy) return
    Ui.messages.add(Ui.Msg(true, text.trim()))
    Ui.busy = true; Ui.status = "Thinking…"; Ui.coreState = CoreState.THINKING
    scope.launch {
        val reply = Ai.chat(text.trim()) { Ui.status = "Using ${it.replace('_', ' ')}…"; Ui.coreState = CoreState.EXECUTING }
        Ui.messages.add(Ui.Msg(false, reply))
        Ui.busy = false; Ui.status = ""
        if (Prefs.speak) Voice.speak(reply)
        Ui.coreState = CoreState.SPEAKING
        kotlinx.coroutines.delay((reply.length * 55L).coerceIn(1200L, 7000L))
        if (!Ui.busy) Ui.coreState = CoreState.IDLE
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Text(title.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
fun ActionsScreen() {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val chains = remember(tick) { Store.chains() }
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var note by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Section("Quick Actions") {
            Text("One word that does several things. Each line is a step: app Spotify · site github.com · say Good morning · volume 40 · home",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            Spacer(Modifier.height(8.dp))
            Button(onClick = { editing = "" to "" }) { Text("New quick action") }
            if (note.isNotEmpty()) Text(note, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary)
        }
        chains.forEach { (name, steps) ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(name, fontSize = 17.sp)
                    Text(steps.joinToString("\n"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Button(onClick = { scope.launch { note = withContext(Dispatchers.IO) { Tools.runChain(name) } } }) { Text("Run") }
                        OutlinedButton(onClick = { editing = name to steps.joinToString("\n") }) { Text("Edit") }
                        OutlinedButton(onClick = { Store.deleteChain(name); tick++ }) { Text("Delete") }
                    }
                }
            }
        }
    }
    editing?.let { (n0, s0) ->
        var name by remember { mutableStateOf(n0) }
        var steps by remember { mutableStateOf(s0) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Quick action") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. study mode)") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(steps, { steps = it }, label = { Text("Steps, one per line") }, minLines = 4)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val l = steps.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    if (name.isNotBlank() && l.isNotEmpty()) { if (n0.isNotEmpty() && !n0.equals(name, true)) Store.deleteChain(n0); Store.saveChain(name, l); tick++ }
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun FocusScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(Prefs.focusActive) }
    var blocked by remember { mutableStateOf(Prefs.focusBlocked) }
    var chain by remember { mutableStateOf(Prefs.focusChain) }
    var msg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Section("Focus mode") {
            Text(if (active) "Focus mode is ON" else "Focus mode is off", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                if (active) { Focus.stop(ctx); active = false; msg = "Focus mode is off." }
                else scope.launch { msg = withContext(Dispatchers.IO) { Tools.startFocus() }; active = Prefs.focusActive }
            }) { Text(if (active) "Turn off" else "Turn on") }
            if (msg.isNotEmpty()) Text(msg, Modifier.padding(top = 8.dp))
        }
        Section("Apps to block") {
            OutlinedTextField(blocked, { blocked = it; Prefs.focusBlocked = it }, Modifier.fillMaxWidth(), label = { Text("Comma separated, e.g. youtube, scratch, discord") })
            Text("Matches app names. When one opens, EV sends you back to the home screen. Needs Usage access and the EV AI Accessibility setting.",
                Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
        Section("Quick action to run on start") {
            OutlinedTextField(chain, { chain = it; Prefs.focusChain = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Quick action name (optional)") })
        }
    }
}

@Composable
fun InsightsScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val ok = remember(tick) { Usage.hasAccess(ctx) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (!ok) {
            Section("App Usage Insights") {
                Text("Allow Usage access so EV can show how long you spend in each app. The data stays on your phone.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { Usage.openSettings(ctx) }) { Text("Allow usage access") }
                OutlinedButton(onClick = { tick++ }, modifier = Modifier.padding(top = 8.dp)) { Text("I allowed it, refresh") }
            }
            return@Column
        }
        val today = remember(tick) { Usage.perApp(ctx, Usage.startOfDay(0), System.currentTimeMillis()) }
        val week = remember(tick) { Usage.perApp(ctx, Usage.startOfDay(6), System.currentTimeMillis()) }
        val trend = remember(tick) { (6 downTo 0).map { d -> Usage.perApp(ctx, Usage.startOfDay(d), Usage.startOfDay(d - 1).takeIf { d > 0 } ?: System.currentTimeMillis()).sumOf { it.second } } }
        Section("Today · ${Usage.fmt(today.sumOf { it.second })}") { UsageBars(today.take(8)) }
        Section("Last 7 days · ${Usage.fmt(week.sumOf { it.second })}") { UsageBars(week.take(8)) }
        Section("Daily total, oldest to newest") {
            val max = (trend.maxOrNull() ?: 1L).coerceAtLeast(1L)
            Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                trend.forEach { v ->
                    Box(Modifier.weight(1f).fillMaxHeight((v.toFloat() / max).coerceIn(0.03f, 1f)).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                }
            }
        }
        OutlinedButton(onClick = { tick++ }) { Text("Refresh") }
    }
}

@Composable
fun UsageBars(l: List<Pair<String, Long>>) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (l.isEmpty()) { Text("Nothing recorded yet."); return }
    val max = l.first().second.toFloat()
    l.forEach { (pkg, ms) ->
        Column(Modifier.padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(Usage.label(ctx, pkg)); Text(Usage.fmt(ms)) }
            Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxWidth(ms / max).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
            }
        }
    }
}

@Composable
fun FilesScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FoundFile>?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Section("AI File Finder") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(q, { q = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("e.g. photos, pdf resume, music") })
                Spacer(Modifier.width(6.dp))
                Button(onClick = { scope.launch { results = withContext(Dispatchers.IO) { Finder.search(ctx, q) } } }) { Text("Find") }
            }
            Text("Searches file names of photos, videos, music, documents and downloads that Android lets EV see.",
                Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
        results?.let { r ->
            if (r.isEmpty()) Text("No files found.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(r) { f ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Finder.describe(f), Modifier.weight(1f))
                            TextButton(onClick = { Finder.open(ctx, f) }) { Text("Open") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DoctorScreen(goSettings: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val checks = remember(tick) { Doctor.run(ctx) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Section("Error Doctor") {
            Text(if (checks.all { it.ok }) "Everything looks good." else "${checks.count { !it.ok }} thing(s) need attention.", fontSize = 18.sp)
            OutlinedButton(onClick = { tick++ }, modifier = Modifier.padding(top = 8.dp)) { Text("Check again") }
        }
        checks.forEach { c ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text((if (c.ok) "OK  " else "FIX  ") + c.name, color = if (c.ok) MaterialTheme.colorScheme.primary else Color(0xFFFF6B6B))
                    if (!c.ok) {
                        Text(c.fix, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        if (c.action != "none") TextButton(onClick = {
                            when (c.action) {
                                "settings" -> goSettings()
                                "usage" -> Usage.openSettings(ctx)
                                "access" -> ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                else -> ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }) { Text("Fix it") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var provider by remember { mutableStateOf(Prefs.provider) }
    var key by remember { mutableStateOf(Prefs.apiKey) }
    var model by remember { mutableStateOf(Prefs.model) }
    var baseUrl by remember { mutableStateOf(Prefs.baseUrl) }
    var name by remember { mutableStateOf(Prefs.userName) }
    var speak by remember { mutableStateOf(Prefs.speak) }
    var confirm by remember { mutableStateOf(Prefs.confirmSend) }
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Section("AI") {
            Box {
                OutlinedButton(onClick = { menu = true }) { Text("Provider: $provider") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Ai.BASES.keys.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { provider = p; Prefs.provider = p; menu = false }) }
                }
            }
            OutlinedTextField(key, { key = it; Prefs.apiKey = it.trim() }, Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            OutlinedTextField(model, { model = it; Prefs.model = it.trim() }, Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Model (blank = ${Ai.MODELS[provider] ?: "your own"})") }, singleLine = true)
            if (provider == "custom") OutlinedTextField(baseUrl, { baseUrl = it; Prefs.baseUrl = it.trim() }, Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Base URL (OpenAI style)") }, singleLine = true)
        }
        Section("You") {
            OutlinedTextField(name, { name = it; Prefs.userName = it }, Modifier.fillMaxWidth(), label = { Text("Your name") }, singleLine = true)
        }
        Section("Personality and voice") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Personality.ALL.forEach { (k, v) ->
                    FilterChip(selected = Ui.personality == k, onClick = { Prefs.personality = k; Ui.personality = k }, label = { Text(v.first) })
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(speak, { speak = it; Prefs.speak = it }); Spacer(Modifier.width(8.dp)); Text("Speak replies aloud")
            }
            OutlinedButton(onClick = { Voice.speak("Hi, this is how I sound in ${Personality.ALL[Personality.current()]?.first} mode.") }) { Text("Preview voice") }
        }
        Section("Behaviour") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(confirm, { confirm = it; Prefs.confirmSend = it }); Spacer(Modifier.width(8.dp))
                Text("Ask before sending texts, emails and WhatsApp messages")
            }
            Text("Off by default so EV works like Jarvis: say it and it's sent.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            OutlinedButton(onClick = { Ai.reset(); Ui.messages.clear() }, modifier = Modifier.padding(top = 8.dp)) { Text("Clear conversation") }
        }
        Section("Look") {
            Text("Screen layout")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("landscape" to "Landscape", "portrait" to "Portrait", "auto" to "Auto-rotate").forEach { (k, n) ->
                    FilterChip(selected = Ui.orientation == k, onClick = { Ui.orientation = k; Prefs.orientation = k }, label = { Text(n) })
                }
            }
            Text("Theme", Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ui.THEMES.forEach { (k, n) -> FilterChip(selected = Ui.theme == k, onClick = { Ui.theme = k; Prefs.theme = k }, label = { Text(n) }) }
            }
            Text("Accent colour", Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ui.ACCENTS.forEach { (hex, n) -> FilterChip(selected = Ui.accent == hex, onClick = { Ui.accent = hex; Prefs.accent = hex }, label = { Text(n) }) }
            }
            Text("Text size: ${Ui.fontScale}%", Modifier.padding(top = 8.dp))
            Slider(value = Ui.fontScale.toFloat(), onValueChange = { Ui.fontScale = it.toInt() }, onValueChangeFinished = { Prefs.fontScale = Ui.fontScale }, valueRange = 80f..140f)
        }
        Section("Permissions") {
            Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Accessibility (auto-send, block apps)") }
            OutlinedButton(onClick = { Usage.openSettings(ctx) }, modifier = Modifier.padding(top = 8.dp)) { Text("Usage access") }
            OutlinedButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, modifier = Modifier.padding(top = 8.dp)) { Text("App permissions") }
        }
        Section("Updates") {
            var updMsg by remember { mutableStateOf(Prefs.updateNote) }
            var auto by remember { mutableStateOf(Prefs.autoUpdate) }
            val scope = rememberCoroutineScope()
            Text("Installed build ${Updater.installedCode(ctx)}", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Switch(auto, { auto = it; Prefs.autoUpdate = it }); Spacer(Modifier.width(8.dp)); Text("Update automatically")
            }
            Button(onClick = {
                if (!Updater.canInstall(ctx)) {
                    updMsg = "Allow EV AI to install updates, then press Check again."
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    updMsg = "Checking…"
                    scope.launch { updMsg = withContext(Dispatchers.IO) { Updater.run(ctx) } }
                }
            }, modifier = Modifier.padding(top = 8.dp)) { Text("Check for updates") }
            if (updMsg.isNotEmpty()) Text(updMsg, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary)
        }
        Text("EV AI for Android · 1.0.6", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
    }
}

@Composable
fun ComputerScreen() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val code = remember { DeviceCode.mine() }
    var listen by remember { mutableStateOf(Prefs.listen) }
    var note by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Section("Connect to your computer") {
            Text("1. Open EV AI on your computer.\n2. Go to Settings, Connected device.\n3. Type this code and press Connect.")
            Spacer(Modifier.height(12.dp))
            Text(code, fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("EV code", code)); note = "Code copied."
            }) { Text("Copy code") }
            Text("Keep this code private. Anyone with it can send call alerts to this phone.",
                Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
        ComputeSection()
        Section("Incoming calls from your computer") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(listen, {
                    listen = it; Prefs.listen = it
                    if (it) CallService.start(ctx) else CallService.stop(ctx)
                })
                Spacer(Modifier.width(8.dp))
                Text("Ring me when my computer switches off mid-task")
            }
            Text("If you give EV a task and then switch your computer off before it finishes, this phone rings like a call, over the internet. No SIM balance needed.",
                Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
        Section("Make sure it always rings") {
            Button(onClick = {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) { note = "Open Settings, Battery, and let EV AI run in the background." }
            }) { Text("Allow background running") }
            if (Build.VERSION.SDK_INT >= 34) {
                OutlinedButton(onClick = {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) { note = "Open Settings, Apps, EV AI, and allow full-screen notifications." }
                }, modifier = Modifier.padding(top = 8.dp)) { Text("Allow full-screen calls") }
            }
            OutlinedButton(onClick = { IncomingCall.show(ctx, "This is a test call from EV AI."); note = "Test call started." },
                modifier = Modifier.padding(top = 8.dp)) { Text("Test a call now") }
        }
        if (note.isNotEmpty()) Text(note, color = MaterialTheme.colorScheme.primary)
    }
}
