package app.cardbalances.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.cardbalances.domain.EventType
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    /**
     * [sort] is a CardSortField name. Used-up cards always sort last; each CASE is NULL for every
     * row unless its sort is the selected one, so only that one orders the list.
     */
    @Query(
        """
        SELECT * FROM card_with_balance
        ORDER BY balance_minor = 0,
            CASE WHEN :sort = 'EXPIRY' AND :ascending THEN expiry END ASC,
            CASE WHEN :sort = 'EXPIRY' AND NOT :ascending THEN expiry END DESC,
            CASE WHEN :sort = 'RECENT' AND :ascending THEN last_activity_at END ASC,
            CASE WHEN :sort = 'RECENT' AND NOT :ascending THEN last_activity_at END DESC,
            CASE WHEN :sort = 'BALANCE' AND :ascending THEN balance_minor END ASC,
            CASE WHEN :sort = 'BALANCE' AND NOT :ascending THEN balance_minor END DESC,
            id DESC
        """
    )
    fun observeCards(sort: String, ascending: Boolean): Flow<List<CardWithBalance>>

    @Query("SELECT * FROM card_with_balance WHERE id = :id")
    fun observeCard(id: Long): Flow<CardWithBalance?>

    @Query("SELECT balance_minor FROM card_with_balance WHERE id = :id")
    suspend fun balanceOf(id: Long): Long?

    /** The card's history, newest first, with running balances. */
    @Query("SELECT * FROM card_ledger WHERE card_id = :cardId ORDER BY id DESC")
    fun observeLedger(cardId: Long): Flow<List<LedgerRow>>

    @Insert suspend fun insertCard(card: CardEntity): Long

    @Insert suspend fun insertEvent(event: CardEventEntity): Long

    @Query(
        "UPDATE card SET nickname = :nickname, last4 = :last4, network = :network, currency = :currency, expiry = :expiry WHERE id = :id"
    )
    suspend fun updateDetails(id: Long, nickname: String, last4: String, network: String, currency: String, expiry: Int)

    @Query("SELECT value FROM preference WHERE `key` = :key")
    fun observePreference(key: String): Flow<String?>

    @Upsert suspend fun putPreference(preference: PreferenceEntity)

    @Query("DELETE FROM card_event WHERE card_id = :id") suspend fun deleteEvents(id: Long)

    @Query("DELETE FROM card WHERE id = :id") suspend fun deleteCardRow(id: Long)

    /** Inserts the card and its initial balance, which is an ordinary SET_BALANCE event. */
    @Transaction
    suspend fun addCard(card: CardEntity, initialBalanceMinor: Long): Long {
        val id = insertCard(card)
        insertEvent(CardEventEntity(cardId = id, type = EventType.SET_BALANCE.name, amountMinor = initialBalanceMinor, note = null, createdAt = card.createdAt))
        return id
    }

    /**
     * Records a purchase, checking the balance in the same transaction. Returns the balance left,
     * null if the card doesn't exist, or throws [OverspendException].
     */
    @Transaction
    suspend fun redeem(cardId: Long, amountMinor: Long, note: String?, now: Long): Long? {
        val balance = balanceOf(cardId) ?: return null
        if (amountMinor > balance) throw OverspendException(balance)
        insertEvent(CardEventEntity(cardId = cardId, type = EventType.REDEEM.name, amountMinor = amountMinor, note = note, createdAt = now))
        return balance - amountMinor
    }

    /** Anchors the balance to a known figure. Skipped when it already matches. */
    @Transaction
    suspend fun setBalance(cardId: Long, balanceMinor: Long, note: String?, now: Long) {
        val balance = balanceOf(cardId) ?: return
        if (balance != balanceMinor) {
            insertEvent(CardEventEntity(cardId = cardId, type = EventType.SET_BALANCE.name, amountMinor = balanceMinor, note = note, createdAt = now))
        }
    }

    @Query("SELECT card_id FROM card_event WHERE id = :eventId") suspend fun cardOfEvent(eventId: Long): Long?

    @Query("SELECT MIN(id) FROM card_event WHERE card_id = :cardId") suspend fun firstEventId(cardId: Long): Long?

    @Query("DELETE FROM card_event WHERE id = :eventId") suspend fun deleteEventRow(eventId: Long)

    /** Lowest running balance anywhere in the card's history, per the card_ledger view. */
    @Query("SELECT IFNULL(MIN(balance_after_minor), 0) FROM card_ledger WHERE card_id = :cardId")
    suspend fun lowestBalance(cardId: Long): Long

    /**
     * Removes one history entry; balances after it recalculate on their own. The card's first
     * entry (its starting balance) can't be removed, and a removal that would take any running
     * balance below zero is rolled back by throwing inside the transaction.
     */
    @Transaction
    suspend fun deleteEvent(eventId: Long) {
        val cardId = cardOfEvent(eventId) ?: return
        if (firstEventId(cardId) == eventId) throw DeleteEventException("The starting balance can't be deleted. Use Set balance to change it.")
        deleteEventRow(eventId)
        if (lowestBalance(cardId) < 0) throw DeleteEventException("Deleting this would take the balance below zero.")
    }

    @Transaction
    suspend fun deleteCard(id: Long) {
        deleteEvents(id)
        deleteCardRow(id)
    }
}

class OverspendException(val balanceMinor: Long) : IllegalArgumentException("That's more than the balance left on the card")

class DeleteEventException(message: String) : IllegalStateException(message)
