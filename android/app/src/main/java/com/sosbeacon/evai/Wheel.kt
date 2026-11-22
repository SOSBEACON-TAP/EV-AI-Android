package com.sosbeacon.evai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** One chip on the wheel. */
private class Chip(val key: String, val label: String, val color: Color)

private val CHIPS = listOf(
    Chip("ev", "EV", Color(0xFF5AA0E6)),
    Chip("jarvis", "JARVIS", Color(0xFFFFC400)),
    Chip("ultron", "ULTRON", Color(0xFFFF2A2A)),
    Chip("game", "GAME", Color(0xFF2BFF88)),
    Chip("karen", "KAREN", Color(0xFFB26BFF)),
    Chip("friday", "FRIDAY", Color(0xFFFF4FA3)),
    Chip("tars", "TARS", Color(0xFFE0A030)),
    Chip("gideon", "GIDEON", Color(0xFF4DA3FF)),
    Chip("edith", "EDITH", Color(0xFF40E0FF)),
    Chip("baymax", "BAYMAX", Color(0xFFFF7A7A)),
)

/** The arc reactor: steel housing, turning coil segments, glowing ring and core. [angle] turns it, [glow] colours it. */
fun DrawScope.drawReactor(center: Offset, r: Float, angle: Float, glow: Color, pulse: Float) {
    val k = r / 120f
    drawCircle(Color(0xFF050B14), 116f * k, center)
    drawCircle(Color(0xFF96A0AF), 116f * k, center, style = Stroke(width = 3f * k))
    for (n in 0 until 72) {
        val a = Math.toRadians((n * 5 - angle * 0.4f).toDouble())
        val r1 = 108f * k
        val r2 = (if (n % 6 == 0) 114f else 112f) * k
        drawLine(glow.copy(alpha = 0.65f),
            Offset(center.x + (r1 * cos(a)).toFloat(), center.y + (r1 * sin(a)).toFloat()),
            Offset(center.x + (r2 * cos(a)).toFloat(), center.y + (r2 * sin(a)).toFloat()), strokeWidth = 1.2f * k)
    }
    for (n in 0 until 10) {
        val a0 = n * 36f + angle * 0.25f
        drawArc(Color(0xFF364658), a0, 26f, false, Offset(center.x - 82f * k, center.y - 82f * k), Size(164f * k, 164f * k), style = Stroke(width = 32f * k))
        drawArc(glow.copy(alpha = 0.8f), a0, 26f, false, Offset(center.x - 98f * k, center.y - 98f * k), Size(196f * k, 196f * k), style = Stroke(width = 1.4f * k))
        drawArc(Color(0xD2D68034), a0 + 3f, 20f, false, Offset(center.x - 80f * k, center.y - 80f * k), Size(160f * k, 160f * k), style = Stroke(width = 1.2f * k))
    }
    drawCircle(glow.copy(alpha = 0.9f), 60f * k, center, style = Stroke(width = 2.4f * k))
    drawCircle(
        Brush.radialGradient(listOf(Color.White, glow.copy(alpha = 0.82f + 0.18f * pulse), glow.copy(alpha = 0f)), center = center, radius = 58f * k),
        58f * k, center,
    )
    val tri = Path()
    for (n in 0 until 3) {
        val a = Math.toRadians((-90 + n * 120 - angle * 0.6f).toDouble())
        val x = center.x + (32f * k * cos(a)).toFloat()
        val y = center.y + (32f * k * sin(a)).toFloat()
        if (n == 0) tri.moveTo(x, y) else tri.lineTo(x, y)
    }
    tri.close()
    drawPath(tri, Color.White.copy(alpha = 0.8f), style = Stroke(width = 1.4f * k))
}

/** Jarvis's own arc reactor: clean and precise. Dark glass disc, fine gold ring with ticks, twelve steel coil blocks, a bright core. */
fun DrawScope.drawJarvisReactor(center: Offset, r: Float, angle: Float, glow: Color, pulse: Float) {
    val k = r / 120f
    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.16f + 0.14f * pulse), Color.Transparent), center = center, radius = 140f * k), 140f * k, center)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF161C26), Color(0xFF04070C)), center = Offset(center.x, center.y - 20f * k), radius = 130f * k), 116f * k, center)
    drawCircle(glow.copy(alpha = 0.85f), 116f * k, center, style = Stroke(width = 2.2f * k))
    for (n in 0 until 72) {
        val a = Math.toRadians((n * 5 + angle * 0.15f).toDouble())
        val major = n % 3 == 0
        val r1 = (if (major) 101f else 105f) * k
        val r2 = 110f * k
        drawLine(glow.copy(alpha = if (major) 0.92f else 0.47f),
            Offset(center.x + (r1 * cos(a)).toFloat(), center.y + (r1 * sin(a)).toFloat()),
            Offset(center.x + (r2 * cos(a)).toFloat(), center.y + (r2 * sin(a)).toFloat()), strokeWidth = (if (major) 1.6f else 1f) * k)
    }
    for (n in 0 until 12) {
        val a0 = n * 30f - 12f
        drawArc(Color(0xFF3C4A5C), a0, 24f, false, Offset(center.x - 75f * k, center.y - 75f * k), Size(150f * k, 150f * k), style = Stroke(width = 34f * k))
        drawArc(Color(0xFF65748A), a0 + 1f, 22f, false, Offset(center.x - 86f * k, center.y - 86f * k), Size(172f * k, 172f * k), style = Stroke(width = 10f * k))
        drawArc(glow.copy(alpha = 0.6f), a0, 24f, false, Offset(center.x - 92f * k, center.y - 92f * k), Size(184f * k, 184f * k), style = Stroke(width = 1f * k))
        val m = Math.toRadians((a0 + 12f).toDouble())
        drawLine(glow.copy(alpha = 0.67f + 0.28f * pulse),
            Offset(center.x + (70f * k * cos(m)).toFloat(), center.y + (70f * k * sin(m)).toFloat()),
            Offset(center.x + (80f * k * cos(m)).toFloat(), center.y + (80f * k * sin(m)).toFloat()), strokeWidth = 1.8f * k)
    }
    drawCircle(glow.copy(alpha = 0.9f), 52f * k, center, style = Stroke(width = 2f * k))
    drawCircle(Color.White.copy(alpha = 0.47f), 46f * k, center, style = Stroke(width = 0.8f * k))
    drawCircle(
        Brush.radialGradient(listOf(Color.White, Color(0xFFFFF4C8), glow.copy(alpha = 0.8f + 0.2f * pulse), glow.copy(alpha = 0f)), center = center, radius = 46f * k),
        46f * k, center,
    )
    val tri = Path()
    for (n in 0 until 3) {
        val a = Math.toRadians((-90 + n * 120 - angle * 0.5f).toDouble())
        val x = center.x + (26f * k * cos(a)).toFloat()
        val y = center.y + (26f * k * sin(a)).toFloat()
        if (n == 0) tri.moveTo(x, y) else tri.lineTo(x, y)
    }
    tri.close()
    drawPath(tri, Color.White.copy(alpha = 0.85f), style = Stroke(width = 1.5f * k))
}

/** The mode wheel: a ring of chips around the arc reactor. Tap a chip to switch; tap outside to close. */
@Composable
fun ModeWheelOverlay() {
    if (!Ui.wheel) return
    var angle by remember { mutableFloatStateOf(0f) }
    var ringAngle by remember { mutableFloatStateOf(0f) }            // the blue dashes: they turn only while charging
    var pulse by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        var charging = true
        var checked = 0L
        while (true) withFrameNanos { t ->
            if (t - checked > 2_000_000_000L) {
                checked = t
                charging = try { (EvApp.ctx.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager).isCharging } catch (_: Exception) { true }
            }
            if (last != 0L) {
                val dt = ((t - last) / 1_000_000_000f).coerceAtMost(0.05f)
                angle = (angle + 20f * dt) % 360f
                if (charging) ringAngle = (ringAngle + 20f * dt) % 360f
                pulse = 0.5f + 0.5f * sin(t / 1_000_000_000f * 2.4f)
            }
            last = t
        }
    }
    val active = currentMode().ifEmpty { "ev" }
    Box(Modifier.fillMaxSize().background(Color(0xE6030610)).clickable { Ui.wheel = false }, contentAlignment = Alignment.Center) {
        val side = 330.dp
        Box(Modifier.size(side).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(side)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color(0xFF10182A), size.minDimension / 2f, c)
                drawCircle(Color(0xFFA8B0BC), size.minDimension / 2f - 4f, c, style = Stroke(width = 6f))
                for (n in 0 until 48) {
                    drawArc(Color(0xFF4678BE), n * 7.5f + ringAngle * 1.6f, 3.2f, false,
                        Offset(c.x - size.minDimension / 2f + 4f, c.y - size.minDimension / 2f + 4f),
                        Size(size.minDimension - 8f, size.minDimension - 8f), style = Stroke(width = 4f))
                }
                val glow = CHIPS.firstOrNull { it.key == active }?.color ?: Color(0xFF5AD7FF)
                drawReactor(c, size.minDimension * 0.24f, angle, glow, pulse)
            }
            // the arc reactor in the centre is the Overdrive button
            val od = Overdrive.running()
            Box(Modifier.size(side * 0.48f).clip(androidx.compose.foundation.shape.CircleShape)
                .clickable { Ui.wheel = false; Overdrive.begin() }, contentAlignment = Alignment.Center) {
                Text(if (od) "OVERDRIVE ON" else "OVERDRIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF06101C), modifier = Modifier.offset(y = 34.dp)
                        .background(Color(0xFFFF9A1E).copy(alpha = 0.9f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            val radius = 128f
            CHIPS.forEachIndexed { i, chip ->
                val a = Math.toRadians((-90.0 + i * 360.0 / CHIPS.size))
                val x = (radius * cos(a)).toFloat()
                val y = (radius * sin(a)).toFloat()
                val on = chip.key == active
                Box(
                    Modifier.offset(x.dp, y.dp).size(width = 62.dp, height = 28.dp)
                        .background(if (on) chip.color.copy(alpha = 0.85f) else Color(0xFF0B1A2A), RoundedCornerShape(8.dp))
                        .border(1.5.dp, chip.color, RoundedCornerShape(8.dp))
                        .clickable { Ui.wheel = false; pick(AppScope, chip.key) },
                    contentAlignment = Alignment.Center,
                ) { Text(chip.label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (on) Color(0xFF06101C) else chip.color, maxLines = 1) }
            }
        }
    }
}

/** A scope that lives as long as the app, so a mode switch is not cancelled when the wheel closes. */
val AppScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

private fun pick(scope: kotlinx.coroutines.CoroutineScope, key: String) {
    when (key) {
        "ev" -> { val cur = currentMode(); if (cur.isNotEmpty()) switchMode(scope, cur) }
        else -> switchMode(scope, key)
    }
}
