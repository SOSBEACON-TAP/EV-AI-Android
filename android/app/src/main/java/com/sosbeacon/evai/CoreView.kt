package com.sosbeacon.evai

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The animated AI core: the same web, beams, tilted red and blue rings and pulsing centre as the
 * computer version. Turns all gold when Jarvis is switched on. Speed and centre colour follow the state.
 */
@Composable
fun AiCore(modifier: Modifier = Modifier) {
    var angle by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableFloatStateOf(0f) }
    val speed by rememberUpdatedState(Ui.coreState.speed * Ui.boost)

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) {
                    val dt = ((t - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    angle = (angle + speed * 33f * dt) % 360f
                    phase = (phase + 0.66f * dt) % 1f
                }
                last = t
            }
        }
    }

    val jarvis = Ui.jarvis
    val state = Ui.coreState
    Canvas(modifier) {
        val mode = modeAccent()
        val red = mode ?: if (jarvis) Color(0xFFFFC400) else Color(0xFFF0465A)
        val blue = mode?.copy(alpha = 0.75f) ?: if (jarvis) Color(0xFFFFE066) else Color(0xFF3C8CF0)
        val accent = if (mode != null && state != CoreState.ERROR) mode else if (jarvis && state != CoreState.ERROR) Color(0xFFFFC400) else Color(state.hex)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (minOf(size.width, size.height) - 12.dp.toPx()) / 2f
        val cx = center.x
        val cy = center.y

        val pulse = 0.5f + 0.5f * sin(phase * 2f * PI.toFloat())
        val fk = Faces.key()
        val bmp = if (fk.isEmpty()) null else Faces.bitmap(fk)
        if (bmp != null) {
            with(Faces) { drawFace(fk, bmp, cx, cy, radius, accent, angle, phase, state, System.nanoTime() / 1_000_000_000f) }
        } else {
            drawReactor(center, radius, angle, accent, pulse)          // the same arc reactor as the computer version
        }
    }
}
