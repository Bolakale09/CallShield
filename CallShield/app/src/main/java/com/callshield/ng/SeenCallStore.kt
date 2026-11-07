package com.callshield.ng

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Every incoming call from a number that is not a contact (blocked or not).
 * This history is what the spam-pattern detector learns from. Kept for 30 days,
 * saved crash-safely and cached in memory.
 */
object SeenCallStore {
    private const val FILE = "seen_calls.json"
    private const val MAX = 2000
    private const val KEEP_MS = 30L * 24 * 60 * 60 * 1000
    private val lock = Any()

    @Volatile
    private var cache: List<SeenCall>? = null

    private fun store(context: Context) = SafeJsonFile(context, FILE)

    fun all(context: Context): List<SeenCall> {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val arr = store(context).read()
            val list = (0 until arr.length()).mapNotNull {
                val o = arr.optJSONObject(it) ?: return@mapNotNull null
                SeenCall(o.optString("n"), o.optLong("t"), o.optBoolean("b"))
            }
            cache = list
            return list
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
            store(context).write(arr)
            cache = list
        }
    }
}
