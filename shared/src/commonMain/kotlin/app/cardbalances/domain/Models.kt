package app.cardbalances.domain

import kotlin.time.Instant

data class Card(
    val id: Long,
    val nickname: String,
    val last4: String,
    val network: CardNetwork,
    val currency: Currency,
    val expiry: Expiry,
    /** Derived from the card's ledger by the card_with_balance view; never stored. */
    val balanceMinor: Long,
    val createdAt: Instant,
    val lastActivityAt: Instant,
)

enum class EventType {
    /** Money spent with the card. Amount is what was spent. */
    REDEEM,
    /** The balance was set to a known figure, including when the card was added. Amount is the balance. */
    SET_BALANCE,
}

/** One ledger entry with its running balance, both computed in SQL (the card_ledger view). */
data class CardEvent(
    val id: Long,
    val type: EventType,
    /** As recorded: the balance for SET_BALANCE, the amount spent for REDEEM. */
    val amountMinor: Long,
    /** Signed change this event made to the balance. */
    val deltaMinor: Long,
    val balanceAfterMinor: Long,
    val note: String?,
    val createdAt: Instant,
)
