package com.callshield.ng

import android.content.Context
import android.content.SharedPreferences

/** All user settings, stored in SharedPreferences. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("callshield", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_ENABLED, v).apply()

    /** Block every number that is not saved in contacts. */
    var blockUnknown: Boolean
        get() = sp.getBoolean(KEY_BLOCK_UNKNOWN, true)
        set(v) = sp.edit().putBoolean(KEY_BLOCK_UNKNOWN, v).apply()

    /** Block private / hidden / "No caller ID" calls. */
    var blockHidden: Boolean
        get() = sp.getBoolean(KEY_BLOCK_HIDDEN, true)
        set(v) = sp.edit().putBoolean(KEY_BLOCK_HIDDEN, v).apply()

    /** Let through numbers you have dialled before (needs call log permission). */
    var allowCalledBefore: Boolean
        get() = sp.getBoolean(KEY_ALLOW_CALLED, false)
        set(v) = sp.edit().putBoolean(KEY_ALLOW_CALLED, v).apply()

    /** If the same unknown number calls again within a few minutes, let it ring (emergencies). */
    var allowRepeatCallers: Boolean
        get() = sp.getBoolean(KEY_ALLOW_REPEAT, true)
        set(v) = sp.edit().putBoolean(KEY_ALLOW_REPEAT, v).apply()

    /** Silence (no ring, still shows as missed) instead of rejecting outright. */
    var silenceInsteadOfReject: Boolean
        get() = sp.getBoolean(KEY_SILENCE, false)
        set(v) = sp.edit().putBoolean(KEY_SILENCE, v).apply()

    var notifyOnBlock: Boolean
        get() = sp.getBoolean(KEY_NOTIFY, true)
        set(v) = sp.edit().putBoolean(KEY_NOTIFY, v).apply()

    /** Numbers that are always allowed. Stored normalised (see [PhoneUtils.normalize]). */
    var allowList: Set<String>
        get() = sp.getStringSet(KEY_ALLOW_LIST, emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_ALLOW_LIST, HashSet(v)).apply()

    /** Numbers / prefixes that are always blocked. Entries ending in '*' are prefixes. */
    var blockList: Set<String>
        get() = sp.getStringSet(KEY_BLOCK_LIST, emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_BLOCK_LIST, HashSet(v)).apply()

    /** Loan-app spam detection: 0 = off, 1 = normal, 2 = strict. */
    var detectionLevel: Int
        get() = sp.getInt(KEY_DETECTION, 1)
        set(v) = sp.edit().putInt(KEY_DETECTION, v).apply()

    val detectionThreshold: Int
        get() = when (detectionLevel) {
            1 -> SpamScorer.THRESHOLD_NORMAL
            2 -> SpamScorer.THRESHOLD_STRICT
            else -> 0
        }

    /** When an unknown number is allowed to ring, show a "Block as spam" notification. */
    var askAboutUnknown: Boolean
        get() = sp.getBoolean(KEY_ASK_UNKNOWN, true)
        set(v) = sp.edit().putBoolean(KEY_ASK_UNKNOWN, v).apply()

    /** Numbers detected (or reported by you) as loan-app spam. Always blocked. */
    var detectedSpam: Set<String>
        get() = sp.getStringSet(KEY_DETECTED, emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_DETECTED, HashSet(v)).apply()

    /** Everything known to be spam: manual block list + detected. Used for number-range matching. */
    val allKnownSpam: Set<String>
        get() = blockList + detectedSpam

    fun addToAllowList(entry: String) {
        allowList = allowList + entry
        blockList = blockList - entry
        detectedSpam = detectedSpam - entry
    }

    fun addToBlockList(entry: String) {
        blockList = blockList + entry
        allowList = allowList - entry
    }

    fun markSpam(entry: String) {
        if (entry.isEmpty()) return
        detectedSpam = detectedSpam + entry
        allowList = allowList - entry
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BLOCK_UNKNOWN = "block_unknown"
        private const val KEY_BLOCK_HIDDEN = "block_hidden"
        private const val KEY_ALLOW_CALLED = "allow_called_before"
        private const val KEY_ALLOW_REPEAT = "allow_repeat"
        private const val KEY_SILENCE = "silence_instead"
        private const val KEY_NOTIFY = "notify"
        private const val KEY_ALLOW_LIST = "allow_list"
        private const val KEY_BLOCK_LIST = "block_list"
        private const val KEY_DETECTION = "detection_level"
        private const val KEY_ASK_UNKNOWN = "ask_unknown"
        private const val KEY_DETECTED = "detected_spam"

        const val REPEAT_WINDOW_MS = 5 * 60 * 1000L
    }
}
