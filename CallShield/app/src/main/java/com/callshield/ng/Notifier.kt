package com.callshield.ng

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    private const val CHANNEL_BLOCKED = "blocked_calls"
    private const val CHANNEL_ASK = "unknown_callers"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_BLOCKED) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_BLOCKED,
                    context.getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        if (nm.getNotificationChannel(CHANNEL_ASK) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ASK,
                    context.getString(R.string.channel_ask_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { setSound(null, null) }
            )
        }
    }

    fun idFor(number: String): Int = 1000 + (number.hashCode() and 0x7FFFFFF)

    private const val SUMMARY_ID = 42

    /**
     * One notification for all turned-away calls, updated in place:
     * "5 calls turned away today" with the latest few listed. It never buzzes.
     */
    fun blockedSummary(context: Context, todayCount: Int) {
        val recent = BlockedCallStore.today(context, BlockStats.startOfToday()).take(5)
        val lines = recent.map {
            context.getString(
                R.string.notif_summary_line,
                CallDisplay.number(context, it.number),
                CallDisplay.reason(context, it.reason).text
            )
        }
        val title = context.resources.getQuantityString(R.plurals.notif_summary_title, todayCount, todayCount)
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.forEach { style.addLine(it) }
        if (todayCount > lines.size) {
            style.setSummaryText(context.getString(R.string.notif_summary_more, todayCount - lines.size))
        }
        val n = base(context, CHANNEL_BLOCKED)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull() ?: "")
            .setStyle(style)
            .setNumber(todayCount)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        post(context, SUMMARY_ID, n)
    }

    private const val EXPECTING_ID = 43

    /** Ongoing reminder while strangers may ring; disappears by itself when the time is up. */
    fun expecting(context: Context, until: Long) {
        val remaining = until - System.currentTimeMillis()
        if (remaining <= 0) return
        val time = android.text.format.DateUtils.formatDateTime(
            context, until, android.text.format.DateUtils.FORMAT_SHOW_TIME
        )
        val n = base(context, CHANNEL_BLOCKED)
            .setContentTitle(context.getString(R.string.expecting_notif_title, time))
            .setContentText(context.getString(R.string.expecting_notif_text))
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setTimeoutAfter(remaining)
            .addAction(0, context.getString(R.string.stop_now),
                SpamActionReceiver.stopExpecting(context, EXPECTING_ID))
            .build()
        post(context, EXPECTING_ID, n)
    }

    fun cancelExpecting(context: Context) = cancel(context, EXPECTING_ID)

    /** A number was just flagged by the loan-app pattern detector. Offers "Not spam". */
    fun spamDetected(context: Context, shown: String, normalized: String, reason: String) {
        val id = idFor(normalized)
        val n = base(context, CHANNEL_BLOCKED)
            .setContentTitle(context.getString(R.string.notif_spam_title, shown))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .addAction(0, context.getString(R.string.not_spam),
                SpamActionReceiver.pending(context, SpamActionReceiver.ACTION_NOT_SPAM, normalized, id))
            .build()
        post(context, id, n)
    }

    /** An unknown number is ringing. Offers "Block as loan spam" (hangs up if allowed). */
    fun unknownRinging(context: Context, shown: String, normalized: String) {
        val id = idFor(normalized)
        val n = base(context, CHANNEL_ASK)
            .setContentTitle(context.getString(R.string.notif_unknown_title, shown))
            .setContentText(context.getString(R.string.notif_unknown_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setTimeoutAfter(10 * 60 * 1000L)
            .addAction(0, context.getString(R.string.block_as_spam),
                SpamActionReceiver.pending(context, SpamActionReceiver.ACTION_SPAM, normalized, id))
            .addAction(0, context.getString(R.string.not_spam),
                SpamActionReceiver.pending(context, SpamActionReceiver.ACTION_NOT_SPAM, normalized, id))
            .build()
        post(context, id, n)
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    private fun base(context: Context, channel: String): NotificationCompat.Builder {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, BlockedLogActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(open)
            .setAutoCancel(true)
    }

    private fun post(context: Context, id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            !PhoneUtils.hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        ) return
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            // Notification permission revoked; ignore
        }
    }
}
