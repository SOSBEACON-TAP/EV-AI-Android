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
    val speed by rememberUpdatedState(Ui.coreState.speed)

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
        val red = if (jarvis) Color(0xFFFFC400) else Color(0xFFF0465A)
        val blue = if (jarvis) Color(0xFFFFE066) else Color(0xFF3C8CF0)
        val accent = if (jarvis && state != CoreState.ERROR) Color(0xFFFFC400) else Color(state.hex)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (minOf(size.width, size.height) - 12.dp.toPx()) / 2f
        val cx = center.x
        val cy = center.y

        // faint web
        val web = accent.copy(alpha = 45f / 255f)
        for (f in floatArrayOf(0.35f, 0.6f, 0.85f, 1.05f)) {
            drawCircle(web, radius * f, center, style = Stroke(1f))
        }
        val spoke = -angle * 0.3f
        for (i in 0 until 8) {
            val a = (spoke + i * 45f) * PI.toFloat() / 180f
            drawLine(web, center, Offset(cx + radius * 1.05f * cos(a), cy + radius * 1.05f * sin(a)), strokeWidth = 1f)
        }

        // two diagonal beams
        fun beam(deg: Float, color: Color, width: Float) {
            val a = deg * PI.toFloat() / 180f
            val len = radius * 1.35f
            val dx = cos(a) * len
            val dy = sin(a) * len
            drawLine(color.copy(alpha = 190f / 255f), Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy),
                strokeWidth = width, cap = StrokeCap.Round)
        }
        beam(angle * 1.1f + 20f, red, 1.5.dp.toPx())
        beam(-angle * 0.9f - 20f, blue, 1.5.dp.toPx())

        // tilted orbit rings
        fun ring(deg: Float, tilt: Float, color: Color, squash: Float, frac: Float, widthDp: Float, alpha: Int) {
            withTransform({
                rotate(deg, center)
                scale(1f, squash, center)
                rotate(tilt, center)
            }) {
                drawCircle(color.copy(alpha = alpha / 255f), radius * frac, center,
                    style = Stroke(widthDp.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        ring(angle, 15f, red, 0.45f, 0.92f, 3.5f, 210)
        ring(-angle * 1.15f, -15f, blue, 0.45f, 0.92f, 3.5f, 210)
        ring(-angle * 0.7f + 60f, 55f, red, 0.65f, 0.72f, 2.2f, 150)
        ring(angle * 0.85f - 60f, -55f, blue, 0.65f, 0.72f, 2.2f, 150)
        ring(angle * 1.6f, 35f, red, 0.3f, 0.42f, 1.5f, 170)
        ring(-angle * 1.8f, -35f, blue, 0.3f, 0.42f, 1.5f, 170)

        // pulsing centre
        val pulse = 0.5f + 0.5f * sin(phase * 2f * PI.toFloat())
        drawCircle(accent.copy(alpha = 60f / 255f), radius * 0.22f + pulse * 6.dp.toPx(), center)
        drawCircle(lerp(accent, Color.White, 0.3f).copy(alpha = 230f / 255f), radius * 0.08f + pulse * 2.dp.toPx(), center)
    }
}
