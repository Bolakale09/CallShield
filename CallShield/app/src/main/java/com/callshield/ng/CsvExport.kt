package com.callshield.ng

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds the turned-away-calls spreadsheet (CSV) used as evidence, e.g. for an FCCPC
 * complaint against a loan app. Pure Kotlin so it can be tested off-device.
 */
object CsvExport {

    data class Row(val time: Long, val number: String, val reason: String, val loanSpam: Boolean)

    val HEADER = listOf("Date", "Time", "Number", "Loan spam", "Why it was blocked")

    fun build(rows: List<Row>, tz: TimeZone = TimeZone.getDefault()): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
        val time = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = tz }
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",") { escape(it) }).append("\r\n")
        // Oldest first reads naturally as a record of events
        for (r in rows.sortedBy { it.time }) {
            val d = Date(r.time)
            sb.append(
                listOf(
                    date.format(d),
                    time.format(d),
                    NumberFormat.pretty(r.number),   // spaces keep the leading 0 in Excel/Sheets
                    if (r.loanSpam) "Yes" else "No",
                    r.reason
                ).joinToString(",") { escape(it) }
            ).append("\r\n")
        }
        return sb.toString()
    }

    /** RFC 4180 quoting: wrap in quotes when needed, double any quotes inside. */
    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"" + field.replace("\"", "\"\"") + "\""
        else field

    fun fileName(now: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
        return "callshield-turned-away-${date.format(Date(now))}.csv"
    }
}
