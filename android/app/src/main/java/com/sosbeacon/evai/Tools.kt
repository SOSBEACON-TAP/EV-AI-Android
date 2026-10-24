package com.sosbeacon.evai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Tool(
    val name: String,
    val desc: String,
    val params: List<List<String>>,   // each: [name, type, description, "req"?]
    val fn: (JSONObject) -> String,
)

object Tools {
    private val ctx: Context get() = EvApp.ctx
    private val all = mutableListOf<Tool>()
    private var torchOn = false

    private fun def(name: String, desc: String, vararg params: String, fn: (JSONObject) -> String) {
        // param format: "*name:type:description" (leading * = required)
        all.add(Tool(name, desc, params.map { p ->
            val req = p.startsWith("*"); val parts = p.removePrefix("*").split(":", limit = 3)
            listOf(parts[0], parts[1], parts[2]) + if (req) listOf("req") else emptyList()
        }, fn))
    }

    fun schemas(): JSONArray {
        val arr = JSONArray()
        for (t in all) {
            val props = JSONObject(); val req = JSONArray()
            for (p in t.params) {
                props.put(p[0], JSONObject().put("type", p[1]).put("description", p[2]))
                if (p.size > 3) req.put(p[0])
            }
            val parameters = JSONObject().put("type", "object").put("properties", props).put("required", req)
            arr.put(JSONObject().put("type", "function").put("function",
                JSONObject().put("name", t.name).put("description", t.desc).put("parameters", parameters)))
        }
        return arr
    }

    fun run(name: String, args: JSONObject): String {
        val t = all.firstOrNull { it.name == name } ?: return "Unknown tool $name"
        return try { t.fn(args) } catch (e: SecurityException) {
            "Permission missing: ${e.message}. Ask the user to allow it in Settings."
        } catch (e: Exception) { "That failed: ${e.message ?: e.javaClass.simpleName}" }
    }

    // ---------------- helpers ----------------
    private fun go(i: Intent) { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    private fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun findApp(query: String): Pair<String, String>? {
        val pm = ctx.packageManager
        val q = query.lowercase().trim()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0).map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        return apps.firstOrNull { it.first.lowercase() == q }
            ?: apps.firstOrNull { it.first.lowercase().startsWith(q) }
            ?: apps.firstOrNull { it.first.lowercase().contains(q) || it.second.lowercase().contains(q) }
    }

    fun openApp(name: String): String {
        val a = findApp(name) ?: return "I couldn't find an app called $name."
        val li = ctx.packageManager.getLaunchIntentForPackage(a.second) ?: return "I can't open ${a.first}."
        go(li); return "Opened ${a.first}."
    }

    fun openSite(url: String): String {
        var u = url.trim()
        if (!u.startsWith("http")) u = "https://$u"
        go(Intent(Intent.ACTION_VIEW, Uri.parse(u))); return "Opened $u."
    }

    /** Name or number -> number. */
    private fun resolveNumber(who: String): Pair<String, String>? {
        val digits = who.filter { it.isDigit() || it == '+' }
        if (digits.length >= 6 && who.count { it.isLetter() } == 0) return who to digits
        if (!has(android.Manifest.permission.READ_CONTACTS)) return null
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$who%"), null
        )?.use { c ->
            var best: Pair<String, String>? = null
            while (c.moveToNext()) {
                val n = c.getString(0); val num = c.getString(1)
                if (n.equals(who, true)) return n to num
                if (best == null) best = n to num
            }
            return best
        }
        return null
    }

    private fun needConfirm(a: JSONObject, what: String): String? =
        if (Prefs.confirmSend && !a.optBoolean("confirmed")) "Ask the user to confirm first: $what" else null

    // ---------------- tool list ----------------
    init {
        def("get_datetime", "Current date and time.") {
            SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        }
        def("get_phone_info", "Battery level, charging state and free storage of the phone.") {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val lvl = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val st = StatFs(Environment.getDataDirectory().path)
            val freeGb = st.availableBytes / 1_000_000_000.0
            "Battery $lvl%${if (bm.isCharging) " (charging)" else ""}. Free storage ${"%.1f".format(freeGb)} GB. Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MODEL}."
        }
        def("open_app", "Open an installed app by name, e.g. Camera, Instagram, Settings.", "*name:string:App name") { openApp(it.getString("name")) }
        def("open_website", "Open a website in the browser.", "*url:string:Web address") { openSite(it.getString("url")) }
        def("search_web", "Search the web in the browser.", "*query:string:Search words") {
            openSite("https://www.google.com/search?q=${enc(it.getString("query"))}")
        }
        def("open_youtube", "Open YouTube.") { openApp("YouTube") }
        def("search_youtube", "Search YouTube for something.", "*query:string:Search words") {
            openSite("https://www.youtube.com/results?search_query=${enc(it.getString("query"))}")
        }
        def("open_maps", "Show a place or directions in Maps.", "*place:string:Place or address") {
            go(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${enc(it.getString("place"))}"))); "Opened Maps for ${it.getString("place")}."
        }
        def("call_contact", "Call a contact by name or a phone number.", "*to:string:Contact name or number") { a ->
            val r = resolveNumber(a.getString("to")) ?: return@def "I couldn't find ${a.getString("to")} in your contacts."
            if (has(android.Manifest.permission.CALL_PHONE)) go(Intent(Intent.ACTION_CALL, Uri.parse("tel:${r.second}")))
            else go(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${r.second}")))
            "Calling ${r.first}."
        }
        def("find_contact", "Look up a contact's phone number.", "*name:string:Contact name") {
            val r = resolveNumber(it.getString("name")); if (r == null) "No contact found." else "${r.first}: ${r.second}"
        }
        def("send_sms", "Send a text message (SMS) to a contact or number.",
            "*to:string:Contact name or number", "*message:string:Message text", "confirmed:boolean:true once the user approved sending") { a ->
            needConfirm(a, "SMS to ${a.getString("to")}: ${a.getString("message")}")?.let { return@def it }
            val r = resolveNumber(a.getString("to")) ?: return@def "I couldn't find ${a.getString("to")} in your contacts."
            if (!has(android.Manifest.permission.SEND_SMS)) return@def "I need the SMS permission to send texts."
            val sms = ctx.getSystemService(SmsManager::class.java)
            sms.sendMultipartTextMessage(r.second, null, sms.divideMessage(a.getString("message")), null, null)
            "Text sent to ${r.first}."
        }
        def("send_whatsapp_message", "Send a WhatsApp message to a contact or number.",
            "*to:string:Contact name or number", "*message:string:Message text", "confirmed:boolean:true once the user approved sending") { a ->
            needConfirm(a, "WhatsApp to ${a.getString("to")}: ${a.getString("message")}")?.let { return@def it }
            val r = resolveNumber(a.getString("to")) ?: return@def "I couldn't find ${a.getString("to")} in your contacts."
            var num = r.second.filter { it.isDigit() }
            if (num.length == 10) num = "91$num"
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num?text=${enc(a.getString("message"))}")).setPackage("com.whatsapp")
            try { EvAccess.armAutoSend("com.whatsapp"); go(i) } catch (e: Exception) { return@def "WhatsApp isn't installed." }
            if (EvAccess.enabled(ctx)) "Sending the WhatsApp message to ${r.first}."
            else "WhatsApp is open with the message ready for ${r.first}. Turn on EV AI in Accessibility settings and I'll press Send for you."
        }
        def("send_email", "Send an email with the phone's Gmail app.",
            "*to:string:Email address", "*subject:string:Subject", "*body:string:Message body", "confirmed:boolean:true once the user approved sending") { a ->
            needConfirm(a, "Email to ${a.getString("to")}: ${a.getString("subject")}")?.let { return@def it }
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(a.getString("to")))
                .putExtra(Intent.EXTRA_SUBJECT, a.getString("subject")).putExtra(Intent.EXTRA_TEXT, a.getString("body"))
            if (ctx.packageManager.getLaunchIntentForPackage("com.google.android.gm") != null) i.setPackage("com.google.android.gm")
            EvAccess.armAutoSend("com.google.android.gm"); go(i)
            if (EvAccess.enabled(ctx)) "Sending the email to ${a.getString("to")}."
            else "The email is ready in Gmail. Turn on EV AI in Accessibility settings and I'll press Send for you."
        }
        def("set_timer", "Set a countdown timer.", "*minutes:number:Minutes from now", "label:string:What it is for") { a ->
            val m = a.getDouble("minutes"); val label = a.optString("label", "Timer")
            Reminders.schedule(ctx, System.currentTimeMillis() + (m * 60000).toLong(), "$label is done"); "Timer set for ${m.toInt()} minutes."
        }
        def("set_reminder", "Set a reminder. Use minutes (from now) OR at (local time 'yyyy-MM-dd HH:mm').",
            "*text:string:What to remind", "minutes:number:Minutes from now", "at:string:Local date and time yyyy-MM-dd HH:mm") { a ->
            val at = if (a.has("minutes")) System.currentTimeMillis() + (a.getDouble("minutes") * 60000).toLong()
            else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(a.optString("at"))?.time ?: return@def "I need a time for the reminder."
            if (at < System.currentTimeMillis()) return@def "That time has already passed."
            Reminders.schedule(ctx, at, a.getString("text"))
            "Reminder set for " + SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(at)) + "."
        }
        def("list_reminders", "List upcoming reminders and timers.") {
            val l = Store.reminders().sortedBy { r -> r.at }
            if (l.isEmpty()) "No reminders." else l.mapIndexed { i, r -> "${i + 1}. ${r.text} at ${SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(r.at))}" }.joinToString("\n")
        }
        def("cancel_reminder", "Cancel a reminder by number from list_reminders or words from its text.", "*which:string:Number or words") { a ->
            val l = Store.reminders().sortedBy { r -> r.at }; val k = a.getString("which")
            val r = k.toIntOrNull()?.let { n -> l.getOrNull(n - 1) } ?: l.firstOrNull { x -> x.text.contains(k, true) }
            if (r == null) "No matching reminder." else { Reminders.cancel(ctx, r.id); "Cancelled: ${r.text}" }
        }
        def("add_todo", "Add an item to the to-do list.", "*text:string:The task") { Store.addTodo(it.getString("text")); "Added." }
        def("list_todos", "Show the to-do list.") {
            val l = Store.todos(); if (l.isEmpty()) "Your to-do list is empty." else l.mapIndexed { i, t -> "${i + 1}. ${if (t.second) "[done] " else ""}${t.first}" }.joinToString("\n")
        }
        def("complete_todo", "Mark a to-do done by number or words.", "*which:string:Number or words") {
            Store.completeTodo(it.getString("which"))?.let { t -> "Done: $t" } ?: "No matching to-do."
        }
        def("remove_todo", "Delete a to-do by number or words.", "*which:string:Number or words") {
            Store.removeTodo(it.getString("which"))?.let { t -> "Removed: $t" } ?: "No matching to-do."
        }
        def("add_note", "Save a quick note.", "*text:string:Note text") { Store.addNote(it.getString("text")); "Saved." }
        def("list_notes", "Show saved notes.") {
            val l = Store.notes(); if (l.isEmpty()) "No notes." else l.mapIndexed { i, t -> "${i + 1}. $t" }.take(15).joinToString("\n")
        }
        def("delete_note", "Delete a note by number or words.", "*which:string:Number or words") {
            Store.deleteNote(it.getString("which"))?.let { t -> "Deleted: $t" } ?: "No matching note."
        }
        def("remember", "Remember a fact about the user for later.", "*key:string:Short label, e.g. 'mom birthday'", "*value:string:The fact") {
            Store.remember(it.getString("key"), it.getString("value")); "I'll remember that."
        }
        def("recall", "Look up remembered facts.", "query:string:Words to look for (empty = everything)") { a ->
            val q = a.optString("query", "").lowercase()
            val l = Store.memory().filter { q.isEmpty() || it.first.lowercase().contains(q) || it.second.lowercase().contains(q) }
            if (l.isEmpty()) "Nothing remembered about that." else l.joinToString("\n") { "${it.first}: ${it.second}" }
        }
        def("forget", "Forget a remembered fact.", "*key:string:Label or words") { if (Store.forget(it.getString("key"))) "Forgotten." else "I had nothing like that." }
        def("media_control", "Control music/video playback: play_pause, next, previous.", "*action:string:play_pause, next or previous") { a ->
            val code = when (a.getString("action")) {
                "next" -> KeyEvent.KEYCODE_MEDIA_NEXT; "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS; else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            }
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)); "Done."
        }
        def("set_volume", "Change media volume: up, down, mute, or a percent 0-100.", "*value:string:up, down, mute, or a number") { a ->
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC); val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val v = a.getString("value").lowercase()
            val target = when {
                v == "up" -> minOf(max, cur + maxOf(1, max / 7)); v == "down" -> maxOf(0, cur - maxOf(1, max / 7)); v == "mute" -> 0
                else -> ((v.filter { it.isDigit() }.toIntOrNull() ?: 50).coerceIn(0, 100) * max / 100)
            }
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI); "Volume ${target * 100 / max}%."
        }
        def("flashlight", "Turn the flashlight on or off.", "*on:boolean:true to turn on") { a ->
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { cid ->
                cm.getCameraCharacteristics(cid).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return@def "No flashlight found."
            torchOn = a.getBoolean("on"); cm.setTorchMode(id, torchOn); if (torchOn) "Flashlight on." else "Flashlight off."
        }
        def("read_clipboard", "Read the text the user last copied (works while EV AI is on screen).") {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
            if (t.isNullOrBlank()) "The clipboard is empty (or Android blocked reading it while EV AI is in the background)." else t.take(4000)
        }
        def("write_clipboard", "Copy text to the clipboard.", "*text:string:Text to copy") {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("EV", it.getString("text"))); "Copied."
        }
        def("get_usage_summary", "How long the user spent in apps. period: today or week.", "period:string:today or week") { a ->
            if (!Usage.hasAccess(ctx)) return@def "Usage access is off. Ask the user to allow it in the Insights tab."
            val week = a.optString("period", "today") == "week"
            val l = Usage.perApp(ctx, Usage.startOfDay(if (week) 6 else 0), System.currentTimeMillis()).take(8)
            if (l.isEmpty()) "No app usage recorded yet." else
                "Total ${Usage.fmt(l.sumOf { it.second })}. " + l.joinToString(", ") { "${Usage.label(ctx, it.first)} ${Usage.fmt(it.second)}" }
        }
        def("search_files", "Find files on the phone by describing them, e.g. 'photos from camera', 'pdf resume'.", "*query:string:What to look for") {
            val r = Finder.search(ctx, it.getString("query"))
            if (r.isEmpty()) "No files found." else r.mapIndexed { i, f -> "${i + 1}. ${Finder.describe(f)}" }.joinToString("\n")
        }
        def("open_search_result", "Open a file from the last file search by number.", "*number:number:Result number") {
            val f = Finder.last.getOrNull(it.getInt("number") - 1)
            if (f == null) "There's no result with that number." else if (Finder.open(ctx, f)) "Opened ${f.name}." else "I couldn't open that file."
        }
        def("save_task_chain", "Save a Quick Action: a named list of steps. Steps look like 'app Spotify', 'site github.com', 'say Good morning'.",
            "*name:string:Name of the quick action", "*steps:string:Steps separated by newlines") {
            val steps = it.getString("steps").split("\n", ";").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
            Store.saveChain(it.getString("name"), steps); "Saved quick action ${it.getString("name")} with ${steps.size} steps."
        }
        def("run_task_chain", "Run a saved Quick Action by name.", "*name:string:Name") { runChain(it.getString("name")) }
        def("list_task_chains", "List saved Quick Actions.") {
            val m = Store.chains(); if (m.isEmpty()) "No quick actions yet." else m.entries.joinToString("\n") { e -> "${e.key}: ${e.value.joinToString(" | ")}" }
        }
        def("delete_task_chain", "Delete a Quick Action.", "*name:string:Name") { if (Store.deleteChain(it.getString("name"))) "Deleted." else "No such quick action." }
        def("start_focus_mode", "Turn on Focus mode: runs the focus quick action and blocks distracting apps.") { startFocus() }
        def("stop_focus_mode", "Turn Focus mode off.") { Focus.stop(ctx); "Focus mode is off." }
        def("set_personality", "Switch EV's personality: default, professional, friendly or humorous.", "*name:string:Personality name") {
            val n = it.getString("name").lowercase()
            if (n in Personality.ALL) { Prefs.personality = n; Ui.personality = n; "Personality is now $n." } else "Unknown personality."
        }
        def("go_home", "Return to the phone's home screen.") { if (EvAccess.goHome()) "Home." else "Turn on EV AI in Accessibility settings so I can do that." }
        def("morning_briefing", "Gather date, battery, to-dos and reminders for a morning briefing.") {
            val sb = StringBuilder(run("get_datetime", JSONObject())).append("\n").append(run("get_phone_info", JSONObject()))
            sb.append("\nTo-dos:\n").append(run("list_todos", JSONObject())).append("\nReminders:\n").append(run("list_reminders", JSONObject())); sb.toString()
        }
    }

    // ---------------- quick actions / focus ----------------
    fun runChain(name: String): String {
        val m = Store.chains(); val key = m.keys.firstOrNull { it.equals(name, true) } ?: return "No quick action called $name."
        val out = mutableListOf<String>()
        for (s in m[key]!!) {
            val verb = s.substringBefore(' ').lowercase(); val rest = s.substringAfter(' ', "").trim()
            out.add(when (verb) {
                "app", "open" -> openApp(rest)
                "site", "web", "url" -> openSite(rest)
                "say", "speak" -> { Voice.speak(rest); "Said it." }
                "volume" -> run("set_volume", JSONObject().put("value", rest))
                "home" -> if (EvAccess.goHome()) "Home." else "Needs Accessibility."
                else -> "Skipped: $s"
            })
            Thread.sleep(700)
        }
        return "Ran $key: " + out.joinToString(" ")
    }

    fun startFocus(): String {
        if (!Usage.hasAccess(ctx)) { Usage.openSettings(ctx); return "I need Usage access to watch for blocked apps. I opened the setting: allow EV AI, then say focus mode again." }
        Focus.start(ctx)
        val chain = Prefs.focusChain.trim()
        val note = if (chain.isNotEmpty() && Store.chains().keys.any { it.equals(chain, true) }) " " + runChain(chain) else ""
        val ax = if (EvAccess.enabled(ctx)) "" else " Turn on EV AI in Accessibility settings so blocked apps can be closed."
        return "Focus mode is on. Blocking: ${Focus.terms().joinToString(", ")}.$note$ax"
    }
}
