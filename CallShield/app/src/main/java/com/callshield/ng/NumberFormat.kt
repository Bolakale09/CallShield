package com.callshield.ng

/** Pure-Kotlin number helpers (no Android imports). */
object NumberFormat {

    /**
     * Turns any Nigerian-style number into one local format so that
     * "+234 803 123 4567", "2348031234567", "002348031234567" and "08031234567"
     * all compare as "08031234567". Other countries keep their digits with a leading '+'.
     * A trailing '*' (prefix marker) is kept.
     */
    fun normalize(raw: String?): String {
        if (raw == null) return ""
        val trimmed = raw.trim()
        val isPrefix = trimmed.endsWith("*")
        val hadPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return ""

        var result: String = when {
            digits.startsWith("00234") -> "0" + digits.substring(5)
            hadPlus && digits.startsWith("234") -> "0" + digits.substring(3)
            // "2348031234567" without '+': 13 digits starting with 234
            !hadPlus && digits.startsWith("234") && digits.length >= 13 -> "0" + digits.substring(3)
            digits.startsWith("00") -> "+" + digits.substring(2)
            hadPlus -> "+$digits"
            else -> digits
        }
        // "+234 0803…" typed with an extra 0 becomes "00803…": collapse to one leading 0
        if (result.startsWith("00")) result = "0" + result.trimStart('0')
        return if (isPrefix) "$result*" else result
    }

    /** True if [number] (already normalised) matches an entry of [list] (exact or prefix*). */
    fun matchesList(number: String, list: Set<String>): Boolean {
        if (number.isEmpty()) return false
        for (entry in list) {
            if (entry.endsWith("*")) {
                val prefix = entry.dropLast(1)
                if (prefix.isNotEmpty() && number.startsWith(prefix)) return true
            } else if (entry == number) {
                return true
            }
        }
        return false
    }

    /**
     * Two different numbers in the same block of 1000 (everything equal except the
     * last 3 digits). Loan-app call centres buy SIMs and lines in batches, so a new
     * number next to a known spammer is very likely the same operation.
     */
    fun sameNumberBlock(a: String, b: String): Boolean {
        if (a == b) return false
        val da = a.filter { it.isDigit() }
        val db = b.filter { it.isDigit() }
        if (da.length < 10 || da.length != db.length) return false
        return da.dropLast(3) == db.dropLast(3)
    }

    /** "08031234567" -> "0803 123 4567" for display. Anything else is returned unchanged. */
    fun pretty(n: String): String =
        if (n.length == 11 && n.startsWith("0") && n.all { it.isDigit() })
            "${n.substring(0, 4)} ${n.substring(4, 7)} ${n.substring(7)}"
        else n

    /** "08031234567" -> "0803123 4xxx" style label for the number block. */
    fun blockLabel(n: String): String = if (n.length > 3) n.dropLast(3) + "xxx" else n

    fun last10(n: String): String {
        val d = n.filter { it.isDigit() }
        return if (d.length > 10) d.takeLast(10) else d
    }
}
