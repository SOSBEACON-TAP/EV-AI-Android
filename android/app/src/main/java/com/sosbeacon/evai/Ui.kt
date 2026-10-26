package com.sosbeacon.evai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Observable look-and-feel + chat state shared by the screens. */
enum class CoreState(val hex: Long, val speed: Float, val label: String) {
    IDLE(0xFF3AA0C8, 0.4f, "IDLE"),
    LISTENING(0xFF33D6C0, 1.6f, "LISTENING"),
    THINKING(0xFF8A6CF0, 2.2f, "THINKING"),
    EXECUTING(0xFFF0913A, 3.2f, "EXECUTING"),
    SPEAKING(0xFF3AC8F0, 2.0f, "SPEAKING"),
    ERROR(0xFFF0473A, 0.2f, "ERROR"),
}

object Ui {
    var coreState by mutableStateOf(CoreState.IDLE)
    var jarvis by mutableStateOf(Prefs.jarvis)
    var input by mutableStateOf("")
    var theme by mutableStateOf(Prefs.theme)
    var accent by mutableStateOf(Prefs.accent)
    var fontScale by mutableStateOf(Prefs.fontScale)
    var personality by mutableStateOf(Prefs.personality)

    class Msg(val fromUser: Boolean, val text: String)
    val messages = mutableStateListOf<Msg>()
    var busy by mutableStateOf(false)
    var status by mutableStateOf("")

    fun accentColor(): Color = try {
        Color(android.graphics.Color.parseColor(accent))
    } catch (e: Exception) { Color(0xFF00E5FF) }

    val ACCENTS = listOf("#00E5FF" to "Cyan", "#7C4DFF" to "Violet", "#FF5252" to "Red", "#69F0AE" to "Green", "#FFAB40" to "Amber", "#FF4081" to "Pink")
    val THEMES = listOf("dark" to "Dark", "amoled" to "AMOLED black", "light" to "Light")
}
