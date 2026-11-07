package com.callshield.ng

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Called by Android for every incoming call once CallShield is set as the
 * "Caller ID & spam app" (call screening role).
 *
 * Android only waits a few seconds for an answer, so the checks (contacts, call log,
 * history files) run on a background thread. If they ever take longer than
 * [DECISION_BUDGET_MS], the call is allowed to ring rather than risk a silent failure.
 */
class CallBlockerService : CallScreeningService() {

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onScreenCall(callDetails: Call.Details) {
        // Only screen incoming calls; never interfere with calls you make.
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val ctx: Context = applicationContext
        val rawNumber = callDetails.handle?.schemeSpecificPart
        val isHidden = callDetails.handlePresentation != TelecomManager.PRESENTATION_ALLOWED
        val answered = AtomicBoolean(false)

        // Safety net: too slow -> let it ring.
        val timeout = Runnable {
            if (answered.compareAndSet(false, true)) {
                respondToCall(callDetails, CallResponse.Builder().build())
            }
        }
        main.postDelayed(timeout, DECISION_BUDGET_MS)

        worker.execute {
            val decision = try {
                BlockRules.evaluate(ctx, rawNumber, isHidden)
            } catch (e: Exception) {
                Decision(false, "Error - allowed to be safe")
            }

            if (answered.compareAndSet(false, true)) {
                main.removeCallbacks(timeout)
                val response = responseFor(decision, Prefs(ctx))
                main.post { respondToCall(callDetails, response) }
                record(ctx, rawNumber, decision)
            } else {
                // Too late: the call already rang. Still learn from it.
                recordSeenOnly(ctx, decision)
            }
        }
    }

    override fun onDestroy() {
        worker.shutdown()   // lets any queued bookkeeping finish
        super.onDestroy()
    }

    private fun responseFor(decision: Decision, prefs: Prefs): CallResponse = when {
        !decision.block -> CallResponse.Builder().build()
        prefs.silenceInsteadOfReject -> CallResponse.Builder().setSilenceCall(true).build()
        else -> CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(false)      // still visible in Samsung Phone > Recents
            .setSkipNotification(true)  // no "missed call" popup
            .build()
    }

    /** Log, count and notify. Runs on the worker thread after the call has been answered. */
    private fun record(ctx: Context, rawNumber: String?, decision: Decision) {
        try {
            val now = System.currentTimeMillis()
            val prefs = Prefs(ctx)
            decision.unknown?.let { SeenCallStore.add(ctx, SeenCall(it, now, decision.block)) }

            val shown = if (rawNumber.isNullOrBlank()) "Private number" else rawNumber
            if (decision.block) {
                val stored = PhoneUtils.normalize(rawNumber).ifEmpty { "Private number" }
                val today = BlockStats.recordBlock(ctx, now)   // before the log, see BlockStats.total
                BlockedCallStore.add(ctx, BlockedCall(stored, decision.reason, now))
                if (decision.newSpam) {
                    Notifier.spamDetected(ctx, shown, stored, decision.reason)
                }
                if (prefs.notifyOnBlock) {
                    Notifier.blockedSummary(ctx, today)
                }
            } else if (decision.unknown != null && prefs.askAboutUnknown) {
                Notifier.unknownRinging(ctx, shown, decision.unknown)
            }
        } catch (e: Exception) {
            // Bookkeeping must never break call handling
        }
    }

    private fun recordSeenOnly(ctx: Context, decision: Decision) {
        try {
            decision.unknown?.let {
                SeenCallStore.add(ctx, SeenCall(it, System.currentTimeMillis(), false))
            }
        } catch (e: Exception) {
        }
    }

    companion object {
        /** Android gives up after about 5 seconds; answer well before that. */
        private const val DECISION_BUDGET_MS = 3500L
    }
}
