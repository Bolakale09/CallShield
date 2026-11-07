package com.callshield.ng

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Every incoming call from a number that is not a contact (blocked or not).
 * This history is what the spam-pattern detector learns from. Kept for 30 days.
 */
object SeenCallStore {
    private const val FILE = "seen_calls.json"
    private const val MAX = 2000
    private const val KEEP_MS = 30L * 24 * 60 * 60 * 1000
    private val lock = Any()

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    fun all(context: Context): List<SeenCall> {
        synchronized(lock) {
            val f = file(context)
            if (!f.exists()) return emptyList()
            return try {
                val arr = JSONArray(f.readText())
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    SeenCall(o.optString("n"), o.optLong("t"), o.optBoolean("b"))
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun add(context: Context, call: SeenCall) {
        synchronized(lock) {
            val cutoff = System.currentTimeMillis() - KEEP_MS
            val list = (listOf(call) + all(context)).filter { it.time >= cutoff }.take(MAX)
            val arr = JSONArray()
            list.forEach {
                arr.put(JSONObject().put("n", it.number).put("t", it.time).put("b", it.blocked))
            }
            file(context).writeText(arr.toString())
        }
    }
}
