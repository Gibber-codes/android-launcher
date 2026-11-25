package codes.gibber.phone.data

import android.telephony.PhoneNumberUtils
import java.util.Locale

/** Small helpers for phone numbers, so "+1 (970) 555-0100" and "9705550100" count as the same person. */
object Numbers {
    /** A key that matches the same number written different ways. US numbers keep their last 10 digits. */
    fun key(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val clean = clean(raw)
        if (clean.contains('@')) return clean.lowercase(Locale.US)
        val digits = clean.filter { it.isDigit() }
        if (digits.isEmpty()) return clean.lowercase(Locale.US)
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    /** Drops what picture messages add to an address, like "/TYPE=PLMN". */
    fun clean(raw: String): String {
        var s = raw.trim()
        val slash = s.indexOf("/TYPE=", ignoreCase = true)
        if (slash >= 0) s = s.substring(0, slash)
        return s
    }

    /** "(970) 555-0100" for showing. */
    fun pretty(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unknown"
        val clean = clean(raw)
        if (clean.contains('@')) return clean
        return PhoneNumberUtils.formatNumber(clean, "US") ?: clean
    }

    fun same(a: String?, b: String?): Boolean {
        val ka = key(a)
        return ka.isNotEmpty() && ka == key(b)
    }

    /** True when it looks like something you can call (not an email address or a word). */
    fun dialable(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val c = clean(raw)
        return !c.contains('@') && c.count { it.isDigit() } >= 3
    }
}
