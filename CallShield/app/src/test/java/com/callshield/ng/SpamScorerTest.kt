package com.callshield.ng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class SpamScorerTest {

    private val lagos: TimeZone = TimeZone.getTimeZone("Africa/Lagos")
    private val min = 60_000L
    private val hour = 60 * min
    private val day = 24 * hour
    private val midnight = 20_000L * day - hour            // a local midnight in Lagos (UTC+1)
    private fun at(d: Int, h: Int, m: Int = 0) = midnight + d * day + h * hour + m * min

    private val n = "08031234567"
    private val normal = SpamScorer.THRESHOLD_NORMAL
    private val strict = SpamScorer.THRESHOLD_STRICT

    private fun score(
        number: String, now: Long, history: List<SeenCall> = emptyList(),
        spam: Set<String> = emptySet(), log: CallLogStats? = null
    ) = SpamScorer.score(number, now, history, spam, log, lagos)

    private fun seen(number: String, t: Long) = SeenCall(number, t, true)

    @Test fun firstCallFromStrangerIsNotSpam() {
        assertEquals(0, score(n, at(1, 14)).score)
    }

    @Test fun quickRedialIsNotSpam() {
        assertFalse(score(n, at(1, 14, 2), listOf(seen(n, at(1, 14)))).isSpam(strict))
    }

    @Test fun emergencyBurstPassesNormalButNotStrict() {
        val v = score(n, at(1, 14, 10), (0 until 4).map { seen(n, at(1, 14, it * 2)) })
        assertFalse(v.isSpam(normal))
        assertTrue(v.isSpam(strict))
    }

    @Test fun strangerAtNightIsNotSpam() {
        assertFalse(score(n, at(1, 23, 3), listOf(seen(n, at(1, 23)))).isSpam(normal))
    }

    @Test fun numberCallingOnTwoDaysIsSpam() {
        val v = score(n, at(1, 10), listOf(seen(n, at(0, 15)), seen(n, at(0, 9))))
        assertTrue(v.summary(), v.isSpam(normal))
    }

    @Test fun callsSpreadOverTheDayAreSpam() {
        val v = score(n, at(1, 12), listOf(seen(n, at(1, 11)), seen(n, at(1, 9)), seen(n, at(1, 8))))
        assertTrue(v.summary(), v.isSpam(normal))
    }

    @Test fun neighbourOfKnownSpamIsSpam() {
        assertTrue(score("08031234999", at(1, 14), spam = setOf(n)).isSpam(normal))
        assertFalse(score("08031299999", at(1, 14), spam = setOf(n)).isSpam(normal))
    }

    @Test fun spamWaveAloneIsStrictOnly() {
        val wave = (0 until 3).map { seen("0901000000$it", at(1, 10 + it)) }
        val v = score(n, at(1, 14), wave)
        assertFalse(v.isSpam(normal))
        assertTrue(v.isSpam(strict))
    }

    @Test fun spamWaveFromCallCentreLandlineIsSpam() {
        val wave = (0 until 3).map { seen("0901000000$it", at(1, 10 + it)) }
        assertTrue(score("02012345678", at(1, 14), wave).isSpam(normal))
    }

    @Test fun ignoredCallsInCallLogAreSpam() {
        val log = CallLogStats(listOf(at(2, 10), at(5, 11), at(8, 12)), outgoingCount = 0)
        assertTrue(score(n, at(10, 14), log = log).isSpam(normal))
    }

    @Test fun deliveryRiderCallingTwiceIsNotSpam() {
        assertEquals(0, score(n, at(1, 15), listOf(seen(n, at(1, 14)))).score)
    }

    @Test fun offModeNeverFlags() {
        val v = score(n, at(1, 10), listOf(seen(n, at(0, 15)), seen(n, at(0, 9))))
        assertFalse(v.isSpam(0))
    }

    @Test fun historyScanFindsPersistentCallers() {
        val found = SpamScorer.scanHistory(
            incoming = mapOf(
                n to listOf(at(1, 9), at(2, 9), at(3, 9)),           // 3 days, never returned
                "08051112222" to listOf(at(3, 9), at(3, 10), at(3, 11)), // you called them back
                "08061112222" to listOf(at(3, 9))                      // one call only
            ),
            outgoing = setOf("+2348051112222"),
            now = at(4, 12),
            tz = lagos
        )
        assertEquals(setOf(n), found.keys)
    }

    @Test fun nigerianFormatsNormaliseTogether() {
        assertEquals(n, NumberFormat.normalize("+234 803 123 4567"))
        assertEquals(n, NumberFormat.normalize("2348031234567"))
        assertEquals(n, NumberFormat.normalize("002348031234567"))
        assertEquals(n, NumberFormat.normalize("+234 0803 123 4567"))
        assertEquals("070*", NumberFormat.normalize("+23470*"))
        assertTrue(NumberFormat.matchesList("07012345678", setOf("070*")))
    }

    @Test fun numbersAreSpacedForReading() {
        assertEquals("0803 123 4567", NumberFormat.pretty("08031234567"))
        assertEquals("0700*", NumberFormat.pretty("0700*"))
        assertEquals("+447700900123", NumberFormat.pretty("+447700900123"))
    }
}
