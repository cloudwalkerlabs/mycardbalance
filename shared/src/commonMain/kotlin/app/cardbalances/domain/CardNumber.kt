package app.cardbalances.domain

enum class CardNetwork(val label: String) {
    VISA("Visa"),
    MASTERCARD("Mastercard"),
    AMEX("Amex"),
    OTHER("Card"),
}

/** What we keep from a typed card number: the last four digits and the network. */
data class CardIdentity(val last4: String, val network: CardNetwork)

sealed interface CardNumberResult {
    data class Valid(val identity: CardIdentity) : CardNumberResult

    data class Invalid(val reason: String) : CardNumberResult
}

object CardNumber {
    /**
     * Accepts either just the last four digits, or a full 12–19 digit card number (spaces and
     * dashes allowed). A full number must pass the Luhn check, and gives us the network too.
     * When only four digits are typed, [fallbackNetwork] is kept.
     */
    fun parse(input: String, fallbackNetwork: CardNetwork = CardNetwork.OTHER): CardNumberResult {
        val digits = input.filter { !it.isWhitespace() && it != '-' }
        if (digits.isEmpty()) return CardNumberResult.Invalid("Enter the card number or its last 4 digits")
        if (!digits.all(Char::isDigit)) return CardNumberResult.Invalid("Digits only")
        return when {
            digits.length == 4 -> CardNumberResult.Valid(CardIdentity(digits, fallbackNetwork))
            digits.length < 12 -> CardNumberResult.Invalid("Enter all digits, or just the last 4")
            digits.length > 19 -> CardNumberResult.Invalid("Too many digits")
            !luhn(digits) -> CardNumberResult.Invalid("That number doesn't look right — check for typos")
            else -> CardNumberResult.Valid(CardIdentity(digits.takeLast(4), detectNetwork(digits)))
        }
    }

    fun detectNetwork(digits: String): CardNetwork {
        val two = digits.take(2).toIntOrNull() ?: return CardNetwork.OTHER
        val four = digits.take(4).toIntOrNull() ?: return CardNetwork.OTHER
        return when {
            digits.startsWith("4") -> CardNetwork.VISA
            two in 51..55 || four in 2221..2720 -> CardNetwork.MASTERCARD
            two == 34 || two == 37 -> CardNetwork.AMEX
            else -> CardNetwork.OTHER
        }
    }

    fun luhn(digits: String): Boolean {
        var sum = 0
        digits.reversed().forEachIndexed { i, c ->
            var d = c - '0'
            if (i % 2 == 1) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return sum % 10 == 0
    }
}
