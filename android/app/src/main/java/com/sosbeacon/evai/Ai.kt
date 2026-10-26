package com.sosbeacon.evai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Personality {
    val ALL = linkedMapOf(
        "default" to ("Default" to ""),
        "professional" to ("Professional" to "Personality: professional. Be polished, precise and businesslike. No slang, no emoji, no jokes. Lead with the answer."),
        "friendly" to ("Friendly" to "Personality: friendly. Be warm, upbeat and encouraging, like a helpful friend. Stay concise."),
        "humorous" to ("Humorous" to "Personality: humorous. Be witty and playful with short, good-natured jokes, but keep the answer clear. Skip jokes for serious or urgent topics."),
    )
    /** pitch, speech rate for the phone's text-to-speech voice */
    val VOICE = mapOf(
        "default" to (1.0f to 1.0f),
        "professional" to (0.85f to 0.95f),
        "friendly" to (1.15f to 1.05f),
        "humorous" to (1.25f to 1.12f),
    )
    fun current(): String = if (Prefs.personality in ALL) Prefs.personality else "default"
}

object Ai {
    val BASES = linkedMapOf(
        "groq" to "https://api.groq.com/openai/v1",
        "gemini" to "https://generativelanguage.googleapis.com/v1beta/openai",
        "openai" to "https://api.openai.com/v1",
        "anthropic" to "https://api.anthropic.com/v1",
        "openrouter" to "https://openrouter.ai/api/v1",
        "deepseek" to "https://api.deepseek.com",
        "mistral" to "https://api.mistral.ai/v1",
        "xai" to "https://api.x.ai/v1",
        "custom" to "",
    )
    val MODELS = mapOf(
        "groq" to "openai/gpt-oss-120b",
        "gemini" to "gemini-3.8-flash",
        "openai" to "gpt-4o",
        "anthropic" to "claude-sonnet-4-6",
        "openrouter" to "openai/gpt-4o",
        "deepseek" to "deepseek-chat",
        "mistral" to "mistral-large-latest",
        "xai" to "grok-4",
        "custom" to "",
    )

    private val history = mutableListOf<JSONObject>()

    fun reset() { history.clear() }

    private fun systemPrompt(): String {
        if (Ui.jarvis) return "You are JARVIS, the phone's system-monitoring officer. You only report on and judge the health of this phone: " +
            "battery, memory, storage, network, uptime and screen time. Always call get_system_status (and get_usage_summary when relevant) for real numbers, " +
            "then give a short, calm, precise report and flag anything that needs attention (battery under 20%, memory or storage over 90%, offline). " +
            "You cannot call, message, open apps or do anything else; if asked, say that is EV's job and to switch back to EV. " +
            "Keep replies to two or three sentences. No markdown."
        val now = SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        val sb = StringBuilder()
        sb.append("You are EV AI, a fast, capable personal assistant living on the user's Android phone, in the spirit of Jarvis. ")
        sb.append("Current date and time: $now. ")
        if (Prefs.userName.isNotBlank()) sb.append("The user's name is ${Prefs.userName}. ")
        sb.append("Use your tools to actually do things (open apps, call, message, set reminders, find files and so on) instead of only describing how. ")
        sb.append("Keep spoken-style replies short: one or two sentences unless asked for detail. Do not use markdown. ")
        if (!Prefs.confirmSend) {
            sb.append("Sending: the user has turned OFF send confirmations. When asked to send a message, email or text, do it immediately with confirmed=true. ")
            sb.append("Only ask a question if the recipient or the message is missing or ambiguous. ")
        } else {
            sb.append("Before sending any message, email or text, tell the user what you will send and to whom, and wait for a yes; then call the tool with confirmed=true. ")
        }
        val p = Personality.ALL[Personality.current()]?.second ?: ""
        if (p.isNotBlank()) sb.append(p).append(' ')
        val mem = Store.memory()
        if (mem.isNotEmpty()) sb.append("Things the user asked you to remember: ").append(mem.joinToString("; ") { "${it.first}: ${it.second}" }).append(". ")
        return sb.toString()
    }

    /** Only role, content and tool_calls go back: some providers (Groq, Gemini) reject extra fields like "reasoning". */
    private fun cleanAssistant(msg: JSONObject): JSONObject {
        val out = JSONObject().put("role", "assistant")
        out.put("content", if (msg.isNull("content")) JSONObject.NULL else msg.optString("content", ""))
        val calls = msg.optJSONArray("tool_calls")
        if (calls != null) {
            val clean = JSONArray()
            for (i in 0 until calls.length()) {
                val c = calls.getJSONObject(i)
                val f = c.getJSONObject("function")
                clean.put(JSONObject().put("id", c.getString("id")).put("type", "function")
                    .put("function", JSONObject().put("name", f.getString("name")).put("arguments", f.optString("arguments", "{}"))))
            }
            out.put("tool_calls", clean)
        }
        return out
    }

    private fun endpoint(): String {
        val base = if (Prefs.provider == "custom") Prefs.baseUrl else (BASES[Prefs.provider] ?: "")
        return base.trimEnd('/') + "/chat/completions"
    }

    private fun model(): String = Prefs.model.ifBlank { MODELS[Prefs.provider] ?: "" }

    private fun post(body: JSONObject): JSONObject {
        val c = URL(endpoint()).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 20000
        c.readTimeout = 60000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Authorization", "Bearer ${Prefs.apiKey}")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw IOException("AI service error ($code): ${text.take(240)}")
        return JSONObject(text)
    }

    /** Sends [userText]; runs any tools the model asks for; returns the final reply. */
    suspend fun chat(userText: String, onTool: (String) -> Unit): String = withContext(Dispatchers.IO) {
        if (Prefs.apiKey.isBlank()) return@withContext "I need an AI key first. Open Settings and paste your key."
        val startSize = history.size
        history.add(JSONObject().put("role", "user").put("content", userText))
        while (history.size > 40) history.removeAt(0)
        // never start the window on a tool message
        while (history.isNotEmpty() && history[0].optString("role") == "tool") history.removeAt(0)

        val tools = Tools.schemas()
        try {
            for (round in 0 until 6) {
                val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt()))
                history.forEach { msgs.put(it) }
                val body = JSONObject().put("model", model()).put("messages", msgs)
                    .put("tools", tools).put("tool_choice", "auto")
                val resp = post(body)
                val msg = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                val calls = msg.optJSONArray("tool_calls")
                if (calls == null || calls.length() == 0) {
                    val reply = msg.optString("content", "").trim()
                    history.add(JSONObject().put("role", "assistant").put("content", reply))
                    return@withContext reply.ifBlank { "Done." }
                }
                history.add(cleanAssistant(msg))
                for (i in 0 until calls.length()) {
                    val call = calls.getJSONObject(i)
                    val fn = call.getJSONObject("function")
                    val name = fn.getString("name")
                    val args = try { JSONObject(fn.optString("arguments", "{}").ifBlank { "{}" }) } catch (e: Exception) { JSONObject() }
                    onTool(name)
                    val result = Tools.run(name, args)
                    history.add(JSONObject().put("role", "tool").put("tool_call_id", call.getString("id")).put("content", result))
                }
            }
            "I did what I could, but that took too many steps."
        } catch (e: IOException) {
            while (history.size > startSize) history.removeAt(history.size - 1)
            "I couldn't reach the AI service. ${e.message ?: ""}"
        } catch (e: Exception) {
            while (history.size > startSize) history.removeAt(history.size - 1)
            "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
