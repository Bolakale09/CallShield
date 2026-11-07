package com.callshield.ng

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import android.widget.Toast

/** Handles notification buttons: "Block as loan spam", "Not spam" and "Stop now". */
class SpamActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)

        when (intent.action) {
            ACTION_SPAM -> {
                val number = intent.getStringExtra(EXTRA_NUMBER) ?: return
                prefs.markSpam(number)
                val hungUp = endRingingCall(context)
                Toast.makeText(
                    context,
                    if (hungUp) R.string.toast_spam_hung_up else R.string.toast_spam_next_time,
                    Toast.LENGTH_LONG
                ).show()
            }
            ACTION_NOT_SPAM -> {
                val number = intent.getStringExtra(EXTRA_NUMBER) ?: return
                prefs.addToAllowList(number)
                Toast.makeText(context, R.string.added_allow, Toast.LENGTH_SHORT).show()
            }
            ACTION_STOP_EXPECTING -> {
                prefs.expectingUntil = 0L
                Toast.makeText(context, R.string.expecting_stopped, Toast.LENGTH_SHORT).show()
            }
        }
        if (notifId != 0) Notifier.cancel(context, notifId)
    }

    /** Hangs up the ringing call. Needs the "Phone" (answer calls) permission (Full edition). */
    private fun endRingingCall(context: Context): Boolean {
        if (!PhoneUtils.hasPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)) return false
        return try {
            val tm = context.getSystemService(TelecomManager::class.java) ?: return false
            @Suppress("DEPRECATION")
            tm.endCall()
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val ACTION_SPAM = "com.callshield.ng.ACTION_SPAM"
        const val ACTION_NOT_SPAM = "com.callshield.ng.ACTION_NOT_SPAM"
        const val ACTION_STOP_EXPECTING = "com.callshield.ng.ACTION_STOP_EXPECTING"
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_NOTIF_ID = "notif_id"

        fun pending(context: Context, action: String, number: String, notifId: Int): PendingIntent {
            val intent = Intent(context, SpamActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_NOTIF_ID, notifId)
            val requestCode = notifId * 2 + if (action == ACTION_SPAM) 1 else 0
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun stopExpecting(context: Context, notifId: Int): PendingIntent {
            val intent = Intent(context, SpamActionReceiver::class.java)
                .setAction(ACTION_STOP_EXPECTING)
                .putExtra(EXTRA_NOTIF_ID, notifId)
            return PendingIntent.getBroadcast(
                context, 7, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
