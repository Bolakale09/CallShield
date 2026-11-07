package com.callshield.ng

import android.content.Context
import java.util.Calendar

/**
 * Counts of turned-away calls: since install, and today.
 * Separate from the log, which only keeps the latest 300 entries.
 */
object BlockStats {
    private const val PREFS = "callshield_stats"
    private const val KEY_TOTAL = "total"
    private const val KEY_DAY = "day"
    private const val KEY_TODAY = "today"
    private val lock = Any()

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Midnight today in the phone's time zone. */
    fun startOfToday(now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun total(context: Context): Int = synchronized(lock) {
        val prefs = sp(context)
        if (!prefs.contains(KEY_TOTAL)) {
            // First run after updating: start from what the log already holds
            val start = BlockedCallStore.all(context).size
            prefs.edit().putInt(KEY_TOTAL, start).apply()
            start
        } else {
            prefs.getInt(KEY_TOTAL, 0)
        }
    }

    fun today(context: Context): Int = synchronized(lock) {
        val prefs = sp(context)
        if (prefs.getLong(KEY_DAY, 0L) == startOfToday()) prefs.getInt(KEY_TODAY, 0) else 0
    }

    /** Counts one more blocked call. Returns the new count for today. */
    fun recordBlock(context: Context, now: Long = System.currentTimeMillis()): Int = synchronized(lock) {
        val total = total(context) + 1
        val day = startOfToday(now)
        val prefs = sp(context)
        val today = (if (prefs.getLong(KEY_DAY, 0L) == day) prefs.getInt(KEY_TODAY, 0) else 0) + 1
        prefs.edit()
            .putInt(KEY_TOTAL, total)
            .putLong(KEY_DAY, day)
            .putInt(KEY_TODAY, today)
            .apply()
        today
    }
}
