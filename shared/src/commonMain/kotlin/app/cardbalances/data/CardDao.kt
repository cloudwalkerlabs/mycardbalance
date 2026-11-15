package app.cardbalances.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.cardbalances.domain.EventType
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM card_with_balance ORDER BY balance_minor = 0, last_activity_at DESC, id DESC")
    fun observeCards(): Flow<List<CardWithBalance>>

    @Query("SELECT * FROM card_with_balance WHERE id = :id")
    fun observeCard(id: Long): Flow<CardWithBalance?>

    @Query("SELECT balance_minor FROM card_with_balance WHERE id = :id")
    suspend fun balanceOf(id: Long): Long?

    /** The card's history, newest first, with running balances. */
    @Query("SELECT * FROM card_ledger WHERE card_id = :cardId ORDER BY id DESC")
    fun observeLedger(cardId: Long): Flow<List<LedgerRow>>

    @Query("SELECT currency FROM card ORDER BY created_at DESC, id DESC LIMIT 1")
    suspend fun mostRecentCurrency(): String?

    @Insert suspend fun insertCard(card: CardEntity): Long

    @Insert suspend fun insertEvent(event: CardEventEntity): Long

    @Query("UPDATE card SET nickname = :nickname, last4 = :last4, network = :network, currency = :currency WHERE id = :id")
    suspend fun updateDetails(id: Long, nickname: String, last4: String, network: String, currency: String)

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

    @Transaction
    suspend fun deleteCard(id: Long) {
        deleteEvents(id)
        deleteCardRow(id)
    }
}

class OverspendException(val balanceMinor: Long) : IllegalArgumentException("That's more than the balance left on the card")
