package com.callshield.ng

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BlockedCall(val number: String, val reason: String, val time: Long)

/** Keeps the most recent blocked calls in a small JSON file. */
object BlockedCallStore {
    private const val FILE = "blocked_calls.json"
    private const val MAX = 300
    private val lock = Any()

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    fun all(context: Context): List<BlockedCall> {
        synchronized(lock) {
            val f = file(context)
            if (!f.exists()) return emptyList()
            return try {
                val arr = JSONArray(f.readText())
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    BlockedCall(o.optString("n"), o.optString("r"), o.optLong("t"))
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun add(context: Context, call: BlockedCall) {
        synchronized(lock) {
            val list = (listOf(call) + all(context)).take(MAX)
            val arr = JSONArray()
            list.forEach {
                arr.put(JSONObject().put("n", it.number).put("r", it.reason).put("t", it.time))
            }
            file(context).writeText(arr.toString())
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            file(context).delete()
        }
    }

    /** Was this (normalised) number blocked within the last [windowMs]? */
    fun blockedRecently(context: Context, number: String, windowMs: Long): Boolean {
        if (number.isEmpty()) return false
        val since = System.currentTimeMillis() - windowMs
        return all(context).any { it.number == number && it.time >= since }
    }
}
