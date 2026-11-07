package com.callshield.ng

import java.util.TimeZone

/** One earlier incoming call from a number that was not a contact. */
data class SeenCall(val number: String, val time: Long, val blocked: Boolean)

/** What the phone's own call log says about one number (optional, needs call-log permission). */
data class CallLogStats(
    val incomingTimes: List<Long>,   // times of earlier incoming / missed / rejected calls
    val outgoingCount: Int           // how many times you called this number
)

data class SpamVerdict(val score: Int, val reasons: List<String>) {
    fun isSpam(threshold: Int) = threshold > 0 && score >= threshold
    fun summary() = reasons.joinToString("; ")
}

/**
 * Scores an incoming call from an unknown number against the calling patterns
 * that Nigerian loan-app / debt-recovery call centres are known for:
 *
 *  - the same number calling on several different days ("persistent caller")
 *  - many calls from one number in a day, often redialling straight away
 *  - many *different* unknown numbers calling in a short time (number rotation)
 *  - a new number from the same block of 1000 as a number already marked spam
 *  - calls you keep receiving but never return (from the phone's call log)
 *  - calls late at night / very early morning
 *  - call-centre landlines (020…, Lagos/Abuja fixed lines)
 *
 * Pure Kotlin so it can be tested off-device. All numbers must be normalised.
 */
object SpamScorer {

    const val THRESHOLD_NORMAL = 3
    const val THRESHOLD_STRICT = 2

    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
    private const val DAY = 24 * HOUR

    fun score(
        number: String,
        now: Long,
        history: List<SeenCall>,          // earlier unknown calls, newest first, NOT including this one
        knownSpam: Set<String>,           // numbers already blocked / marked as spam
        callLog: CallLogStats? = null,
        tz: TimeZone = TimeZone.getDefault()
    ): SpamVerdict {
        var score = 0
        val reasons = mutableListOf<String>()
        if (number.isEmpty()) return SpamVerdict(0, reasons)

        val mine = history.filter { it.number == number && it.time <= now }
        val logTimes = callLog?.incomingTimes?.filter { it <= now } ?: emptyList()
        // Merge both sources; if the same call is in both, times are within a minute
        val allTimes = mergeTimes(mine.map { it.time } + logTimes) + now

        // 1. Persistent caller: several different days within the last 7 days
        val week = allTimes.filter { now - it <= 7 * DAY }
        val days = week.map { dayIndex(it, tz) }.toSet().size
        if (days >= 3 || (days >= 2 && week.size >= 3)) {
            score += 3
            reasons += "called on $days days this week"
        }

        // 2. Call volume from this number in the last 24 hours (this call included).
        //    Collectors spread calls over the day; a person in an emergency redials quickly.
        val dayTimes = allTimes.filter { now - it <= DAY }
        val today = dayTimes.size
        val spreadHours = if (dayTimes.isEmpty()) 0L else (now - dayTimes.min()) / HOUR
        when {
            today >= 8 -> { score += 3; reasons += "$today calls in 24 hours" }
            today >= 4 && spreadHours >= 2 -> {
                score += 3; reasons += "$today calls spread over $spreadHours hours"
            }
            today >= 4 -> { score += 2; reasons += "$today calls in 24 hours" }
        }

        // 3. Rapid redial: 3+ calls within 30 minutes
        val burst = allTimes.count { now - it <= 30 * MIN }
        if (burst >= 3 && today < 4) {
            score += 1
            reasons += "redialled $burst times in 30 minutes"
        }

        // 4. Number rotation: lots of different unknown numbers ringing you
        val others6h = history.filter { it.number != number && now - it.time in 0..6 * HOUR }
            .map { it.number }.toSet().size
        val others24h = history.filter { it.number != number && now - it.time in 0..DAY }
            .map { it.number }.toSet().size
        if (others6h >= 3 || others24h >= 6) {
            score += 2
            val n = if (others6h >= 3) "$others6h other unknown numbers in 6 hours"
            else "$others24h other unknown numbers today"
            reasons += "spam wave: $n"
        }

        // 5. Same number block as a known spammer
        val neighbour = knownSpam.firstOrNull { !it.endsWith("*") && NumberFormat.sameNumberBlock(number, it) }
        if (neighbour != null) {
            score += 3
            reasons += "next to known spam ${NumberFormat.blockLabel(neighbour)}"
        }

        // 6. Phone call log: keeps calling, you never call back
        if (callLog != null && callLog.outgoingCount == 0) {
            val month = logTimes.count { now - it <= 30 * DAY }
            if (month >= 3) {
                score += 2
                reasons += "called $month times before and you never called back"
            }
        }

        // 7. Unsociable hours (before 7am or from 9pm)
        val hour = hourOfDay(now, tz)
        if (hour < 7 || hour >= 21) {
            score += 1
            reasons += "calling at night"
        }

        // 8. Call-centre landline (0201 Lagos, 0209 Abuja, other 020x fixed lines)
        if (number.startsWith("020") && number.length == 11) {
            score += 1
            reasons += "call-centre landline"
        }

        return SpamVerdict(score, reasons)
    }

    /**
     * Looks through the phone's past calls for numbers that already behave like
     * loan-app collectors, so they can be blocked before they call again.
     * [incoming] maps number -> times it called; [outgoing] = numbers you have called.
     */
    fun scanHistory(
        incoming: Map<String, List<Long>>,
        outgoing: Set<String>,
        now: Long,
        tz: TimeZone = TimeZone.getDefault()
    ): Map<String, String> {
        val found = linkedMapOf<String, String>()
        val outgoing10 = outgoing.map { NumberFormat.last10(it) }.toSet()
        for ((number, times) in incoming) {
            if (NumberFormat.last10(number) in outgoing10) continue
            val recent = times.filter { now - it in 0..30 * DAY }
            if (recent.size < 3) continue
            val days = recent.map { dayIndex(it, tz) }.toSet().size
            val maxPerDay = recent.groupingBy { dayIndex(it, tz) }.eachCount().values.maxOrNull() ?: 0
            if (days >= 2 || maxPerDay >= 4) {
                found[number] = "${recent.size} calls on $days days, never called back"
            }
        }
        return found
    }

    private fun mergeTimes(times: List<Long>): List<Long> {
        val sorted = times.sorted()
        val out = mutableListOf<Long>()
        for (t in sorted) {
            if (out.isEmpty() || t - out.last() > MIN) out += t
        }
        return out
    }

    private fun dayIndex(t: Long, tz: TimeZone): Long = (t + tz.getOffset(t)) / DAY

    private fun hourOfDay(t: Long, tz: TimeZone): Int = (((t + tz.getOffset(t)) % DAY) / HOUR).toInt()
}
