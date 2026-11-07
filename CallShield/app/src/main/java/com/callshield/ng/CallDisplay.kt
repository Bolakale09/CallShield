package com.callshield.ng

import android.content.Context
import android.text.format.DateUtils
import android.view.View
import android.widget.TextView

/** Turns stored blocked-call records into what the screens show. */
object CallDisplay {

    data class Reason(val spam: Boolean, val text: String)

    fun number(context: Context, stored: String): String =
        if (stored == "Private number" || stored.isEmpty()) context.getString(R.string.private_number)
        else NumberFormat.pretty(stored)

    fun reason(context: Context, raw: String): Reason = when {
        raw.startsWith("Loan-app pattern: ") -> Reason(
            true,
            raw.removePrefix("Loan-app pattern: ").replace("; ", ", ")
                .replaceFirstChar { it.uppercase() }
        )
        raw.startsWith("Loan-app spam") -> Reason(true, context.getString(R.string.reason_caught_before))
        raw.startsWith("Unknown number") -> Reason(false, context.getString(R.string.reason_unknown))
        raw.startsWith("Hidden") -> Reason(false, context.getString(R.string.reason_hidden))
        raw.startsWith("On your block list") -> Reason(false, context.getString(R.string.reason_block_list))
        else -> Reason(false, raw)
    }

    fun time(context: Context, t: Long): String = when {
        DateUtils.isToday(t) ->
            DateUtils.formatDateTime(context, t, DateUtils.FORMAT_SHOW_TIME)
        DateUtils.isToday(t + DateUtils.DAY_IN_MILLIS) ->
            context.getString(R.string.yesterday)
        else -> DateUtils.formatDateTime(
            context, t,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR
        )
    }

    /** Fills an item_call row. */
    fun bind(view: View, call: BlockedCall) {
        val ctx = view.context
        val reason = reason(ctx, call.reason)
        view.findViewById<TextView>(R.id.callNumber).text = number(ctx, call.number)
        view.findViewById<TextView>(R.id.callReason).text = reason.text
        view.findViewById<View>(R.id.callTag).visibility = if (reason.spam) View.VISIBLE else View.GONE
        view.findViewById<TextView>(R.id.callTime).text = time(ctx, call.time)
    }
}
