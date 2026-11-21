package com.sosbeacon.evai

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Mission Spaces: named sets of apps and websites that open together. Nothing is closed. */
object Spaces {
    private const val KEY = "mission_spaces"
    private val DEFAULTS = """[
      {"name":"School","apps":[],"urls":["https://classroom.google.com","https://docs.google.com"]},
      {"name":"Coding","apps":[],"urls":["https://github.com"]},
      {"name":"Gaming","apps":[],"urls":[]},
      {"name":"Research","apps":[],"urls":["https://scholar.google.com","https://www.wikipedia.org"]}]"""

    fun all(): JSONArray = try { JSONArray(Prefs.sp.getString(KEY, null) ?: DEFAULTS) } catch (e: Exception) { JSONArray(DEFAULTS) }

    fun save(name: String, apps: List<String>, urls: List<String>) {
        val list = all(); val out = JSONArray()
        for (i in 0 until list.length()) if (!list.getJSONObject(i).getString("name").equals(name, true)) out.put(list.getJSONObject(i))
        out.put(JSONObject().put("name", name.take(40)).put("apps", JSONArray(apps.take(20))).put("urls", JSONArray(urls.take(20))))
        Prefs.sp.edit().putString(KEY, out.toString()).apply()
    }

    fun delete(name: String) {
        val list = all(); val out = JSONArray()
        for (i in 0 until list.length()) if (list.getJSONObject(i).getString("name") != name) out.put(list.getJSONObject(i))
        Prefs.sp.edit().putString(KEY, out.toString()).apply()
    }

    fun start(name: String): String {
        val list = all()
        for (i in 0 until list.length()) {
            val s = list.getJSONObject(i)
            if (s.getString("name") != name) continue
            val opened = mutableListOf<String>(); val failed = mutableListOf<String>()
            val apps = s.getJSONArray("apps"); val urls = s.getJSONArray("urls")
            for (k in 0 until apps.length()) { val r = Tools.openApp(apps.getString(k)); (if (r.startsWith("I can't")) failed else opened).add(apps.getString(k)) }
            for (k in 0 until urls.length()) { Tools.openSite(urls.getString(k)); opened.add(urls.getString(k)) }
            return "$name space started: " + (opened.joinToString(", ").ifEmpty { "nothing to open" }) + if (failed.isNotEmpty()) ". Could not open: " + failed.joinToString(", ") else ""
        }
        return "No space called $name."
    }
}

/** Privacy shield: mutes the phone's microphone and stops EV's spoken output. It cannot switch the camera off. */
object Shield {
    var on by mutableStateOf(false)
    fun toggle() {
        val am = EvApp.ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        on = !on
        try { @Suppress("DEPRECATION") am.isMicrophoneMute = on } catch (_: Exception) {}
        if (on) Voice.stop()
        Ui.messages.add(Ui.Msg(false, if (on) "Privacy shield on: the microphone is muted and I have stopped talking. I cannot switch the camera off, so cover it if you need to. Tap the shield again to unmute."
                                       else "Privacy shield off: the microphone is unmuted."))
    }
}

/** The forms: WhatsApp, Email and Mission Spaces. Pressing Send counts as your confirmation. */
@Composable
fun ComposeOverlay() {
    val kind = Ui.compose
    if (kind.isEmpty()) return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var to by remember(kind) { mutableStateOf("") }
    var subject by remember(kind) { mutableStateOf("") }
    var body by remember(kind) { mutableStateOf("") }
    var name by remember(kind) { mutableStateOf("") }
    var apps by remember(kind) { mutableStateOf("") }
    var urls by remember(kind) { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    val close = { Ui.compose = "" }
    val tint = Color(0xFF5AA0E6)

    if (kind == "spaces") {
        AlertDialog(
            onDismissRequest = { close() },
            title = { Text("MISSION SPACES", fontSize = 14.sp) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("Tap a space to open its apps and sites. Nothing is closed.", fontSize = 12.sp)
                    val list = remember(refresh) { Spaces.all() }
                    for (i in 0 until list.length()) {
                        val n = list.getJSONObject(i).getString("name")
                        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
                            TextButton(onClick = { close(); Ui.messages.add(Ui.Msg(false, Spaces.start(n))) }) { Text(n.uppercase(), color = tint) }
                            TextButton(onClick = { Spaces.delete(n); refresh++ }) { Text("delete", fontSize = 11.sp, color = Color(0xFF9FB4CC)) }
                        }
                    }
                    Text("New space", fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(apps, { apps = it }, label = { Text("Apps, comma separated") })
                    OutlinedTextField(urls, { urls = it }, label = { Text("Websites, comma separated") })
                    TextButton(onClick = {
                        if (name.isNotBlank()) {
                            Spaces.save(name.trim(), apps.split(",").map { it.trim() }.filter { it.isNotEmpty() }, urls.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                            name = ""; apps = ""; urls = ""; refresh++
                        }
                    }) { Text("SAVE SPACE", color = tint) }
                }
            },
            confirmButton = { TextButton(onClick = { close() }) { Text("Close") } },
        )
        return
    }

    val wa = kind == "whatsapp"
    AlertDialog(
        onDismissRequest = { close() },
        title = { Text(if (wa) "SEND A WHATSAPP MESSAGE" else "SEND AN EMAIL", fontSize = 14.sp) },
        text = {
            Column {
                OutlinedTextField(to, { to = it }, label = { Text(if (wa) "To (a contact name or a phone number)" else "To (email address)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (!wa) OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (to.isBlank() || body.isBlank()) return@TextButton
                close()
                Ui.messages.add(Ui.Msg(false, "Sending…"))
                scope.launch(Dispatchers.IO) {
                    val args = JSONObject().put("to", to.trim()).put("confirmed", true)
                    val result = if (wa) Tools.run("send_whatsapp_message", args.put("message", body.trim()))
                    else Tools.run("send_email", args.put("subject", subject.ifBlank { "(no subject)" }).put("body", body.trim()))
                    Ui.messages.add(Ui.Msg(false, result))
                }
            }) { Text("SEND", color = tint) }
        },
        dismissButton = { TextButton(onClick = { close() }) { Text("Cancel") } },
    )
}
