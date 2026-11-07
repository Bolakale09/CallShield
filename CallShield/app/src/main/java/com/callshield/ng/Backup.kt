package com.callshield.ng

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lists and settings saved to one file, so a reinstall or a new phone doesn't
 * start from zero. Restoring merges lists (nothing you have now is lost) and
 * replaces settings.
 */
data class BackupData(
    val enabled: Boolean,
    val blockUnknown: Boolean,
    val blockHidden: Boolean,
    val allowCalledBefore: Boolean,
    val allowRepeatCallers: Boolean,
    val silenceInsteadOfReject: Boolean,
    val notifyOnBlock: Boolean,
    val askAboutUnknown: Boolean,
    val detectionLevel: Int,
    val allowList: Set<String>,
    val blockList: Set<String>,
    val detectedSpam: Set<String>
)

object Backup {
    const val FORMAT = "CallShield backup"
    const val VERSION = 1

    fun toJson(d: BackupData, now: Long): String {
        val settings = JSONObject()
            .put("enabled", d.enabled)
            .put("blockUnknown", d.blockUnknown)
            .put("blockHidden", d.blockHidden)
            .put("allowCalledBefore", d.allowCalledBefore)
            .put("allowRepeatCallers", d.allowRepeatCallers)
            .put("silenceInsteadOfReject", d.silenceInsteadOfReject)
            .put("notifyOnBlock", d.notifyOnBlock)
            .put("askAboutUnknown", d.askAboutUnknown)
            .put("detectionLevel", d.detectionLevel)
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("saved", now)
            .put("settings", settings)
            .put("allow", JSONArray(d.allowList.sorted()))
            .put("block", JSONArray(d.blockList.sorted()))
            .put("spam", JSONArray(d.detectedSpam.sorted()))
            .toString(2)
    }

    /** Returns null if the text isn't a CallShield backup. */
    fun fromJson(text: String): BackupData? {
        val o = try { JSONObject(text) } catch (e: Exception) { return null }
        if (o.optString("format") != FORMAT) return null
        val s = o.optJSONObject("settings") ?: JSONObject()
        return BackupData(
            enabled = s.optBoolean("enabled", true),
            blockUnknown = s.optBoolean("blockUnknown", true),
            blockHidden = s.optBoolean("blockHidden", true),
            allowCalledBefore = s.optBoolean("allowCalledBefore", false),
            allowRepeatCallers = s.optBoolean("allowRepeatCallers", true),
            silenceInsteadOfReject = s.optBoolean("silenceInsteadOfReject", false),
            notifyOnBlock = s.optBoolean("notifyOnBlock", true),
            askAboutUnknown = s.optBoolean("askAboutUnknown", true),
            detectionLevel = s.optInt("detectionLevel", 1).coerceIn(0, 2),
            allowList = strings(o.optJSONArray("allow")),
            blockList = strings(o.optJSONArray("block")),
            detectedSpam = strings(o.optJSONArray("spam"))
        )
    }

    /** Only well-formed entries survive (a backup file may have been edited by hand). */
    private fun strings(a: JSONArray?): Set<String> {
        if (a == null) return emptySet()
        return (0 until a.length())
            .map { NumberFormat.normalize(a.optString(it)) }
            .filter { it.isNotEmpty() && it.trimEnd('*').length >= 4 }
            .toSet()
    }
}
