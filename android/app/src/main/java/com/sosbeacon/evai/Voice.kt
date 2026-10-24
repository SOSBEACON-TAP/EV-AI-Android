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

    fun stop() { tts?.stop() }
}
