package app.cardbalances.domain

import kotlin.math.absoluteValue

/** Formats and parses amounts held as integer minor units (cents), so no float rounding ever. */
object Money {
    /** `1234_50` in NZD → `$1,234.50`; negative values get a leading minus. */
    fun format(minor: Long, currency: Currency, showSign: Boolean = false): String {
        val sign =
            when {
                minor < 0 -> "−"
                showSign && minor > 0 -> "+"
                else -> ""
            }
        return sign + currency.symbol + formatNumber(minor.absoluteValue, currency.digits)
    }

    /** The bare number, e.g. `1234.50`, as it should appear pre-filled in an input field. */
    fun formatPlain(minor: Long, currency: Currency): String {
        val unit = pow10(currency.digits)
        val whole = minor / unit
        if (currency.digits == 0) return whole.toString()
        val frac = (minor % unit).absoluteValue.toString().padStart(currency.digits, '0')
        return "$whole.$frac"
    }

    /**
     * Parses user input such as `12`, `12.5`, `$1,234.56` or ` 0.99 ` into minor units. Returns
     * null for anything that isn't a non-negative amount with at most [Currency.digits] decimals.
     */
    fun parse(input: String, currency: Currency): Long? {
        val cleaned = input.trim().removePrefix(currency.symbol.trim()).replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split('.')
        if (parts.size > 2) return null
        val wholePart = parts[0].ifEmpty { "0" }
        val fracPart = parts.getOrNull(1) ?: ""
        if (!wholePart.all(Char::isDigit) || !fracPart.all(Char::isDigit)) return null
        if (fracPart.length > currency.digits) return null
        if (wholePart.length > 12) return null
        val unit = pow10(currency.digits)
        val frac = fracPart.padEnd(currency.digits, '0').ifEmpty { "0" }.toLong()
        return wholePart.toLong() * unit + frac
    }

    private fun formatNumber(minor: Long, digits: Int): String {
        val unit = pow10(digits)
        val whole = (minor / unit).toString().reversed().chunked(3).joinToString(",").reversed()
        if (digits == 0) return whole
        val frac = (minor % unit).toString().padStart(digits, '0')
        return "$whole.$frac"
    }

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }
}
