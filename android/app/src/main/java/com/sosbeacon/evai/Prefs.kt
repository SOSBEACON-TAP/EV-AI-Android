package com.sosbeacon.evai

import android.content.Context
import android.content.SharedPreferences
import kotlin.reflect.KProperty

class StrPref(private val k: String, private val d: String) {
    operator fun getValue(t: Any?, p: KProperty<*>): String = Prefs.sp.getString(k, d) ?: d
    operator fun setValue(t: Any?, p: KProperty<*>, v: String) { Prefs.sp.edit().putString(k, v).apply() }
}

class BoolPref(private val k: String, private val d: Boolean) {
    operator fun getValue(t: Any?, p: KProperty<*>): Boolean = Prefs.sp.getBoolean(k, d)
    operator fun setValue(t: Any?, p: KProperty<*>, v: Boolean) { Prefs.sp.edit().putBoolean(k, v).apply() }
}

class IntPref(private val k: String, private val d: Int) {
    operator fun getValue(t: Any?, p: KProperty<*>): Int = Prefs.sp.getInt(k, d)
    operator fun setValue(t: Any?, p: KProperty<*>, v: Int) { Prefs.sp.edit().putInt(k, v).apply() }
}

class LongPref(private val k: String, private val d: Long) {
    operator fun getValue(t: Any?, p: KProperty<*>): Long = Prefs.sp.getLong(k, d)
    operator fun setValue(t: Any?, p: KProperty<*>, v: Long) { Prefs.sp.edit().putLong(k, v).apply() }
}

object Prefs {
    lateinit var sp: SharedPreferences
    fun init(c: Context) { sp = c.getSharedPreferences("evai", Context.MODE_PRIVATE) }

    var provider by StrPref("provider", "groq")
    var apiKey by StrPref("api_key", "")
    var model by StrPref("model", "")
    var baseUrl by StrPref("base_url", "")
    var personality by StrPref("personality", "default")
    var orientation by StrPref("orientation", "landscape")
    var theme by StrPref("theme", "dark")
    var accent by StrPref("accent", "#00E5FF")
    var fontScale by IntPref("font_scale", 100)
    var speak by BoolPref("speak", true)
    var confirmSend by BoolPref("confirm_send", false)
    var focusBlocked by StrPref("focus_blocked", "youtube, scratch, discord")
    var focusChain by StrPref("focus_chain", "focus mode")
    var focusActive by BoolPref("focus_active", false)
    var userName by StrPref("user_name", "")
    var introDone by BoolPref("intro_done", false)
    var deviceCode by StrPref("device_code", "")
    var server by StrPref("alert_server", "https://ntfy.sh")
    var computeKey by StrPref("compute_key", "")
    var listen by BoolPref("listen", true)
    var lastSeen by LongPref("last_seen", 0L)
    var jarvis by BoolPref("jarvis", false)
    var autoUpdate by BoolPref("auto_update", true)
    var lastUpdateCheck by LongPref("last_update_check", 0L)
    var updateNote by StrPref("update_note", "")
}
