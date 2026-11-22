package com.sosbeacon.evai

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Brush
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

/** Baymax's body: your X-ray picture; tap a part to start your question about it. General care tips only, never a diagnosis. */
@Composable
fun BodyHud(accent: Color) {
    var picked by remember { androidx.compose.runtime.mutableStateOf("") }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { tick = it } }
    val zones = listOf(
        "head" to floatArrayOf(0.39f, 0.03f, 0.62f, 0.17f),
        "chest" to floatArrayOf(0.27f, 0.175f, 0.73f, 0.38f),
        "stomach" to floatArrayOf(0.30f, 0.38f, 0.71f, 0.565f),
        "left arm" to floatArrayOf(0.05f, 0.175f, 0.30f, 0.58f),
        "right arm" to floatArrayOf(0.73f, 0.175f, 0.96f, 0.58f),
        "left leg" to floatArrayOf(0.30f, 0.565f, 0.52f, 0.98f),
        "right leg" to floatArrayOf(0.52f, 0.565f, 0.71f, 0.98f),
    )
    val ratio = 296f / 604f
    Column(Modifier.fillMaxWidth()) {
    EcgStrip(tick)
    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxHeight().aspectRatio(ratio).pointerInput(Unit) {
            detectTapGestures { p ->
                val fx = p.x / size.width
                val fy = p.y / size.height
                val zone = zones.firstOrNull { (_, r) -> fx in r[0]..r[2] && fy in r[1]..r[3] }?.first ?: ""
                if (zone.isNotEmpty()) {
                    picked = zone
                    Ui.messages.add(Ui.Msg(false, "[BAYMAX] Your $zone. Tell me what you feel there and for how long. I can only give general care tips, not a diagnosis. If it is severe or sudden, please call your local emergency number."))
                    Ui.input = "I feel something in my $zone: "
                }
            }
        }) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.body_xray),
                contentDescription = "Body", modifier = Modifier.fillMaxSize(),
            )
            Canvas(Modifier.fillMaxSize()) {
                val ms = tick / 1_000_000L
                zones.firstOrNull { it.first == picked }?.let { (_, r) ->
                    drawRoundRect(Color(0x3C78BEFF), Offset(r[0] * size.width, r[1] * size.height),
                        Size((r[2] - r[0]) * size.width, (r[3] - r[1]) * size.height), androidx.compose.ui.geometry.CornerRadius(14f))
                    drawRoundRect(Color(0xE696D7FF), Offset(r[0] * size.width, r[1] * size.height),
                        Size((r[2] - r[0]) * size.width, (r[3] - r[1]) * size.height), androidx.compose.ui.geometry.CornerRadius(14f), style = Stroke(2f))
                }
                // a small red heart in the chest, beating
                val ph = (ms % 1100L) / 1100f
                val beat = maxOf(Math.exp(-Math.pow(((ph - 0.08f) / 0.05f).toDouble(), 2.0)), 0.7 * Math.exp(-Math.pow(((ph - 0.26f) / 0.06f).toDouble(), 2.0))).toFloat()
                val hc = Offset(0.545f * size.width, 0.285f * size.height)
                val hs = size.width * 0.062f * (1f + 0.16f * beat)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFF3C46).copy(alpha = 0.47f + 0.39f * beat), Color.Transparent), center = hc, radius = hs * 2.6f), hs * 2.6f, hc)
                val heart = Path().apply {
                    moveTo(hc.x, hc.y + hs * 0.95f)
                    cubicTo(hc.x - hs * 1.6f, hc.y - hs * 0.1f, hc.x - hs * 0.9f, hc.y - hs * 1.2f, hc.x, hc.y - hs * 0.35f)
                    cubicTo(hc.x + hs * 0.9f, hc.y - hs * 1.2f, hc.x + hs * 1.6f, hc.y - hs * 0.1f, hc.x, hc.y + hs * 0.95f)
                    close()
                }
                drawPath(heart, Brush.radialGradient(listOf(Color(0xFFFF8C96), Color(0xFFE11E37)), center = Offset(hc.x - hs * 0.3f, hc.y - hs * 0.3f), radius = hs * 1.8f))
                drawPath(heart, Color(0xC8FFBEC3), style = Stroke(width = 1.5f))
                val y = ((ms % 3500L) / 3500f) * size.height
                drawRect(Brush.verticalGradient(listOf(Color(0x0078C8FF), Color(0x6E96D7FF), Color(0x0078C8FF)), startY = y - 28f, endY = y + 28f),
                    Offset(0f, y - 28f), Size(size.width, 56f))
            }
        }
        Text(if (picked.isEmpty()) "TOUCH WHERE YOU FEEL SOMETHING" else picked.uppercase(), fontSize = 8.sp, letterSpacing = 1.sp, color = accent,
            modifier = Modifier.align(Alignment.TopCenter))
    }
    }
}

/** A glowing red heartbeat line scrolling left, like a hospital monitor. An illustration, not a measurement. */
@Composable
fun EcgStrip(tick: Long) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(86.dp).background(Color(0xFF040204))) {
        Canvas(Modifier.fillMaxSize()) {
            val t = (tick / 1_000_000L) / 1000f
            val steps = (size.width / 3f).toInt().coerceAtLeast(40)
            val mid = size.height * 0.58f
            val amp = size.height * 0.36f
            fun beat(x: Float): Float {
                fun bump(c: Float, w: Float, a: Float) = a * Math.exp(-Math.pow(((x - c) / w).toDouble(), 2.0)).toFloat()
                return bump(0.16f, 0.035f, 0.18f) + bump(0.30f, 0.012f, -0.20f) + bump(0.335f, 0.014f, 1.0f) + bump(0.37f, 0.014f, -0.42f) + bump(0.58f, 0.05f, 0.30f)
            }
            val path = Path()
            for (i in 0..steps) {
                val fx = i / steps.toFloat()
                val beats = fx * 2.6f + t / 1.1f
                val y = mid - beat(beats % 1f) * amp
                if (i == 0) path.moveTo(0f, y) else path.lineTo(fx * size.width, y)
            }
            drawPath(path, Color(0x1CFF2832), style = Stroke(width = 18f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawPath(path, Color(0x3CFF2832), style = Stroke(width = 10f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawPath(path, Color(0xFFFF7878), style = Stroke(width = 4f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Text("HEARTBEAT (illustration, not a measurement)", fontSize = 7.sp, color = Color(0xAAFF7878), modifier = Modifier.padding(6.dp))
    }
}
