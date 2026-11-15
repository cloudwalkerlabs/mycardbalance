package app.cardbalances.data

import app.cardbalances.domain.Card
import app.cardbalances.domain.CardEvent
import app.cardbalances.domain.CardIdentity
import app.cardbalances.domain.CardNetwork
import app.cardbalances.domain.CardSort
import app.cardbalances.domain.Currency
import app.cardbalances.domain.EventType
import app.cardbalances.domain.Expiry
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Cards and their ledger. Balances are never written anywhere: the card_ledger and
 * card_with_balance views derive them from the events, so a balance can't disagree with its history.
 */
class CardRepository(db: CardDatabase, private val clock: Clock = Clock.System) {
    private val dao = db.cardDao()

    fun observeCards(sort: CardSort = CardSort.Default): Flow<List<Card>> =
        dao.observeCards(sort.field.name, sort.ascending).map { rows -> rows.map { it.toDomain() } }

    fun observeSort(): Flow<CardSort> = dao.observePreference(SORT_KEY).map(CardSort::decode)

    suspend fun setSort(sort: CardSort) = dao.putPreference(PreferenceEntity(SORT_KEY, sort.encode()))

    fun observeCard(id: Long): Flow<Card?> = dao.observeCard(id).map { it?.toDomain() }

    fun observeEvents(cardId: Long): Flow<List<CardEvent>> = dao.observeLedger(cardId).map { rows -> rows.map { it.toDomain() } }

    suspend fun addCard(nickname: String, identity: CardIdentity, currency: Currency, expiry: Expiry, balanceMinor: Long): Long {
        require(balanceMinor >= 0) { "Balance can't be negative" }
        val card =
            CardEntity(
                nickname = nickname.trim(),
                last4 = identity.last4,
                network = identity.network.name,
                currency = currency.code,
                expiry = expiry.yyyymm,
                createdAt = now(),
            )
        return dao.addCard(card, balanceMinor)
    }

    suspend fun updateDetails(id: Long, nickname: String, identity: CardIdentity, currency: Currency, expiry: Expiry) =
        dao.updateDetails(id, nickname.trim(), identity.last4, identity.network.name, currency.code, expiry.yyyymm)

    /** Records a purchase; fails rather than letting the balance go negative. Returns what's left. */
    suspend fun redeem(id: Long, amountMinor: Long, note: String?): Result<Long> {
        require(amountMinor > 0) { "Amount must be positive" }
        return try {
            dao.redeem(id, amountMinor, note.clean(), now())?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("Card not found"))
        } catch (e: OverspendException) {
            Result.failure(e)
        }
    }

    suspend fun setBalance(id: Long, balanceMinor: Long, note: String?) {
        require(balanceMinor >= 0) { "Balance can't be negative" }
        dao.setBalance(id, balanceMinor, note.clean(), now())
    }

    suspend fun deleteCard(id: Long) = dao.deleteCard(id)

    /** Deletes one history entry, or fails with a message saying why it can't be. */
    suspend fun deleteEvent(eventId: Long): Result<Unit> =
        try {
            Result.success(dao.deleteEvent(eventId))
        } catch (e: DeleteEventException) {
            Result.failure(e)
        }

    private fun now() = clock.now().toEpochMilliseconds()

    private fun String?.clean() = this?.trim()?.ifEmpty { null }

    private companion object {
        const val SORT_KEY = "card_sort"
    }
}

private fun CardWithBalance.toDomain() =
    Card(
        id = id,
        nickname = nickname,
        last4 = last4,
        network = CardNetwork.entries.firstOrNull { it.name == network } ?: CardNetwork.OTHER,
        currency = Currency.of(currency),
        expiry = Expiry.fromYyyymm(expiry),
        balanceMinor = balanceMinor,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        lastActivityAt = Instant.fromEpochMilliseconds(lastActivityAt),
    )

private fun LedgerRow.toDomain() =
    CardEvent(
        id = id,
        type = EventType.entries.firstOrNull { it.name == type } ?: EventType.SET_BALANCE,
        amountMinor = amountMinor,
        deltaMinor = deltaMinor,
        balanceAfterMinor = balanceAfterMinor,
        note = note,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
    )
