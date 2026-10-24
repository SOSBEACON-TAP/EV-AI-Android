package com.sosbeacon.evai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Observable look-and-feel + chat state shared by the screens. */
object Ui {
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
