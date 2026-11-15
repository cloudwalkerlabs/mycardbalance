package app.cardbalances.data

import androidx.room.ColumnInfo
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** A prepaid card. Only the last four digits are kept; the balance lives in the ledger. */
@Entity(tableName = "card")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val last4: String,
    /** CardNetwork.name */
    val network: String,
    /** ISO 4217 code */
    val currency: String,
    /** Valid through the end of this month, as yyyymm (e.g. 202803 for 03/28). */
    val expiry: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Small key/value settings, such as how the card list is sorted. */
@Entity(tableName = "preference")
data class PreferenceEntity(@PrimaryKey val key: String, val value: String)

/**
 * One ledger entry. For SET_BALANCE, [amountMinor] is the balance itself (an "anchor"; a card's
 * first event is always one); for REDEEM it is the amount spent. Balances are derived from these
 * rows only.
 */
@Entity(
    tableName = "card_event",
    foreignKeys = [ForeignKey(entity = CardEntity::class, parentColumns = ["id"], childColumns = ["card_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("card_id", "id")],
)
data class CardEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "card_id") val cardId: Long,
    val type: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * Every event with the balance after it. Events are replayed in id order (the order they were
 * recorded): each anchor starts a new group `grp`, and within a group the balance is the anchor
 * amount minus the redemptions so far.
 */
@DatabaseView(
    viewName = "card_ledger",
    value =
        """
        SELECT id, card_id, type, amount_minor, note, created_at, balance_after_minor,
               balance_after_minor - IFNULL(LAG(balance_after_minor) OVER (PARTITION BY card_id ORDER BY id), 0)
                   AS delta_minor
        FROM (
            SELECT *,
                   SUM(CASE WHEN type = 'REDEEM' THEN -amount_minor ELSE amount_minor END)
                       OVER (PARTITION BY card_id, grp ORDER BY id) AS balance_after_minor
            FROM (
                SELECT *,
                       SUM(CASE WHEN type = 'REDEEM' THEN 0 ELSE 1 END)
                           OVER (PARTITION BY card_id ORDER BY id) AS grp
                FROM card_event
            )
        )
        """,
)
data class LedgerRow(
    val id: Long,
    @ColumnInfo(name = "card_id") val cardId: Long,
    val type: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "balance_after_minor") val balanceAfterMinor: Long,
    @ColumnInfo(name = "delta_minor") val deltaMinor: Long,
)

/** Each card with its current balance: the balance after its most recent ledger entry. */
@DatabaseView(
    viewName = "card_with_balance",
    value =
        """
        SELECT card.id, card.nickname, card.last4, card.network, card.currency, card.expiry, card.created_at,
               ledger.balance_after_minor AS balance_minor,
               ledger.created_at AS last_activity_at
        FROM card
        JOIN card_ledger AS ledger ON ledger.card_id = card.id
        WHERE ledger.id = (SELECT MAX(e.id) FROM card_event AS e WHERE e.card_id = card.id)
        """,
)
data class CardWithBalance(
    val id: Long,
    val nickname: String,
    val last4: String,
    val network: String,
    val currency: String,
    val expiry: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "balance_minor") val balanceMinor: Long,
    @ColumnInfo(name = "last_activity_at") val lastActivityAt: Long,
)

@Database(
    entities = [CardEntity::class, CardEventEntity::class, PreferenceEntity::class],
    views = [LedgerRow::class, CardWithBalance::class],
    version = 1,
)
@ConstructedBy(CardDatabaseConstructor::class)
abstract class CardDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
}

// Room generates the actual implementations.
@Suppress("KotlinNoActualForExpect")
expect object CardDatabaseConstructor : RoomDatabaseConstructor<CardDatabase> {
    override fun initialize(): CardDatabase
}

/** Applies the settings every platform shares: the bundled SQLite and an IO query context. */
fun RoomDatabase.Builder<CardDatabase>.configure(context: CoroutineContext = Dispatchers.IO): CardDatabase =
    setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(context).build()
