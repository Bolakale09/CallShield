package com.callshield.ng

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BlockedCall(val number: String, val reason: String, val time: Long)

/**
 * The most recent turned-away calls (newest first). Saved crash-safely and kept in
 * memory, so the home screen and the call screener don't re-read the file every time.
 * The lifetime count lives in [BlockStats], so it isn't limited by [MAX].
 */
object BlockedCallStore {
    private const val FILE = "blocked_calls.json"
    private const val MAX = 300
    private val lock = Any()

    @Volatile
    private var cache: List<BlockedCall>? = null

    private fun store(context: Context) = SafeJsonFile(context, FILE)

    fun all(context: Context): List<BlockedCall> {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val arr = store(context).read()
            val list = (0 until arr.length()).mapNotNull {
                val o = arr.optJSONObject(it) ?: return@mapNotNull null
                BlockedCall(o.optString("n"), o.optString("r"), o.optLong("t"))
            }
            cache = list
            return list
        }
    }

    fun add(context: Context, call: BlockedCall) {
        synchronized(lock) {
            val list = (listOf(call) + all(context)).take(MAX)
            val arr = JSONArray()
            list.forEach {
                arr.put(JSONObject().put("n", it.number).put("r", it.reason).put("t", it.time))
            }
            store(context).write(arr)
            cache = list
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            store(context).delete()
            cache = emptyList()
        }
    }

    /** Was this (normalised) number blocked within the last [windowMs]? */
    fun blockedRecently(context: Context, number: String, windowMs: Long): Boolean {
        if (number.isEmpty()) return false
        val since = System.currentTimeMillis() - windowMs
        return all(context).any { it.number == number && it.time >= since }
    }

    /** Today's turned-away calls, newest first (for the summary notification). */
    fun today(context: Context, startOfToday: Long): List<BlockedCall> =
        all(context).takeWhile { it.time >= startOfToday }
}
