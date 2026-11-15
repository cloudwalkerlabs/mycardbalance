package app.cardbalances

import androidx.room.Room
import app.cardbalances.data.CardDatabase
import app.cardbalances.data.CardRepository
import app.cardbalances.data.configure
import app.cardbalances.domain.CardIdentity
import app.cardbalances.domain.CardNetwork
import app.cardbalances.domain.Currency
import app.cardbalances.domain.CardSort
import app.cardbalances.domain.CardSortField
import app.cardbalances.domain.EventType
import app.cardbalances.domain.Expiry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Runs against the real bundled SQLite, so the window-function views are exercised for real. */
class CardRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder<CardDatabase>().configure()
    private val repo = CardRepository(db)
    private val nzd = Currency.of("NZD")
    private val exp = Expiry(2030, 1)

    @AfterTest fun close() = db.close()

    @Test fun initialBalanceIsAnUpdateEvent() = runTest {
        val id = repo.addCard("Prezzy", CardIdentity("1111", CardNetwork.VISA), nzd, exp, 100_00)
        val events = repo.observeEvents(id).first()
        assertEquals(1, events.size)
        assertEquals(EventType.SET_BALANCE, events[0].type)
        assertEquals(100_00L, events[0].deltaMinor)
        assertEquals(100_00L, events[0].balanceAfterMinor)
        assertEquals(100_00L, repo.observeCard(id).first()!!.balanceMinor)
    }

    @Test fun balanceIsLatestUpdateMinusLaterRedemptions() = runTest {
        val id = repo.addCard("Prezzy", CardIdentity("1111", CardNetwork.VISA), nzd, exp, 100_00)
        repo.redeem(id, 30_00, "Groceries").getOrThrow()
        repo.setBalance(id, 50_00, "Checked online")
        repo.redeem(id, 10_00, null).getOrThrow()
        repo.redeem(id, 2_50, null).getOrThrow()

        assertEquals(37_50L, repo.observeCard(id).first()!!.balanceMinor)
        val history = repo.observeEvents(id).first().reversed() // oldest first
        assertEquals(listOf(100_00L, 70_00L, 50_00L, 40_00L, 37_50L), history.map { it.balanceAfterMinor })
        assertEquals(listOf(100_00L, -30_00L, -20_00L, -10_00L, -2_50L), history.map { it.deltaMinor })
        assertEquals("Groceries", history[1].note)
    }

    @Test fun cardsDontLeakIntoEachOthersLedgers() = runTest {
        val a = repo.addCard("A", CardIdentity("1111", CardNetwork.OTHER), nzd, exp, 10_00)
        val b = repo.addCard("B", CardIdentity("2222", CardNetwork.OTHER), nzd, exp, 20_00)
        repo.redeem(a, 1_00, null)
        repo.redeem(b, 5_00, null)
        repo.setBalance(a, 8_00, null)
        repo.redeem(b, 1_00, null)
        assertEquals(8_00L, repo.observeCard(a).first()!!.balanceMinor)
        assertEquals(14_00L, repo.observeCard(b).first()!!.balanceMinor)
    }

    @Test fun refusesToOverspend() = runTest {
        val id = repo.addCard("Small", CardIdentity("2222", CardNetwork.OTHER), nzd, exp, 10_00)
        assertTrue(repo.redeem(id, 10_01, null).isFailure)
        assertEquals(1, repo.observeEvents(id).first().size)
        assertEquals(0L, repo.redeem(id, 10_00, null).getOrThrow())
        assertIs<IllegalStateException>(repo.redeem(999, 1, null).exceptionOrNull())
    }

    @Test fun setBalanceToSameValueRecordsNothing() = runTest {
        val id = repo.addCard("Same", CardIdentity("3333", CardNetwork.OTHER), nzd, exp, 5_00)
        repo.setBalance(id, 5_00, null)
        assertEquals(1, repo.observeEvents(id).first().size)
    }

    @Test fun deleteRemovesCardAndHistory() = runTest {
        val id = repo.addCard("Gone", CardIdentity("3333", CardNetwork.OTHER), nzd, exp, 5_00)
        repo.deleteCard(id)
        assertEquals(emptyList(), repo.observeCards().first())
        assertEquals(emptyList(), repo.observeEvents(id).first())
        assertNull(repo.observeCard(id).first())
    }

    @Test fun usedUpCardsSortLast() = runTest {
        val empty = repo.addCard("Empty", CardIdentity("4444", CardNetwork.OTHER), nzd, exp, 0)
        val full = repo.addCard("Full", CardIdentity("5555", CardNetwork.OTHER), nzd, exp, 50_00)
        assertEquals(listOf(full, empty), repo.observeCards().first().map { it.id })
    }

    @Test fun sortsByEachFieldBothWaysWithUsedUpLast() = runTest {
        val soon = repo.addCard("Soon", CardIdentity("1111", CardNetwork.OTHER), nzd, Expiry(2027, 3), 20_00)
        val late = repo.addCard("Late", CardIdentity("2222", CardNetwork.OTHER), nzd, Expiry(2029, 8), 90_00)
        val mid = repo.addCard("Mid", CardIdentity("3333", CardNetwork.OTHER), nzd, Expiry(2028, 1), 50_00)
        val empty = repo.addCard("Empty", CardIdentity("4444", CardNetwork.OTHER), nzd, Expiry(2026, 1), 0)
        repo.redeem(soon, 1_00, null) // soon becomes the most recently used

        suspend fun order(field: CardSortField, asc: Boolean) =
            repo.observeCards(CardSort(field, asc)).first().map { it.id }

        assertEquals(listOf(soon, mid, late, empty), order(CardSortField.EXPIRY, true))
        assertEquals(listOf(late, mid, soon, empty), order(CardSortField.EXPIRY, false))
        assertEquals(listOf(soon, mid, late, empty), order(CardSortField.BALANCE, true))
        assertEquals(listOf(late, mid, soon, empty), order(CardSortField.BALANCE, false))
        assertEquals(soon, order(CardSortField.RECENT, false).first())
        assertEquals(empty, order(CardSortField.RECENT, false).last())
    }

    @Test fun sortPreferenceIsSaved() = runTest {
        assertEquals(CardSort.Default, repo.observeSort().first())
        repo.setSort(CardSort(CardSortField.BALANCE, ascending = false))
        assertEquals(CardSort(CardSortField.BALANCE, false), repo.observeSort().first())
    }

    @Test fun expiryIsStoredAndEditable() = runTest {
        val id = repo.addCard("A", CardIdentity("1111", CardNetwork.OTHER), nzd, Expiry(2028, 3), 1)
        assertEquals(Expiry(2028, 3), repo.observeCard(id).first()!!.expiry)
        repo.updateDetails(id, "A", CardIdentity("1111", CardNetwork.OTHER), nzd, Expiry(2029, 12))
        assertEquals(Expiry(2029, 12), repo.observeCard(id).first()!!.expiry)
    }
}
