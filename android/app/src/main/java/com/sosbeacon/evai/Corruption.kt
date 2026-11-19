package com.sosbeacon.evai

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Switching to Ultron: a glitch in mixed reds slowly corrupts the screen (thin traces first, then torn slices and blocks,
 * then everything drains to a grayish red), a sentinel scanner with a core that looks around appears, the picture
 * glitches, and the Ultron screen takes over as the glitch clears.
 * Purely cosmetic: it draws over the screen and ignores nothing important - switchMode() decides when the mode changes.
 */
object Corruption {
    const val SPREAD_MS = 6500L
    const val REVEAL_MS = 1500L

    /** SystemClock.uptimeMillis() when it started, or 0 when it is not running. */
    var start by mutableLongStateOf(0L)

    fun begin() { start = SystemClock.uptimeMillis() }
    fun end() { start = 0L }
}

// a mix of reds: crimson, blood, maroon, ember, ash and a near-black void
private val PALETTE = listOf(
    Color(0xFFCC1230), Color(0xFF960A1C), Color(0xFF4C0612), Color(0xFFEC4C2C), Color(0xFF80484C), Color(0xFF0E0206),
)

private fun smooth(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

@Composable
fun UltronCorruptionOverlay() {
    val start = Corruption.start
    if (start == 0L) return
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(start) {
        while (true) {
            withFrameNanos { now = SystemClock.uptimeMillis() }
        }
    }
    val t = (now - start).coerceAtLeast(0L)
    Canvas(
        Modifier.fillMaxSize().pointerInput(start) {       // swallow touches while the screen is being corrupted
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    e.changes.forEach { it.consume() }
                }
            }
        }
    ) {
        drawCorruption(t, now)
    }
}

private fun DrawScope.cells(size: Float, coverage: Float, frame: Int, alpha: Float, seed: Int) {
    if (coverage <= 0.01f || alpha <= 0f) return
    val cols = (this.size.width / size).toInt() + 1
    val rows = (this.size.height / size).toInt() + 1
    val rnd = Random(frame * 7919 + seed)
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            if (rnd.nextFloat() < coverage) {
                val col = PALETTE[rnd.nextInt(PALETTE.size)]
                drawRect(col.copy(alpha = alpha), Offset(c * size, r * size), Size(size, size))
            } else rnd.nextInt()
        }
    }
}

private fun DrawScope.drawCorruption(t: Long, now: Long) {
    val w = size.width
    val h = size.height
    val frame = (now / 70L).toInt()
    val rnd = Random(frame * 131)
    val prog = (t.toFloat() / Corruption.SPREAD_MS).coerceIn(0f, 1f)
    val reveal = if (t > Corruption.SPREAD_MS) ((t - Corruption.SPREAD_MS).toFloat() / Corruption.REVEAL_MS).coerceIn(0f, 1f) else 0f
    val veil = 1f - smooth(reveal * 1.15f)
    if (veil <= 0f) return

    // red tint grows as it spreads; near the end the base is almost solid so the new screen can be built unseen behind it
    drawRect(Color(0xFF8A2A34).copy(alpha = 0.5f * smooth((prog - 0.4f) / 0.4f) * veil))
    if (prog > 0.8f) drawRect(Color(0xFF2A0006).copy(alpha = 0.93f * smooth((prog - 0.8f) / 0.12f) * veil))

    // 1) thin traces (the wires)
    val c1 = smooth(prog / 0.28f)
    val lines = (46 * c1).toInt()
    for (i in 0 until lines) {
        val horizontal = rnd.nextBoolean()
        val col = PALETTE[rnd.nextInt(2)].copy(alpha = 0.85f * veil)
        if (horizontal) {
            val y = rnd.nextFloat() * h
            val x0 = rnd.nextFloat() * w * 0.6f
            drawLine(col, Offset(x0, y), Offset(x0 + rnd.nextFloat() * w * 0.7f, y), strokeWidth = 1.5.dp.toPx())
        } else {
            val x = rnd.nextFloat() * w
            val y0 = rnd.nextFloat() * h * 0.6f
            drawLine(col, Offset(x, y0), Offset(x, y0 + rnd.nextFloat() * h * 0.7f), strokeWidth = 1.5.dp.toPx())
        }
    }
    // 2) blocks (the controls and tabs), 3) the whole screen
    cells(26.dp.toPx(), 0.28f * smooth((prog - 0.24f) / 0.3f), frame, 0.8f * veil, 1)
    cells(16.dp.toPx(), 0.3f * smooth((prog - 0.52f) / 0.3f), frame, 0.7f * veil, 2)
    // torn slices: bands of the screen shifted sideways in mixed reds
    val slices = (14 * smooth((prog - 0.3f) / 0.5f)).toInt()
    repeat(slices) {
        val y = rnd.nextFloat() * h
        val bh = 3f + rnd.nextFloat() * (h * 0.04f)
        val shift = (rnd.nextFloat() - 0.5f) * w * 0.25f
        drawRect(PALETTE[rnd.nextInt(PALETTE.size)].copy(alpha = 0.55f * veil), Offset(shift, y), Size(w, bh))
    }

    // the sentinel scanner: appears in the glitch, its core follows a slow wandering look
    val eyeT = smooth((prog - 0.74f) / 0.14f) * (1f - smooth((reveal - 0.1f) / 0.5f))
    if (eyeT > 0.01f) drawSentinel(eyeT, now)

    // the glitch: the picture tears just before Ultron takes over, and for a moment after
    val glitch = if (reveal == 0f) smooth((prog - 0.9f) / 0.1f) else (1f - reveal * 3f).coerceAtLeast(0f)
    if (glitch > 0f) {
        repeat((4 + 22 * glitch).toInt()) {
            val y = rnd.nextFloat() * h
            val bar = 2f + rnd.nextFloat() * (6f + 40f * glitch)
            val col = if (rnd.nextFloat() < 0.6f) Color(0xFFFF2832).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.92f)
            drawRect(col, Offset((rnd.nextFloat() - 0.5f) * 80f * glitch, y), Size(w, bar))
        }
        if (rnd.nextFloat() < 0.3f * glitch) drawRect(Color(0xFFFF1E28).copy(alpha = 0.45f * rnd.nextFloat()))
    }
}

private fun DrawScope.drawSentinel(t: Float, now: Long) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = minOf(size.width, size.height) * 0.3f
    val sec = now / 1000f
    drawCircle(Color(0xFF0E0206).copy(alpha = 0.9f * t), r, Offset(cx, cy))
    drawCircle(Color(0xFFCC1230).copy(alpha = 0.9f * t), r, Offset(cx, cy), style = Stroke(width = 3.dp.toPx()))
    // three counter-rotating arc rings
    val rings = listOf(0.86f to Color(0xFFCC1230), 0.66f to Color(0xFFEC4C2C), 0.46f to Color(0xFF960A1C))
    rings.forEachIndexed { i, (f, c) ->
        val rr = r * f
        val start = sec * (50f + i * 25f) * (if (i % 2 == 0) 1f else -1f)
        drawArc(
            c.copy(alpha = 0.9f * t), start, 240f, false,
            Offset(cx - rr, cy - rr), Size(rr * 2f, rr * 2f), style = Stroke(width = 2.5.dp.toPx()),
        )
    }
    // the core with a vertical slit that wanders
    val ic = Offset(cx + sin(sec * 0.9f) * r * 0.18f, cy + cos(sec * 0.7f) * r * 0.12f)
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFD2C8).copy(alpha = t), Color(0xFFCC1230).copy(alpha = t), Color(0xFF4C0612).copy(alpha = 0f)),
            center = ic, radius = r * 0.3f,
        ),
        r * 0.3f, ic,
    )
    drawRect(Color(0xFF080002).copy(alpha = 0.9f * t), Offset(ic.x - r * 0.025f, ic.y - r * 0.2f), Size(r * 0.05f, r * 0.4f))
}
