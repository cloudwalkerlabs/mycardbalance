package app.cardbalances.domain

/** The currencies offered when adding a card. [digits] is the number of minor-unit digits. */
data class Currency(val code: String, val symbol: String, val name: String, val digits: Int = 2) {
    companion object {
        val all: List<Currency> =
            listOf(
                Currency("NZD", "$", "New Zealand dollar"),
                Currency("AUD", "$", "Australian dollar"),
                Currency("USD", "$", "US dollar"),
                Currency("CAD", "$", "Canadian dollar"),
                Currency("EUR", "€", "Euro"),
                Currency("GBP", "£", "British pound"),
                Currency("JPY", "¥", "Japanese yen", digits = 0),
                Currency("CNY", "¥", "Chinese yuan"),
                Currency("HKD", "$", "Hong Kong dollar"),
                Currency("SGD", "$", "Singapore dollar"),
                Currency("CHF", "CHF ", "Swiss franc"),
                Currency("SEK", "kr ", "Swedish krona"),
                Currency("NOK", "kr ", "Norwegian krone"),
                Currency("DKK", "kr ", "Danish krone"),
                Currency("INR", "₹", "Indian rupee"),
                Currency("KRW", "₩", "South Korean won", digits = 0),
                Currency("THB", "฿", "Thai baht"),
                Currency("MYR", "RM ", "Malaysian ringgit"),
                Currency("FJD", "$", "Fijian dollar"),
            )

        private val byCode = all.associateBy { it.code }

        /** Known currencies resolve to their entry; anything else falls back to the code itself. */
        fun of(code: String): Currency = byCode[code] ?: Currency(code, "$code ", code)
    }
}
