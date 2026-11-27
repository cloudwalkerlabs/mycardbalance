package dev.fanchao.mycardbalance

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Files
import dev.fanchao.mycardbalance.data.CardDatabase
import dev.fanchao.mycardbalance.data.CardRepository
import dev.fanchao.mycardbalance.data.configure
import dev.fanchao.mycardbalance.domain.CardIdentity
import dev.fanchao.mycardbalance.domain.CardNetwork
import dev.fanchao.mycardbalance.domain.Currency
import dev.fanchao.mycardbalance.domain.CardSort
import dev.fanchao.mycardbalance.domain.CardSortField
import dev.fanchao.mycardbalance.domain.EventType
import dev.fanchao.mycardbalance.domain.Expiry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
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

    @Test fun migratesExistingCardsAndHistoryBeforeRecordingRefunds() = runTest {
        val path = Files.createTempFile("cardbalance-v1", ".db")
        try {
            BundledSQLiteDriver().open(path.toString()).use { connection ->
                val schema = checkNotNull(javaClass.getResource("/schema-v1.sql")).readText()
                schema.split(';').filter { it.isNotBlank() }.forEach { sql ->
                    connection.prepare(sql).use { it.step() }
                }
                listOf(
                    "INSERT INTO card VALUES (1, 'Existing', '1111', 'VISA', 'NZD', 203001, 1)",
                    "INSERT INTO card_event VALUES (1, 1, 'SET_BALANCE', 10000, NULL, 1)",
                    "INSERT INTO card_event VALUES (2, 1, 'REDEEM', 3000, 'Groceries', 2)",
                    "INSERT INTO card_event VALUES (3, 1, 'SET_BALANCE', 5000, 'Checked', 3)",
                    "INSERT INTO card_event VALUES (4, 1, 'REDEEM', 1000, NULL, 4)",
                    "INSERT INTO preference VALUES ('card_sort', 'BALANCE:DESC')",
                ).forEach { sql -> connection.prepare(sql).use { it.step() } }
            }
            val migrated = Room.databaseBuilder<CardDatabase>(path.toString()).configure()
            try {
                val repository = CardRepository(migrated)
                assertEquals(CardSort(CardSortField.BALANCE, false), repository.observeSort().first())
                assertEquals(40_00L, repository.observeCard(1).first()!!.balanceMinor)
                assertEquals(listOf(40_00L, 50_00L, 70_00L, 100_00L), repository.observeEvents(1).first().map { it.balanceAfterMinor })
                assertEquals("Groceries", repository.observeEvents(1).first().first { it.id == 2L }.note)
                assertEquals(55_00L, repository.refund(1, 15_00, "Returned item").getOrThrow())
                assertEquals(55_00L, repository.observeCard(1).first()!!.balanceMinor)
            } finally {
                migrated.close()
            }
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(path.resolveSibling("${path.fileName}-wal"))
            Files.deleteIfExists(path.resolveSibling("${path.fileName}-shm"))
        }
    }

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

    @Test fun refundsAddToTheLedgerBetweenPurchasesAndBalanceUpdates() = runTest {
        val id = repo.addCard("Prezzy", CardIdentity("1111", CardNetwork.VISA), nzd, exp, 100_00)
        repo.redeem(id, 30_00, null).getOrThrow()
        assertEquals(85_00L, repo.refund(id, 15_00, "  Returned item  ").getOrThrow())
        repo.redeem(id, 5_00, null).getOrThrow()
        repo.setBalance(id, 60_00, null)
        repo.refund(id, 10_00, "  ").getOrThrow()
        repo.redeem(id, 2_50, null).getOrThrow()

        val history = repo.observeEvents(id).first().reversed()
        assertEquals(listOf(100_00L, 70_00L, 85_00L, 80_00L, 60_00L, 70_00L, 67_50L), history.map { it.balanceAfterMinor })
        assertEquals(listOf(100_00L, -30_00L, 15_00L, -5_00L, -20_00L, 10_00L, -2_50L), history.map { it.deltaMinor })
        assertEquals(EventType.REFUND, history[2].type)
        assertEquals(15_00L, history[2].amountMinor)
        assertEquals("Returned item", history[2].note)
        assertNull(history[5].note)
        assertEquals(67_50L, repo.observeCard(id).first()!!.balanceMinor)
    }

    @Test fun refundWorksOnEmptyCardsWithoutAPriorPurchase() = runTest {
        val id = repo.addCard("Empty", CardIdentity("1111", CardNetwork.OTHER), nzd, exp, 0)
        val other = repo.addCard("Other", CardIdentity("2222", CardNetwork.OTHER), nzd, exp, 10_00)
        assertEquals(20_00L, repo.refund(id, 20_00, null).getOrThrow())
        assertEquals(20_00L, repo.observeCard(id).first()!!.balanceMinor)
        assertEquals(10_00L, repo.observeCard(other).first()!!.balanceMinor)
    }

    @Test fun rejectsInvalidRefundsWithoutRecordingAnEntry() = runTest {
        val id = repo.addCard("A", CardIdentity("1111", CardNetwork.OTHER), nzd, exp, 1)
        assertFailsWith<IllegalArgumentException> { repo.refund(id, 0, null) }
        assertFailsWith<IllegalArgumentException> { repo.refund(id, -1, null) }
        assertTrue(repo.refund(id, Long.MAX_VALUE, null).isFailure)
        assertIs<IllegalStateException>(repo.refund(999, 1, null).exceptionOrNull())
        assertEquals(1, repo.observeEvents(id).first().size)
    }

    @Test fun deletingARefundRecalculatesLaterBalances() = runTest {
        val id = repo.addCard("A", CardIdentity("1111", CardNetwork.OTHER), nzd, exp, 100_00)
        repo.refund(id, 20_00, null).getOrThrow()
        repo.redeem(id, 30_00, null).getOrThrow()
        val refund = repo.observeEvents(id).first().first { it.type == EventType.REFUND }
        repo.deleteEvent(refund.id).getOrThrow()
        assertEquals(listOf(70_00L, 100_00L), repo.observeEvents(id).first().map { it.balanceAfterMinor })
    }

    @Test fun deletingARefundThatFundedAPurchaseIsRolledBack() = runTest {
        val id = repo.addCard("A", CardIdentity("1111", CardNetwork.OTHER), nzd, exp, 0)
        repo.refund(id, 20_00, null).getOrThrow()
        repo.redeem(id, 15_00, null).getOrThrow()
        repo.setBalance(id, 10_00, null) // even a later anchor must not hide a negative historical balance
        val refund = repo.observeEvents(id).first().first { it.type == EventType.REFUND }
        assertTrue(repo.deleteEvent(refund.id).isFailure)
        assertEquals(4, repo.observeEvents(id).first().size)
        assertEquals(10_00L, repo.observeCard(id).first()!!.balanceMinor)
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

    @Test fun deletingAnEntryRecalculatesLaterBalances() = runTest {
        val id = repo.addCard("Prezzy", CardIdentity("1111", CardNetwork.VISA), nzd, exp, 100_00)
        repo.redeem(id, 30_00, "Groceries")
        repo.redeem(id, 10_00, "Coffee")
        val groceries = repo.observeEvents(id).first().first { it.note == "Groceries" }

        assertTrue(repo.deleteEvent(groceries.id).isSuccess)
        assertEquals(90_00L, repo.observeCard(id).first()!!.balanceMinor)
        assertEquals(listOf(90_00L, 100_00L), repo.observeEvents(id).first().map { it.balanceAfterMinor })
    }

    @Test fun deletingALaterUpdateFallsBackToTheEarlierOne() = runTest {
        val id = repo.addCard("Prezzy", CardIdentity("1111", CardNetwork.VISA), nzd, exp, 100_00)
        repo.setBalance(id, 60_00, null)
        repo.redeem(id, 5_00, null)
        val update = repo.observeEvents(id).first().first { it.type == EventType.SET_BALANCE && it.balanceAfterMinor == 60_00L }
        repo.deleteEvent(update.id).getOrThrow()
        assertEquals(95_00L, repo.observeCard(id).first()!!.balanceMinor)
    }

    @Test fun startingBalanceCantBeDeleted() = runTest {
        val id = repo.addCard("Prezzy", CardIdentity("1111", CardNetwork.VISA), nzd, exp, 100_00)
        val start = repo.observeEvents(id).first().single()
        assertTrue(repo.deleteEvent(start.id).isFailure)
        assertEquals(100_00L, repo.observeCard(id).first()!!.balanceMinor)
    }

    @Test fun deletionThatWouldGoNegativeIsRolledBack() = runTest {
        val id = repo.addCard("Small", CardIdentity("1111", CardNetwork.OTHER), nzd, exp, 10_00)
        repo.redeem(id, 10_00, null)
        repo.setBalance(id, 50_00, "Top up")
        repo.redeem(id, 40_00, null)
        val topUp = repo.observeEvents(id).first().first { it.note == "Top up" }
        assertTrue(repo.deleteEvent(topUp.id).isFailure) // would leave 10 - 10 - 40 = -40
        assertEquals(4, repo.observeEvents(id).first().size)
        assertEquals(10_00L, repo.observeCard(id).first()!!.balanceMinor)
    }
}
