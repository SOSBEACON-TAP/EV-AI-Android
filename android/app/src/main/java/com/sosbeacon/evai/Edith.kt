package com.sosbeacon.evai

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** EDITH: the screen shrinks away, then a heads-up display (brackets, reticle, scan line, clock) draws over the phone. */
object Edith {
    var t0 by mutableLongStateOf(0L)
    fun begin() { t0 = SystemClock.uptimeMillis() }
}

private val CYAN = Color(0xFF40E0FF)

@Composable
fun EdithOverlay() {
    if (Ui.mode != "edith") return
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { now = SystemClock.uptimeMillis() } }
    val t = (now - Edith.t0).coerceAtLeast(0L)
    val shrink = (t / 900f).coerceIn(0f, 1f)          // 0..1: the old screen collapses
    val hud = ((t - 700L) / 700f).coerceIn(0f, 1f)    // then the HUD fades in
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (shrink < 1f) {                                         // a dark frame closes in on the middle, then vanishes
                val k = 1f - shrink
                val ww = w * k
                val hh = h * k * k
                drawRect(Color(0xFF020812).copy(alpha = 0.9f * (1f - shrink * 0.6f)), Offset((w - ww) / 2f, (h - hh) / 2f), Size(ww, hh))
                drawRect(CYAN.copy(alpha = 0.8f * k), Offset((w - ww) / 2f, (h - hh) / 2f), Size(ww, hh), style = Stroke(2f))
            }
            val a = hud
            if (a > 0f) {
                val m = 14f
                val L = 36f
                val col = CYAN.copy(alpha = 0.85f * a)
                for ((sx, sy) in listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)) {
                    val x = if (sx == 0f) m else w - m
                    val y = if (sy == 0f) m else h - m
                    val dx = if (sx == 0f) L else -L
                    val dy = if (sy == 0f) L else -L
                    drawLine(col, Offset(x, y), Offset(x + dx, y), 3f)
                    drawLine(col, Offset(x, y), Offset(x, y + dy), 3f)
                }
                val c = Offset(w / 2f, h / 2f)
                val rr = minOf(w, h) * 0.16f
                val spin = now / 1000f * 40f
                drawCircle(col.copy(alpha = 0.35f * a), rr, c, style = Stroke(1.5f))
                for (n in 0 until 4) drawArc(col, spin + n * 90f, 50f, false, Offset(c.x - rr * 1.25f, c.y - rr * 1.25f), Size(rr * 2.5f, rr * 2.5f), style = Stroke(2.5f))
                drawLine(col.copy(alpha = 0.5f * a), Offset(c.x - rr * 0.4f, c.y), Offset(c.x + rr * 0.4f, c.y), 1.5f)
                drawLine(col.copy(alpha = 0.5f * a), Offset(c.x, c.y - rr * 0.4f), Offset(c.x, c.y + rr * 0.4f), 1.5f)
                val sy = ((now % 3500L) / 3500f) * h                  // a scan line sweeping down
                drawLine(CYAN.copy(alpha = 0.25f * a), Offset(0f, sy), Offset(w, sy), 2f)
            }
        }
        if (hud > 0f) {
            val clock = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            Text("EDITH  //  $clock", color = CYAN.copy(alpha = hud), fontSize = 11.sp, letterSpacing = 3.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp))
        }
    }
}

/** Baymax's body: tap a part to start your question about it. General care tips only, never a diagnosis. */
@Composable
fun BodyHud(accent: Color) {
    var picked by remember { androidx.compose.runtime.mutableStateOf("") }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { tick = it } }
    Box(Modifier.fillMaxWidth().height(190.dp)) {
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { p ->
                val sc = minOf(size.width / 100f, size.height / 200f)
                val ox = (size.width - 100f * sc) / 2f
                val x = (p.x - ox) / sc
                val y = p.y / sc
                val zone = when {
                    y in 4f..32f && x in 38f..62f -> "head"
                    y in 40f..76f && x in 31f..69f -> "chest"
                    y in 76f..110f && x in 33f..67f -> "stomach"
                    y in 40f..112f && x in 8f..28f -> "left arm"
                    y in 40f..112f && x in 72f..92f -> "right arm"
                    y in 112f..196f && x in 33f..50f -> "left leg"
                    y in 112f..196f && x in 50f..67f -> "right leg"
                    else -> ""
                }
                if (zone.isNotEmpty()) {
                    picked = zone
                    Ui.messages.add(Ui.Msg(false, "[BAYMAX] Your $zone. Tell me what you feel there and for how long. I can only give general care tips, not a diagnosis. If it is severe or sudden, please call your local emergency number."))
                    Ui.input = "I feel something in my $zone: "
                }
            }
        }) {
            val sc = minOf(size.width / 100f, size.height / 200f)
            val ox = (size.width - 100f * sc) / 2f
            val frame = tick
            fun r(x: Float, y: Float, w: Float, h: Float, rad: Float) =
                drawRoundRect(accent.copy(alpha = 0.9f), Offset(ox + x * sc, y * sc), Size(w * sc, h * sc), androidx.compose.ui.geometry.CornerRadius(rad * sc), style = Stroke(1.6f))
            drawOval(accent.copy(alpha = 0.9f), Offset(ox + 40f * sc, 5f * sc), Size(20f * sc, 24f * sc), style = Stroke(1.6f))
            r(46f, 29f, 8f, 8f, 2f); r(31f, 37f, 38f, 42f, 8f); r(33f, 76f, 34f, 36f, 8f)
            r(11f, 40f, 15f, 70f, 7f); r(74f, 40f, 15f, 70f, 7f); r(34f, 110f, 14f, 84f, 7f); r(52f, 110f, 14f, 84f, 7f)
            val beat = 1f + 0.18f * maxOf(0f, sin((frame / 1_000_000L) / 160f)).let { it * it * it }
            drawCircle(accent, 3.2f * sc * beat, Offset(ox + 56f * sc, 50f * sc))
            drawLine(accent.copy(alpha = 0.5f), Offset(ox + 6f * sc, (((frame / 1_000_000L) % 3500L) / 3500f) * 196f * sc + 4f * sc),
                Offset(ox + 94f * sc, (((frame / 1_000_000L) % 3500L) / 3500f) * 196f * sc + 4f * sc), 2f)
        }
        Text(if (picked.isEmpty()) "TOUCH WHERE YOU FEEL SOMETHING" else picked.uppercase(), fontSize = 8.sp, letterSpacing = 1.sp, color = accent,
            modifier = Modifier.align(Alignment.TopCenter))
    }
}
