package app.cardbalances.domain

import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** A card's expiry as printed on it (MM/YY). The card is valid through the end of that month. */
data class Expiry(val year: Int, val month: Int) : Comparable<Expiry> {
    init {
        require(month in 1..12) { "Month must be 1-12" }
    }

    /** Stored form, e.g. 202803 for 03/28. Sorts chronologically. */
    val yyyymm: Int
        get() = year * 100 + month

    /** `03/28` */
    val display: String
        get() = "${month.toString().padStart(2, '0')}/${(year % 100).toString().padStart(2, '0')}"

    fun isExpired(today: LocalDate = today()): Boolean = today.year * 100 + today.month.ordinal + 1 > yyyymm

    override fun compareTo(other: Expiry) = yyyymm.compareTo(other.yyyymm)

    companion object {
        fun fromYyyymm(value: Int) = Expiry(value / 100, value % 100)

        /** Parses the digits a user typed for MM/YY, e.g. `0328`. Null when incomplete or invalid. */
        fun parse(digits: String): Expiry? {
            val d = digits.filter(Char::isDigit)
            if (d.length != 4) return null
            val month = d.take(2).toInt()
            val year = 2000 + d.takeLast(2).toInt()
            return if (month in 1..12) Expiry(year, month) else null
        }

        fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
    }
}
