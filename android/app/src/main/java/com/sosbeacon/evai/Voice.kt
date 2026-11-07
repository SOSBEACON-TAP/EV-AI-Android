package com.sosbeacon.evai

import android.speech.tts.TextToSpeech
import java.util.Locale

/** Spoken replies. Each personality gets its own pitch and speed. */
object Voice {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(EvApp.ctx) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.getDefault().let { l -> if (tts?.isLanguageAvailable(l) ?: -2 >= 0) l else Locale.US }
                pending?.let { speak(it); pending = null }
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) { init(); pending = text; return }
        val (pitch, rate) = Personality.VOICE[Personality.current()] ?: (1f to 1f)
        tts?.setPitch(pitch); tts?.setSpeechRate(rate)
        tts?.speak(text.take(1200), TextToSpeech.QUEUE_FLUSH, null, "ev")
    }

    /** A mode character (Ultron, Game, Karen) speaks with its own pitch and speed, so it sounds different from EV. */
    private val CHARACTER = mapOf("ultron" to (0.55f to 0.8f), "game" to (1.3f to 1.3f), "karen" to (1.4f to 0.95f), "jarvis" to (0.8f to 0.92f))

    fun speakAs(who: String, text: String) {
        if (text.isBlank()) return
        if (!ready) { init(); pending = text; return }
        val (pitch, rate) = CHARACTER[who] ?: (1f to 1f)
        tts?.setPitch(pitch); tts?.setSpeechRate(rate)
        tts?.speak(text.take(1200), TextToSpeech.QUEUE_FLUSH, null, "ev")
    }

    /** Speaks in the voice of whichever mode is on (EV's own voice when none is). */
    fun speakNow(text: String) {
        val who = if (Ui.mode.isNotEmpty()) Ui.mode else if (Ui.jarvis) "jarvis" else ""
        if (who.isEmpty()) speak(text) else speakAs(who, text)
    }

    fun stop() { tts?.stop() }
}
