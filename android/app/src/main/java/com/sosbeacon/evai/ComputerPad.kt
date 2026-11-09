package com.sosbeacon.evai

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** One key on the phone keyboard. [name] is the key's name on the computer; a plain character has no name. */
private class K(val label: String, val name: String? = null, val w: Float = 1f)

private val SHIFTED = mapOf("`" to "~", "1" to "!", "2" to "@", "3" to "#", "4" to "$", "5" to "%", "6" to "^", "7" to "&",
    "8" to "*", "9" to "(", "0" to ")", "-" to "_", "=" to "+", "[" to "{", "]" to "}", "\\" to "|", ";" to ":", "'" to "\"",
    "," to "<", "." to ">", "/" to "?")

private fun ch(c: String) = K(c)

private val ROWS: List<List<K>> = listOf(
    listOf(K("Esc", "esc", 1.3f)) + (1..12).map { K("F$it", "f$it") } + K("Del", "delete", 1.3f),
    listOf("`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=").map(::ch) + K("Bksp", "backspace", 2f),
    listOf(K("Tab", "tab", 1.5f)) + listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "[", "]", "\\").map(::ch),
    listOf(K("Caps", "capslock", 1.8f)) + listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", ";", "'").map(::ch) + K("Enter", "enter", 2.2f),
    listOf(K("Shift", "shift", 2.3f)) + listOf("z", "x", "c", "v", "b", "n", "m", ",", ".", "/").map(::ch) + K("Shift", "shift", 2.3f),
    listOf(K("Ctrl", "ctrl", 1.4f), K("Win", "win", 1.3f), K("Alt", "alt", 1.3f), K("Space", "space", 6f), K("Alt", "alt", 1.3f),
        K("Ctrl", "ctrl", 1.4f), K("←", "left"), K("↑", "up"), K("↓", "down"), K("→", "right")),
)

private fun setLandscape(act: Activity?, on: Boolean) {
    act?.requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

/** The phone as the computer's keyboard and touchpad: wide, curved keys with a touchpad on the left. */
@Composable
fun KeyboardMode(onClose: () -> Unit) {
    val c = ComputeClient
    val act = LocalContext.current as? Activity
    DisposableEffect(Unit) { setLandscape(act, true); onDispose { setLandscape(act, false) } }
    var shift by remember { mutableStateOf(false) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var win by remember { mutableStateOf(false) }
    val accent = Ui.accentColor()
    val curvePx = with(LocalDensity.current) { 7.dp.toPx() }

    fun press(k: K) {
        when (k.name) {
            "shift" -> { shift = !shift; return }
            "ctrl" -> { ctrl = !ctrl; return }
            "alt" -> { alt = !alt; return }
            "win" -> { win = !win; return }
        }
        val mods = ArrayList<String>()
        if (ctrl) mods.add("ctrl"); if (alt) mods.add("alt"); if (win) mods.add("win")
        if (k.name != null) {
            if (shift) mods.add("shift")
            mods.add(k.name)
            c.send("keys", JSONObject().put("keys", JSONArray(mods)))
        } else if (mods.isNotEmpty()) {
            if (shift) mods.add("shift")
            mods.add(k.label)
            c.send("keys", JSONObject().put("keys", JSONArray(mods)))
        } else {
            val text = if (shift) (SHIFTED[k.label] ?: k.label.uppercase()) else k.label
            c.send("type", JSONObject().put("text", text))
        }
        shift = false; ctrl = false; alt = false; win = false
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF05060A)) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("KEYBOARD", color = accent, fontSize = 12.sp, letterSpacing = 3.sp, modifier = Modifier.weight(1f))
                    if (c.status.isNotEmpty()) Text(c.status, color = Color(0xFF8FB4D8), fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(2f))
                    OutlinedButton(onClick = { c.send("keys", JSONObject().put("keys", JSONArray(listOf("win", "d")))) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), modifier = Modifier.height(30.dp)) { Text("Desktop", fontSize = 11.sp) }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), modifier = Modifier.height(30.dp)) { Text("Close", fontSize = 11.sp) }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // left: touchpad, scroll strip and mouse buttons
                    Column(Modifier.weight(0.32f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Touchpad(Modifier.weight(1f).fillMaxHeight(), accent)
                            ScrollStrip(Modifier.width(30.dp).fillMaxHeight(), accent)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().height(40.dp)) {
                            PadButton("Left click", Modifier.weight(1f), accent) { c.send("click") }
                            PadButton("Right click", Modifier.weight(1f), accent) { c.send("click", JSONObject().put("button", "right")) }
                        }
                    }
                    // right: the curved keyboard
                    Column(Modifier.weight(0.68f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                        ROWS.forEachIndexed { r, row ->
                            Row(Modifier.fillMaxWidth().weight(1f).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                row.forEachIndexed { i, k ->
                                    val mid = (row.size - 1) / 2f
                                    val x = if (mid == 0f) 0f else (i - mid) / mid          // -1 (left end) .. 1 (right end)
                                    val on = when (k.name) { "shift" -> shift; "ctrl" -> ctrl; "alt" -> alt; "win" -> win; else -> false }
                                    val label = if (k.name == null) (if (shift) (SHIFTED[k.label] ?: k.label.uppercase()) else k.label) else k.label
                                    Box(
                                        Modifier.weight(k.w).fillMaxHeight()
                                            .graphicsLayer { translationY = x * x * curvePx; rotationZ = x * 4f }
                                            .background(if (on) accent.copy(alpha = 0.55f) else Color(0xFF0B1A2A), RoundedCornerShape(6.dp))
                                            .border(1.dp, if (on) accent else accent.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                            .clickable { press(k) },
                                        contentAlignment = Alignment.Center,
                                    ) { Text(label, color = Color(0xFFD6ECFA), fontSize = if (label.length > 3) 9.sp else 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, textAlign = TextAlign.Center) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadButton(text: String, modifier: Modifier, accent: Color, onClick: () -> Unit) {
    Box(modifier.fillMaxHeight().background(Color(0xFF0B1A2A), RoundedCornerShape(8.dp)).border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        .clickable { onClick() }, contentAlignment = Alignment.Center) { Text(text, color = Color(0xFFD6ECFA), fontSize = 12.sp) }
}

/** Drag = move the pointer, tap = click, double tap = double click, long press = right click. */
@Composable
private fun Touchpad(modifier: Modifier, accent: Color) {
    val c = ComputeClient
    Box(modifier.background(Color(0xFF08131F), RoundedCornerShape(14.dp)).border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { c.send("click") },
                onDoubleTap = { c.send("click", JSONObject().put("double", true)) },
                onLongPress = { c.send("click", JSONObject().put("button", "right")) })
        }
        .pointerInput(Unit) {
            var ax = 0f; var ay = 0f; var last = 0L
            fun flush() {
                if (ax != 0f || ay != 0f) c.send("move_rel", JSONObject().put("dx", (ax * 1.8f).toInt()).put("dy", (ay * 1.8f).toInt()))
                ax = 0f; ay = 0f; last = System.currentTimeMillis()
            }
            detectDragGestures(onDragEnd = { flush() }, onDrag = { change, d ->
                change.consume(); ax += d.x; ay += d.y
                if (System.currentTimeMillis() - last > 70) flush()
            })
        }, contentAlignment = Alignment.Center) {
        Text("TOUCHPAD", color = accent.copy(alpha = 0.35f), fontSize = 11.sp, letterSpacing = 3.sp)
    }
}

@Composable
private fun ScrollStrip(modifier: Modifier, accent: Color) {
    val c = ComputeClient
    Box(modifier.background(Color(0xFF08131F), RoundedCornerShape(10.dp)).border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
        .pointerInput(Unit) {
            var acc = 0f; var last = 0L
            detectVerticalDragGestures { change, dy ->
                change.consume(); acc += dy
                val now = System.currentTimeMillis()
                if (now - last > 100 && kotlin.math.abs(acc) > 20f) {
                    c.send("scroll", JSONObject().put("dy", (acc / 20f).toInt().coerceIn(-10, 10))); acc = 0f; last = now
                }
            }
        }, contentAlignment = Alignment.Center) { Text("⇅", color = accent.copy(alpha = 0.5f), fontSize = 14.sp) }
}

private fun saveShot(ctx: Context, bmp: Bitmap): Boolean {
    return try {
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "EV-computer-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EV AI")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return false
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    } catch (e: Exception) { false }
}

/** Watch the computer's screen live and control it by touch. */
@Composable
fun MonitorMode(onClose: () -> Unit) {
    val c = ComputeClient
    val ctx = LocalContext.current
    val accent = Ui.accentColor()
    var auto by remember { mutableStateOf(true) }
    var everyMs by remember { mutableStateOf(2500L) }
    var sharp by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    fun shotNow() = c.send("screenshot", JSONObject().put("scale", if (sharp) 0.8 else 0.5).put("q", if (sharp) 65 else 45))
    LaunchedEffect(auto, everyMs, sharp, c.session) {
        while (auto && c.session) { shotNow(); delay(everyMs) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF05060A)) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("MONITORING", color = accent, fontSize = 12.sp, letterSpacing = 3.sp, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { shotNow() }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), modifier = Modifier.height(32.dp)) { Text("Refresh", fontSize = 11.sp) }
                    OutlinedButton(onClick = {
                        val b = c.shot
                        note = if (b == null) "No screen yet." else if (saveShot(ctx, b)) "Saved to Pictures/EV AI." else "Couldn't save the picture."
                    }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), modifier = Modifier.height(32.dp)) { Text("Save", fontSize = 11.sp) }
                    OutlinedButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), modifier = Modifier.height(32.dp)) { Text("Close", fontSize = 11.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Switch(auto, { auto = it }); Text("Live", fontSize = 12.sp)
                    listOf(1000L to "1 s", 2500L to "2.5 s", 5000L to "5 s").forEach { (ms, label) ->
                        FilterChip(selected = everyMs == ms, onClick = { everyMs = ms }, label = { Text(label, fontSize = 11.sp) })
                    }
                    FilterChip(selected = sharp, onClick = { sharp = !sharp }, label = { Text("Sharp", fontSize = 11.sp) })
                }
                if (note.isNotEmpty()) Text(note, color = accent, fontSize = 11.sp)
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                    val bmp = c.shot
                    if (bmp == null) Text("Waiting for the computer's screen...", color = Color(0xFF8FB4D8), modifier = Modifier.padding(24.dp))
                    else {
                        var w by remember { mutableStateOf(1) }; var h by remember { mutableStateOf(1) }
                        Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth()
                            .onSizeChanged { w = it.width.coerceAtLeast(1); h = it.height.coerceAtLeast(1) }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { o -> c.send("click", JSONObject().put("x", o.x / w).put("y", o.y / h)) },
                                    onDoubleTap = { o -> c.send("click", JSONObject().put("x", o.x / w).put("y", o.y / h).put("double", true)) },
                                    onLongPress = { o -> c.send("click", JSONObject().put("x", o.x / w).put("y", o.y / h).put("button", "right")) })
                            }
                            .pointerInput(Unit) {
                                var last = 0L
                                detectDragGestures { chg, _ ->
                                    val now = System.currentTimeMillis()
                                    if (now - last > 120) { last = now; c.send("move", JSONObject().put("x", chg.position.x / w).put("y", chg.position.y / h)) }
                                }
                            })
                    }
                }
                Text("Tap = click, double tap = double click, long press = right click, drag = move.", color = Color(0xFF6F8CA8), fontSize = 10.sp)
            }
        }
    }
}

/** What to do right after connecting. */
@Composable
fun ComputerChooser(onKeyboard: () -> Unit, onMonitor: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Connected to your computer") },
        text = { Text("Do you want to use this phone as a keyboard and touchpad, or to monitor the computer's screen?") },
        confirmButton = { Button(onClick = onKeyboard) { Text("Keyboard") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onMonitor) { Text("Monitoring") }
                TextButton(onClick = onLater) { Text("Not now") }
            }
        })
}

/** Shortcut buttons for the connected computer. */
@Composable
fun ComputerTools() {
    val c = ComputeClient
    var app by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    fun keys(vararg k: String) = c.send("keys", JSONObject().put("keys", JSONArray(k.toList())))
    @Composable fun group(title: String, items: List<Pair<String, () -> Unit>>) {
        Text(title, Modifier.padding(top = 10.dp, bottom = 2.dp), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.take(4).forEach { (t, f) -> OutlinedButton(onClick = f, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.weight(1f)) { Text(t, fontSize = 11.sp, maxLines = 1) } }
        }
        if (items.size > 4) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.drop(4).forEach { (t, f) -> OutlinedButton(onClick = f, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.weight(1f)) { Text(t, fontSize = 11.sp, maxLines = 1) } }
        }
    }
    group("Windows", listOf("Desktop" to { keys("win", "d") }, "Switch" to { keys("alt", "tab") }, "Close" to { keys("alt", "f4") }, "Task view" to { keys("win", "tab") }))
    group("Edit", listOf("Copy" to { keys("ctrl", "c") }, "Paste" to { keys("ctrl", "v") }, "Undo" to { keys("ctrl", "z") }, "Select all" to { keys("ctrl", "a") }))
    group("Media", listOf("Prev" to { keys("prevtrack") }, "Play/Pause" to { keys("playpause") }, "Next" to { keys("nexttrack") }, "Mute" to { c.send("mute") }))
    group("Presentation", listOf("◀" to { keys("left") }, "▶" to { keys("right") }, "Start F5" to { keys("f5") }, "Exit Esc" to { keys("esc") }))
    group("Browser", listOf("New tab" to { keys("ctrl", "t") }, "Close tab" to { keys("ctrl", "w") }, "Refresh" to { keys("f5") }, "Full screen" to { keys("f11") }))
    group("Computer", listOf("Screenshot" to { keys("win", "printscreen") }, "Status" to { c.send("status") }, "Lock" to { c.send("lock") }, "Sleep" to { c.send("sleep") }))
    Text("Open an app on the computer", Modifier.padding(top = 10.dp, bottom = 2.dp), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(app, { app = it }, label = { Text("e.g. chrome, notepad, spotify") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = { if (app.isNotBlank()) { c.send("open_app", JSONObject().put("name", app.trim())); app = "" } }) { Text("Open") }
    }
    OutlinedButton(onClick = {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val t = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
        if (t.isNotBlank()) c.send("type", JSONObject().put("text", t.take(300))) else c.status = "Your phone's clipboard is empty."
    }, modifier = Modifier.padding(top = 6.dp)) { Text("Type my phone's clipboard on the computer") }
}
