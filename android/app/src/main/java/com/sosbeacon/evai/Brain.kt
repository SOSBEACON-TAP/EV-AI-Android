package com.sosbeacon.evai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EV Brain: Trust Levels (EV says what it is about to do and waits for your OK), a Thought Stream, Action Replay and
 * Self-Evolving Shortcuts. All of it stays on the phone.
 */
object Brain {
    val thoughts = mutableStateListOf<String>()
    var level by mutableStateOf(Prefs.sp.getString("trust_level", "balanced") ?: "balanced")
        private set

    fun chooseLevel(v: String) { level = v; Prefs.sp.edit().putString("trust_level", v).apply() }

    /** 1 = changes something small, 2 = acts for you or is hard to undo. Tools that ask by themselves are not listed. */
    private val RISK = mapOf(
        "write_clipboard" to 1, "remember" to 1, "save_task_chain" to 1, "add_note" to 1, "add_todo" to 1, "set_reminder" to 1,
        "forget" to 2, "delete_note" to 2, "delete_task_chain" to 2, "cancel_reminder" to 2, "remove_todo" to 2, "set_personality" to 1,
    )
    private val SELF = setOf("send_sms", "send_whatsapp_message", "send_email", "call_contact")
    private val READ = listOf("get_", "list_", "read_", "search_", "recall", "morning_briefing", "open_search_result", "find_")
    private val NEVER = setOf("send_sms", "send_whatsapp_message", "send_email", "call_contact", "run_task_chain", "save_task_chain", "forget", "delete_note", "delete_task_chain")

    fun risky(name: String) = RISK.containsKey(name)
    private fun threshold() = when (level) { "careful" -> 1; "trusting" -> 99; else -> 2 }
    fun asksFirst(): List<String> = RISK.filter { it.value >= threshold() }.keys.sorted()
    private fun readOnly(n: String) = READ.any { n.startsWith(it) }

    fun intent(name: String, a: JSONObject): String {
        val v = a.keys().asSequence().filter { it != "confirmed" }.joinToString(", ") { k -> "$k=" + a.optString(k).take(40) }
        return "EV is about to ${name.replace('_', ' ')}" + (if (v.isNotEmpty()) " ($v)" else "") + "."
    }

    /** Returns the text to answer with when this call must wait for the user's OK, else null. */
    fun gate(name: String, a: JSONObject): String? {
        val r = RISK[name] ?: return null
        if (name in SELF || r < threshold() || a.optBoolean("confirmed")) return null
        think("❓ " + intent(name, a) + " Waiting for your OK.")
        return intent(name, a) + " I am asking because " + (if (r >= 2) "it is hard to undo" else "it changes something on your phone") +
            ". Tell the user exactly this and call the tool again with confirmed=true only if they say yes."
    }

    fun think(t: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        thoughts.add("$stamp  $t"); while (thoughts.size > 150) thoughts.removeAt(0)
    }

    // ---------------- replay + habits ----------------
    fun record(name: String, a: JSONObject, ok: Boolean) {
        if (readOnly(name)) return
        think(if (ok) "✔ $name done" else "✖ $name failed")
        if (!ok) return
        try {
            val args = JSONObject(); a.keys().forEach { k -> if (k != "confirmed" && !k.contains("pass", true) && !k.contains("key", true)) args.put(k, a.optString(k).take(300)) }
            val l = load("replay"); l.put(JSONObject().put("t", System.currentTimeMillis()).put("tool", name).put("args", args))
            while (l.length() > 40) l.remove(0)
            save("replay", l)
            habit(name, args)
        } catch (_: Exception) {}
    }

    private var prev: Pair<Long, JSONObject>? = null
    private fun habit(name: String, args: JSONObject) {
        if (name != "open_app" && name != "open_website") return
        val now = System.currentTimeMillis()
        val cur = JSONObject().put("tool", name).put("args", args)
        val o = loadObj("habits"); val counts = o.optJSONObject("counts") ?: JSONObject()
        fun bump(steps: JSONArray) {
            val sig = steps.toString(); val e = counts.optJSONObject(sig) ?: JSONObject().put("steps", steps).put("n", 0)
            counts.put(sig, e.put("n", e.getInt("n") + 1).put("last", now))
        }
        bump(JSONArray().put(cur))
        prev?.let { (t, p) -> if (now - t <= 90_000 && p.toString() != cur.toString()) bump(JSONArray().put(p).put(cur)) }
        prev = now to cur
        val it = counts.keys().asSequence().toList()
        it.forEach { k -> if (now - counts.getJSONObject(k).optLong("last") > 30L * 86_400_000) counts.remove(k) }
        o.put("counts", counts); Prefs.sp.edit().putString("habits", o.toString()).apply()
    }

    class Suggestion(val sig: String, val name: String, val lines: List<String>, val n: Int)

    fun suggestions(): List<Suggestion> {
        val o = loadObj("habits"); val counts = o.optJSONObject("counts") ?: return emptyList()
        val dismissed = o.optJSONArray("dismissed") ?: JSONArray()
        val skip = (0 until dismissed.length()).map { dismissed.getString(it) }.toSet()
        val out = mutableListOf<Suggestion>()
        counts.keys().forEach { sig ->
            val e = counts.getJSONObject(sig); val steps = e.getJSONArray("steps"); val n = e.getInt("n")
            if (sig in skip || n < (if (steps.length() == 1) 4 else 3)) return@forEach
            val lines = (0 until steps.length()).map { i ->
                val s = steps.getJSONObject(i); val a = s.getJSONObject("args")
                if (s.getString("tool") == "open_app") "app " + a.optString("name") else "site " + a.optString("url")
            }
            out.add(Suggestion(sig, lines.joinToString(" + ") { it.substringAfter(' ') }.take(40), lines, n))
        }
        return out.sortedByDescending { it.n }.take(8)
    }

    fun dismiss(sig: String) {
        val o = loadObj("habits"); val d = o.optJSONArray("dismissed") ?: JSONArray()
        d.put(sig); o.put("dismissed", d); Prefs.sp.edit().putString("habits", o.toString()).apply()
    }

    fun accept(s: Suggestion, name: String) { Store.saveChain(name, s.lines); dismiss(s.sig) }

    class Run(val steps: List<JSONObject>) {
        fun label(): String {
            val t = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(steps.first().getLong("t")))
            return "$t  ·  " + steps.take(4).joinToString("  →  ") { it.getString("tool").replace('_', ' ') } + if (steps.size > 4) "  (+${steps.size - 4})" else ""
        }
    }

    fun runs(): List<Run> {
        val l = load("replay"); val out = mutableListOf<MutableList<JSONObject>>()
        for (i in 0 until l.length()) {
            val s = l.getJSONObject(i)
            if (out.isNotEmpty() && s.getLong("t") - out.last().last().getLong("t") <= 90_000) out.last().add(s) else out.add(mutableListOf(s))
        }
        return out.reversed().take(6).map { Run(it) }
    }

    fun replay(run: Run, done: (String) -> Unit) {
        Thread {
            val lines = run.steps.map { s ->
                val n = s.getString("tool")
                if (n in NEVER) "skipped $n (needs your OK each time)" else {
                    think("• Replaying $n")
                    val r = Tools.run(n, s.getJSONObject("args"))
                    Thread.sleep(500)
                    "$n: " + if (r.startsWith("That failed") || r.contains("Tell the user exactly this")) "needs attention" else "done"
                }
            }
            done(lines.joinToString("\n"))
        }.start()
    }

    private fun load(k: String): JSONArray = try { JSONArray(Prefs.sp.getString(k, "[]")) } catch (_: Exception) { JSONArray() }
    private fun loadObj(k: String): JSONObject = try { JSONObject(Prefs.sp.getString(k, "{}")) } catch (_: Exception) { JSONObject() }
    private fun save(k: String, a: JSONArray) { Prefs.sp.edit().putString(k, a.toString()).apply() }
}

@Composable
fun BrainScreen() {
    var tick by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf("") }
    var naming by remember { mutableStateOf<Brain.Suggestion?>(null) }
    var nameText by remember { mutableStateOf("") }
    val runs = remember(tick) { Brain.runs() }
    val sugs = remember(tick) { Brain.suggestions() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("Trust: how much may EV do without asking?") {
            listOf("careful" to "Careful: asks before anything that changes something",
                "balanced" to "Balanced: asks before risky actions (forgetting, deleting)",
                "trusting" to "Trusting: only sending, calling and deleting ask").forEach { (k, t) ->
                if (Brain.level == k) Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text(t, fontSize = 12.sp) }
                else OutlinedButton(onClick = { Brain.chooseLevel(k); tick++ }, modifier = Modifier.fillMaxWidth()) { Text(t, fontSize = 12.sp) }
            }
            Text("Asks first right now: " + (Brain.asksFirst().joinToString(", ") { it.replace('_', ' ') }.ifEmpty { "nothing extra" }), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Section("Thought stream") {
            if (Brain.thoughts.isEmpty()) Text("Nothing yet. Ask EV to do something and it shows here.", fontSize = 12.sp)
            Brain.thoughts.takeLast(30).reversed().forEach { Text(it, fontSize = 11.sp) }
            if (Brain.thoughts.isNotEmpty()) OutlinedButton(onClick = { Brain.thoughts.clear() }, modifier = Modifier.padding(top = 6.dp)) { Text("Clear") }
        }
        Section("Replay") {
            if (runs.isEmpty()) Text("Nothing to replay yet.", fontSize = 12.sp)
            runs.forEach { r -> OutlinedButton(onClick = { report = "Replaying…"; Brain.replay(r) { report = it; tick++ } }, modifier = Modifier.fillMaxWidth()) { Text(r.label(), fontSize = 11.sp) } }
            if (report.isNotEmpty()) Text(report, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            Text("Sends and deletes are skipped.", fontSize = 10.sp)
        }
        Section("Shortcuts EV noticed") {
            if (sugs.isEmpty()) Text("Nothing yet. Keep using EV and ideas appear here.", fontSize = 12.sp)
            sugs.forEach { s ->
                Text("${s.name}  (done ${s.n} times)", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { nameText = s.name; naming = s }) { Text("Save") }
                    OutlinedButton(onClick = { Brain.dismiss(s.sig); tick++ }) { Text("Not interested") }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
    naming?.let { s ->
        AlertDialog(onDismissRequest = { naming = null },
            title = { Text("Name the shortcut") },
            text = { OutlinedTextField(nameText, { nameText = it }, singleLine = true, label = { Text("Say or type this to run it") }) },
            confirmButton = { TextButton(onClick = { if (nameText.isNotBlank()) { Brain.accept(s, nameText.trim()); Ui.messages.add(Ui.Msg(false, "Saved. Say \"run ${nameText.trim()}\".")) }; naming = null; tick++ }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("Cancel") } })
    }
}
