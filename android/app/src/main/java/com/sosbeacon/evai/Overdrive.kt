package com.sosbeacon.evai

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/**
 * Overdrive works like a tungsten bulb driven too hard: a warm glow and a faster core while it runs, a flicker in the
 * last ten seconds, a white burn-out flash, then a dark cool-down. Purely visual: it only draws and speeds up the core.
 */
object Overdrive {
    const val RUN_MS = 90_000L
    const val BURN_MS = 1_200L
    const val DARK_MS = 15_000L

    var start by mutableLongStateOf(0L)

    fun running(): Boolean = start != 0L

    fun begin() {
        if (running()) return
        start = SystemClock.uptimeMillis()
        Ui.messages.add(Ui.Msg(false, "Overdrive engaged. 90 seconds, then the filament burns out."))
        if (Prefs.speak) Voice.speak("Overdrive engaged.")
    }

    fun end() {
        start = 0L
        Ui.boost = 1f
        Ui.messages.add(Ui.Msg(false, "Overdrive burned out and the system has cooled down."))
    }
}

@Composable
fun OverdriveOverlay() {
    val s = Overdrive.start
    if (s == 0L) return
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(s) {
        while (true) {
            withFrameNanos { now = SystemClock.uptimeMillis() }
            val t = now - s
            Ui.boost = when {
                t < Overdrive.RUN_MS -> 1.6f + 1.2f * (t.toFloat() / Overdrive.RUN_MS)
                else -> 1f
            }
            if (t >= Overdrive.RUN_MS + Overdrive.BURN_MS + Overdrive.DARK_MS) { Overdrive.end(); break }
        }
    }
    val t = (now - s).coerceAtLeast(0L)
    val rnd = Random((now / 60L).toInt())
    Box(Modifier.fillMaxSize()) {
        when {
            t < Overdrive.RUN_MS -> {
                val ramp = (t.toFloat() / Overdrive.RUN_MS).coerceIn(0f, 1f)
                val left = Overdrive.RUN_MS - t
                val flicker = if (left < 10_000L && rnd.nextFloat() < 0.35f) 0.25f else 1f
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.radialGradient(
                        listOf(Color(0xFFFFC060).copy(alpha = (0.10f + 0.28f * ramp) * flicker), Color(0xFFFF7A10).copy(alpha = (0.05f + 0.22f * ramp) * flicker), Color.Transparent),
                        center = Offset(size.width / 2f, size.height / 2f), radius = size.maxDimension * 0.75f))
                }
            }
            t < Overdrive.RUN_MS + Overdrive.BURN_MS -> {
                val f = 1f - (t - Overdrive.RUN_MS).toFloat() / Overdrive.BURN_MS
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = f.coerceIn(0f, 1f))))
            }
            else -> {
                val remain = ((Overdrive.RUN_MS + Overdrive.BURN_MS + Overdrive.DARK_MS - t) / 1000L).coerceAtLeast(0L)
                Box(
                    Modifier.fillMaxSize().background(Color(0xF2020204))
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } },
                    contentAlignment = Alignment.Center,
                ) { Text("FILAMENT COOLING  $remain s", fontSize = 12.sp, letterSpacing = 3.sp, color = Color(0xFF6A3A18), modifier = Modifier.padding(24.dp)) }
            }
        }
    }
}
