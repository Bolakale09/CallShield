package com.callshield.ng

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract

object PhoneUtils {

    fun normalize(raw: String?): String = NumberFormat.normalize(raw)

    fun matchesList(number: String, list: Set<String>): Boolean =
        NumberFormat.matchesList(number, list)

    fun hasPermission(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Returns true/false if contacts could be checked, or null if the contacts
     * permission is missing (the caller then allows the call, to be safe).
     */
    fun isInContacts(context: Context, rawNumber: String): Boolean? {
        if (!hasPermission(context, Manifest.permission.READ_CONTACTS)) return null
        val candidates = linkedSetOf(rawNumber)
        val local = normalize(rawNumber)
        if (local.isNotEmpty()) {
            candidates.add(local)
            if (local.startsWith("0") && local.length == 11) {
                candidates.add("+234" + local.substring(1))
            }
        }
        for (candidate in candidates) {
            if (lookupContact(context, candidate)) return true
        }
        return false
    }

    private fun lookupContact(context: Context, number: String): Boolean {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null
            )?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Earlier calls with this number from the phone's call log (last 30 days),
     * or null without call-log permission.
     */
    fun callLogStats(context: Context, rawNumber: String): CallLogStats? {
        if (!hasPermission(context, Manifest.permission.READ_CALL_LOG)) return null
        val target = NumberFormat.last10(normalize(rawNumber))
        if (target.length < 7) return null
        val since = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val incoming = mutableListOf<Long>()
        var outgoing = 0
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(since.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                var rows = 0
                while (c.moveToNext() && rows < 3000) {
                    rows++
                    if (NumberFormat.last10(normalize(c.getString(0))) != target) continue
                    when (c.getInt(1)) {
                        CallLog.Calls.OUTGOING_TYPE -> outgoing++
                        else -> incoming += c.getLong(2)
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }
        // Outgoing calls older than 30 days still count as "you know this number"
        if (outgoing == 0 && haveCalledBefore(context, rawNumber)) outgoing = 1
        return CallLogStats(incoming, outgoing)
    }

    /** True if you have ever made an outgoing call to this number (recent 1000 outgoing calls). */
    fun haveCalledBefore(context: Context, rawNumber: String): Boolean {
        if (!hasPermission(context, Manifest.permission.READ_CALL_LOG)) return false
        val target = NumberFormat.last10(normalize(rawNumber))
        if (target.length < 7) return false
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                "${CallLog.Calls.TYPE} = ?",
                arrayOf(CallLog.Calls.OUTGOING_TYPE.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                var checked = 0
                while (c.moveToNext() && checked < 1000) {
                    checked++
                    if (NumberFormat.last10(normalize(c.getString(0))) == target) return@use true
                }
                false
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reads the last 30 days of the call log and returns
     * (incoming calls per non-contact number, numbers you have called).
     * Slow-ish: call from a background thread.
     */
    fun readCallLogForScan(context: Context): Pair<Map<String, List<Long>>, Set<String>>? {
        if (!hasPermission(context, Manifest.permission.READ_CALL_LOG)) return null
        val since = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val incoming = HashMap<String, MutableList<Long>>()
        val outgoing = HashSet<String>()
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(since.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val n = normalize(c.getString(0))
                    if (n.isEmpty()) continue
                    if (c.getInt(1) == CallLog.Calls.OUTGOING_TYPE) outgoing += n
                    else incoming.getOrPut(n) { mutableListOf() }.add(c.getLong(2))
                }
            }
        } catch (e: Exception) {
            return null
        }
        // Drop saved contacts
        val filtered = incoming.filterKeys { isInContacts(context, it) == false }
        return Pair(filtered, outgoing)
    }
}
