package dev.fanchao.mycardbalance.domain

enum class CardSortField(val label: String, val ascendingLabel: String, val descendingLabel: String) {
    EXPIRY("Expiry date", "Expiring soonest first", "Expiring latest first"),
    RECENT("Recently used", "Least recent first", "Most recent first"),
    BALANCE("Balance", "Lowest first", "Highest first"),
}

/** How the card list is ordered. Used-up cards always go last, whatever the sort. */
data class CardSort(val field: CardSortField, val ascending: Boolean) {
    val label: String
        get() = if (ascending) this.field.ascendingLabel else this.field.descendingLabel

    /** Stored form, e.g. `EXPIRY:ASC`. */
    fun encode() = "${this.field.name}:${if (ascending) "ASC" else "DESC"}"

    companion object {
        /** Cards closest to expiring first, so they get spent before they lapse. */
        val Default = CardSort(CardSortField.EXPIRY, ascending = true)

        fun decode(value: String?): CardSort {
            val (field, dir) = value?.split(':')?.takeIf { it.size == 2 } ?: return Default
            val f = CardSortField.entries.firstOrNull { it.name == field } ?: return Default
            return CardSort(f, dir == "ASC")
        }
    }
}
