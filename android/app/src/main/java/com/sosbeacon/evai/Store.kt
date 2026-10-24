package com.sosbeacon.evai

import org.json.JSONArray
import org.json.JSONObject

/** Small JSON-backed lists (to-dos, notes, memory, quick actions, reminders). */
object Store {
    private fun arr(name: String): JSONArray = try {
        JSONArray(Prefs.sp.getString("store_$name", "[]"))
    } catch (e: Exception) { JSONArray() }

    private fun save(name: String, a: JSONArray) { Prefs.sp.edit().putString("store_$name", a.toString()).apply() }

    private fun objs(name: String): MutableList<JSONObject> {
        val a = arr(name)
        return MutableList(a.length()) { a.getJSONObject(it) }
    }

    private fun saveObjs(name: String, l: List<JSONObject>) {
        val a = JSONArray(); l.forEach { a.put(it) }; save(name, a)
    }

    // ---- to-dos ----
    fun todos(): List<Pair<String, Boolean>> = objs("todos").map { it.getString("t") to it.optBoolean("d") }
    fun addTodo(t: String) { val l = objs("todos"); l.add(JSONObject().put("t", t).put("d", false)); saveObjs("todos", l) }
    private fun findIdx(texts: List<String>, key: String): Int {
        key.trim().toIntOrNull()?.let { if (it in 1..texts.size) return it - 1 }
        val k = key.lowercase()
        return texts.indexOfFirst { it.lowercase().contains(k) }
    }
    fun completeTodo(key: String): String? {
        val l = objs("todos"); val i = findIdx(l.map { it.getString("t") }, key); if (i < 0) return null
        l[i].put("d", true); saveObjs("todos", l); return l[i].getString("t")
    }
    fun removeTodo(key: String): String? {
        val l = objs("todos"); val i = findIdx(l.map { it.getString("t") }, key); if (i < 0) return null
        val t = l.removeAt(i).getString("t"); saveObjs("todos", l); return t
    }

    // ---- notes ----
    fun notes(): List<String> = objs("notes").map { it.getString("t") }
    fun addNote(t: String) { val l = objs("notes"); l.add(0, JSONObject().put("t", t)); saveObjs("notes", l) }
    fun deleteNote(key: String): String? {
        val l = objs("notes"); val i = findIdx(l.map { it.getString("t") }, key); if (i < 0) return null
        val t = l.removeAt(i).getString("t"); saveObjs("notes", l); return t
    }

    // ---- memory (facts EV remembers) ----
    fun memory(): List<Pair<String, String>> = objs("memory").map { it.getString("k") to it.getString("v") }
    fun remember(k: String, v: String) {
        val l = objs("memory"); l.removeAll { it.getString("k").equals(k, true) }
        l.add(JSONObject().put("k", k).put("v", v)); saveObjs("memory", l)
    }
    fun forget(k: String): Boolean {
        val l = objs("memory"); val had = l.removeAll { it.getString("k").contains(k, true) }
        saveObjs("memory", l); return had
    }

    // ---- quick actions (named multi-step shortcuts) ----
    fun chains(): Map<String, List<String>> {
        val m = linkedMapOf<String, List<String>>()
        objs("chains").forEach { o ->
            val s = o.getJSONArray("s"); m[o.getString("n")] = List(s.length()) { s.getString(it) }
        }
        return m
    }
    fun saveChain(name: String, steps: List<String>) {
        val l = objs("chains"); l.removeAll { it.getString("n").equals(name, true) }
        l.add(JSONObject().put("n", name.trim()).put("s", JSONArray(steps))); saveObjs("chains", l)
    }
    fun deleteChain(name: String): Boolean {
        val l = objs("chains"); val had = l.removeAll { it.getString("n").equals(name, true) }
        saveObjs("chains", l); return had
    }

    // ---- reminders ----
    data class Reminder(val id: Int, val at: Long, val text: String)
    fun reminders(): List<Reminder> = objs("reminders").map { Reminder(it.getInt("id"), it.getLong("at"), it.getString("x")) }
    fun addReminder(r: Reminder) {
        val l = objs("reminders"); l.add(JSONObject().put("id", r.id).put("at", r.at).put("x", r.text)); saveObjs("reminders", l)
    }
    fun removeReminder(id: Int) { val l = objs("reminders"); l.removeAll { it.getInt("id") == id }; saveObjs("reminders", l) }
}
