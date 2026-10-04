package com.sadrazam.lusifer.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Komut geçmişi: cihazda tutulur, tek dokunuşla silinir. */
object History {
    data class Entry(val time: Long, val heard: String, val reply: String, val tool: String)

    private const val MAX = 200
    private fun file(ctx: Context) = File(ctx.filesDir, "history.json")

    @Synchronized
    fun all(ctx: Context): List<Entry> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val a = JSONArray(f.readText())
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Entry(o.optLong("t"), o.optString("h"), o.optString("r"), o.optString("k"))
            }
        } catch (e: Exception) { emptyList() }
    }

    @Synchronized
    fun add(ctx: Context, heard: String, reply: String, tool: String) {
        val list = all(ctx).toMutableList()
        list.add(Entry(System.currentTimeMillis(), heard, reply, tool))
        val trimmed = if (list.size > MAX) list.takeLast(MAX) else list
        val a = JSONArray()
        for (e in trimmed) a.put(JSONObject().put("t", e.time).put("h", e.heard).put("r", e.reply).put("k", e.tool))
        file(ctx).writeText(a.toString())
    }

    @Synchronized
    fun clear(ctx: Context) { file(ctx).delete() }
}
