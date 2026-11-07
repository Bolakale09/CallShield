package com.callshield.ng

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager

/**
 * Called by Android for every incoming call once CallShield is set as the
 * "Caller ID & spam app" (call screening role). Must answer within a few seconds.
 */
class CallBlockerService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // Only screen incoming calls; never interfere with calls you make.
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val rawNumber = callDetails.handle?.schemeSpecificPart
        val isHidden = callDetails.handlePresentation != TelecomManager.PRESENTATION_ALLOWED

        val decision = try {
            BlockRules.evaluate(this, rawNumber, isHidden)
        } catch (e: Exception) {
            Decision(false, "Error - allowed to be safe")
        }
        val prefs = Prefs(this)

        val response = if (!decision.block) {
            CallResponse.Builder().build()
        } else if (prefs.silenceInsteadOfReject) {
            CallResponse.Builder().setSilenceCall(true).build()
        } else {
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)      // still visible in Samsung Phone > Recents
                .setSkipNotification(true)  // no "missed call" popup
                .build()
        }
        respondToCall(callDetails, response)

        // Bookkeeping after responding, so the call is never delayed by it.
        try {
            val now = System.currentTimeMillis()
            decision.unknown?.let { SeenCallStore.add(this, SeenCall(it, now, decision.block)) }

            val shown = if (rawNumber.isNullOrBlank()) "Private number" else rawNumber
            if (decision.block) {
                val stored = PhoneUtils.normalize(rawNumber).ifEmpty { "Private number" }
                BlockedCallStore.add(this, BlockedCall(stored, decision.reason, now))
                if (decision.newSpam) {
                    Notifier.spamDetected(this, shown, stored, decision.reason)
                } else if (prefs.notifyOnBlock) {
                    Notifier.blocked(this, shown, decision.reason)
                }
            } else if (decision.unknown != null && prefs.askAboutUnknown) {
                Notifier.unknownRinging(this, shown, decision.unknown)
            }
        } catch (e: Exception) {
            // Logging must never break call handling
        }
    }
}
