package com.callshield.ng

import android.content.Context

/**
 * @param block      reject / silence the call
 * @param reason     shown in the blocked-calls list and notifications
 * @param unknown    normalised number if this was a non-contact caller (recorded for pattern detection)
 * @param newSpam    true if this call made the detector flag the number just now
 */
data class Decision(
    val block: Boolean,
    val reason: String,
    val unknown: String? = null,
    val newSpam: Boolean = false
)

/**
 * Decides what to do with an incoming call. Order matters:
 *  1. protection off                    -> allow
 *  2. hidden / private number           -> block (if enabled)
 *  3. your allow list                   -> allow
 *  4. your block list                   -> block
 *  5. saved contact                     -> allow
 *  6. already detected as loan spam     -> block
 *  7. matches a loan-app calling pattern-> block now and remember the number
 *  8. number you called before          -> allow (if enabled)
 *  9. repeat call within 5 minutes      -> allow (if enabled)
 * 10. unknown number                    -> block (if enabled)
 */
object BlockRules {

    fun evaluate(context: Context, rawNumber: String?, isHidden: Boolean): Decision {
        val prefs = Prefs(context)
        if (!prefs.enabled) return Decision(false, "Protection off")

        val number = PhoneUtils.normalize(rawNumber)

        if (isHidden || number.isEmpty()) {
            return if (prefs.blockHidden) Decision(true, "Hidden / private number")
            else Decision(false, "Hidden number (allowed by settings)")
        }

        if (PhoneUtils.matchesList(number, prefs.allowList)) return Decision(false, "On allow list")
        if (PhoneUtils.matchesList(number, prefs.blockList)) {
            return Decision(true, "On your block list", unknown = number)
        }

        when (PhoneUtils.isInContacts(context, rawNumber ?: number)) {
            true -> return Decision(false, "Saved contact")
            null -> return Decision(false, "Contacts permission missing - allowed to be safe")
            false -> Unit
        }

        // From here on the caller is an unknown number.
        if (number in prefs.detectedSpam) {
            return Decision(true, "Loan-app spam (detected earlier)", unknown = number)
        }

        val callLog = PhoneUtils.callLogStats(context, rawNumber ?: number)
        val youCalledThem = (callLog?.outgoingCount ?: 0) > 0

        val threshold = prefs.detectionThreshold
        // Never auto-flag a number you have phoned yourself; you can still mark it manually.
        if (threshold > 0 && !youCalledThem) {
            val verdict = SpamScorer.score(
                number = number,
                now = System.currentTimeMillis(),
                history = SeenCallStore.all(context),
                knownSpam = prefs.allKnownSpam,
                callLog = callLog
            )
            if (verdict.isSpam(threshold)) {
                prefs.markSpam(number)
                return Decision(
                    true, "Loan-app pattern: ${verdict.summary()}",
                    unknown = number, newSpam = true
                )
            }
        }

        if (prefs.allowCalledBefore && youCalledThem) {
            return Decision(false, "You called this number before", unknown = number)
        }

        if (prefs.allowRepeatCallers &&
            BlockedCallStore.blockedRecently(context, number, Prefs.REPEAT_WINDOW_MS)
        ) {
            return Decision(false, "Called again within 5 minutes", unknown = number)
        }

        return if (prefs.blockUnknown) Decision(true, "Unknown number (not in contacts)", unknown = number)
        else Decision(false, "Unknown number (allowed by settings)", unknown = number)
    }
}
