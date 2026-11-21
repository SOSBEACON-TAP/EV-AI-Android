package com.sosbeacon.evai

import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The same face pictures as the computer version, drawn alive in the middle of the core. */
object Faces {
    private val RES = mapOf(
        "baymax" to R.drawable.face_baymax, "edith" to R.drawable.face_edith, "friday" to R.drawable.face_friday,
        "game" to R.drawable.face_game, "gideon" to R.drawable.face_gideon, "jarvis" to R.drawable.face_jarvis,
        "karen" to R.drawable.face_karen, "tars" to R.drawable.face_tars, "ultron" to R.drawable.face_ultron,
    )
    private val cache = HashMap<String, ImageBitmap?>()

    /** Which face to show right now ("" = the plain EV core). */
    fun key(): String = when {
        Ui.jarvis -> "jarvis"
        Ui.mode in RES -> Ui.mode
        else -> ""
    }

    fun bitmap(key: String): ImageBitmap? = cache.getOrPut(key) {
        RES[key]?.let { runCatching { BitmapFactory.decodeResource(EvApp.ctx.resources, it)?.asImageBitmap() }.getOrNull() }
    }

    // mouth boxes (x0, y0, x1, y1, gain), as fractions of the picture
    private val MOUTHS = mapOf(
        "tars" to floatArrayOf(0.28f, 0.72f, 0.70f, 0.80f, 3.0f),
        "gideon" to floatArrayOf(0.42f, 0.50f, 0.58f, 0.57f, 1.4f),
        "friday" to floatArrayOf(0.36f, 0.62f, 0.64f, 0.74f, 1.0f),
        "ultron" to floatArrayOf(0.30f, 0.66f, 0.70f, 0.80f, 0.5f),
    )
    private val BAYMAX_LINE = floatArrayOf(0.345f, 0.504f, 0.685f, 0.587f)
    private class Eyes(val ellipse: Boolean, val cover: Color, val ink: Color, val boxes: List<FloatArray>)
    private val EYES = mapOf(
        "baymax" to Eyes(true, Color(244, 245, 247), Color(10, 10, 12), listOf(floatArrayOf(0.225f, 0.37f, 0.36f, 0.60f), floatArrayOf(0.655f, 0.45f, 0.80f, 0.68f))),
        "tars" to Eyes(false, Color(4, 10, 20), Color(70, 190, 255), listOf(floatArrayOf(0.14f, 0.36f, 0.34f, 0.52f), floatArrayOf(0.59f, 0.36f, 0.80f, 0.52f))),
    )
    private val FLICKER = setOf("gideon", "jarvis", "game", "friday", "karen")

    private var mouthV = 0f
    private var lastT = 0f
    private var nextBlink = 3f

    private fun clock(now: Float, speaking: Boolean): Pair<Float, Float> {
        val dt = min(0.1f, max(0.001f, now - lastT)); lastT = now
        val k = (now * 8).toInt()
        val h = (k * 2654435761L) and 0xFFFFFFFFL
        var target = 0.25f + 0.75f * (((h shr 8) % 100).toFloat() / 100f)
        if ((k / 9) % 3 == 2 && (k % 9) > 5) target = 0f
        if (!speaking) target = 0f
        mouthV += (target - mouthV) * (1f - exp(-dt * (if (target > mouthV) 34f else 16f)))
        var blink = 0f
        if (now >= nextBlink) {
            val span = now - nextBlink
            if (span < 0.18f) blink = 1f - abs(span / 0.09f - 1f) else nextBlink = now + 2.5f + ((now * 1000).toInt() * 40503 and 0xFFFF) / 65535f * 3.5f
        }
        return mouthV to max(0f, blink)
    }

    /** Draw the face: it floats, sways and breathes, glows in the mode colour, blinks and talks. */
    fun DrawScope.drawFace(key: String, bmp: ImageBitmap, cx0: Float, cy0: Float, radius: Float, accent: Color, angle: Float, phase: Float,
                           state: CoreState, nowSec: Float) {
        val speaking = state == CoreState.SPEAKING
        val (mouth, blink) = clock(nowSec, speaking)
        val ph = phase * 2f * PI.toFloat()
        val box = radius * 1.7f
        var scale = min(box / bmp.width, box / bmp.height)
        if (bmp.width > bmp.height * 1.8f) scale = radius * 2f / bmp.width
        val breathe = 1f + 0.02f * sin(ph) + if (speaking) 0.025f * mouth else 0f
        val tw = bmp.width * scale * breathe
        val th = bmp.height * scale * breathe
        val cy = cy0 + sin(ph) * radius * 0.03f
        val cx = cx0 + sin(nowSec * 0.7f) * radius * 0.012f
        val big = max(tw, th)
        val beat = 0.5f + 0.5f * sin(ph * (if (speaking) 3f else 1f))
        val col = if (state == CoreState.ERROR) Color(0xFFFF5252) else accent
        val room = min(size.width, size.height) / 2f - 2f
        for ((rad, a0) in listOf(0.85f to 34, 0.6f to 70)) {
            val gr = min(big * rad, room)
            val a = (a0 * (0.6f + 0.7f * beat)) / 255f
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = a), col.copy(alpha = a * 0.35f), col.copy(alpha = 0f)), center = Offset(cx, cy), radius = gr), gr, Offset(cx, cy))
        }
        if (state == CoreState.LISTENING) {
            for (k in 0 until 3) {
                val t = (phase * 2f + k / 3f) % 1f
                val r = big * (0.45f + 0.5f * t)
                drawCircle(accent.copy(alpha = 0.6f * (1f - t) * (1f - t)), min(r, room), Offset(cx, cy), style = Stroke(2f))
            }
        } else if (state == CoreState.THINKING || state == CoreState.EXECUTING) {
            for (k in 0 until 6) for (tr in 0 until 5) {
                val a = Math.toRadians((angle * 3 + k * 60 - tr * 7).toDouble())
                drawCircle(accent.copy(alpha = (if (k == 0) 0.9f else 0.6f) * (1f - tr / 5f)), 4f - tr * 0.55f,
                    Offset(cx + (kotlin.math.cos(a) * big * 0.62f).toFloat(), cy + (kotlin.math.sin(a) * big * 0.62f).toFloat()))
            }
        }
        val opacity = if (key in FLICKER) 0.9f + 0.1f * sin(nowSec * 37f) * sin(nowSec * 11f) else 1f
        val tilt = sin(nowSec * 0.9f) * 1.1f + if (speaking) sin(nowSec * 5.5f) * 1.6f * mouth else 0f
        val left = cx - tw / 2f
        val top = cy - th / 2f
        withTransform({ rotate(tilt, Offset(cx, cy)) }) {
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(left, top, left + tw, top + th), Paint())
                drawImage(bmp, IntOffset.Zero, IntSize(bmp.width, bmp.height), IntOffset(left.toInt(), top.toInt()), IntSize(tw.toInt(), th.toInt()), alpha = opacity)
                // the mouth opens and closes with the speech
                MOUTHS[key]?.let { m ->
                    if (mouth > 0.02f) {
                        val sx0 = (m[0] * bmp.width).toInt(); val sy0 = (m[1] * bmp.height).toInt()
                        val sw = ((m[2] - m[0]) * bmp.width).toInt(); val sh = ((m[3] - m[1]) * bmp.height).toInt()
                        val dw = (m[2] - m[0]) * tw; val dh = (m[3] - m[1]) * th
                        val grow = 1f + m[4] * mouth
                        val mx = left + (m[0] + m[2]) / 2f * tw; val my = top + (m[1] + m[3]) / 2f * th
                        drawImage(bmp, IntOffset(sx0, sy0), IntSize(sw, sh), IntOffset((mx - dw / 2f).toInt(), (my - dh * grow / 2f).toInt()), IntSize(dw.toInt(), (dh * grow).toInt()))
                    }
                }
                if (key == "baymax") {      // the line between his eyes thickens as he talks
                    drawLine(Color(10, 10, 12), Offset(left + BAYMAX_LINE[0] * tw, top + BAYMAX_LINE[1] * th), Offset(left + BAYMAX_LINE[2] * tw, top + BAYMAX_LINE[3] * th),
                        strokeWidth = max(1f, tw * (0.006f + 0.03f * mouth)), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
                EYES[key]?.let { e ->
                    if (blink > 0.02f) for (b in e.boxes) {
                        val r = Rect(left + b[0] * tw, top + b[1] * th, left + b[2] * tw, top + b[3] * th)
                        val shut = r.height * max(0.08f, 1f - blink)
                        val lid = Rect(r.left, r.center.y - shut / 2f, r.right, r.center.y + shut / 2f)
                        if (e.ellipse) { drawOval(e.cover, r.topLeft, r.size); drawOval(e.ink, lid.topLeft, lid.size) }
                        else { drawRect(e.cover, r.topLeft, r.size); drawRect(e.ink, lid.topLeft, lid.size) }
                    }
                }
                // a faint scan line, then soften the edges so the picture melts into the screen
                val sy = top + (((angle / 360f * 1.6f) % 1f) * (th + 40f)) - 20f
                drawRect(Color.White.copy(alpha = 0.15f), Offset(left, sy), Size(tw, 2f))
                drawRect(
                    Brush.radialGradient(0f to Color.Black, 0.78f to Color.Black, 1f to Color.Transparent, center = Offset(cx, cy), radius = min(tw, th) / 2f * 1.15f),
                    Offset(left, top), Size(tw, th), blendMode = BlendMode.DstIn,
                )
                canvas.restore()
            }
        }
    }
}
