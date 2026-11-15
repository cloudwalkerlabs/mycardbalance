package app.cardbalances

import app.cardbalances.domain.CardSort
import app.cardbalances.domain.CardSortField
import app.cardbalances.domain.Expiry
import app.cardbalances.ui.CardPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class ExpiryTest {
    @Test fun parsesMmYy() {
        assertEquals(Expiry(2028, 3), Expiry.parse("0328"))
        assertEquals("03/28", Expiry(2028, 3).display)
        assertEquals(202803, Expiry(2028, 3).yyyymm)
        assertNull(Expiry.parse("1328"))
        assertNull(Expiry.parse("0028"))
        assertNull(Expiry.parse("032"))
    }

    @Test fun validThroughEndOfMonth() {
        val exp = Expiry(2026, 11)
        assertFalse(exp.isExpired(LocalDate(2026, 11, 30)))
        assertTrue(exp.isExpired(LocalDate(2026, 12, 1)))
    }

    @Test fun sortRoundTrips() {
        val sort = CardSort(CardSortField.RECENT, ascending = false)
        assertEquals(sort, CardSort.decode(sort.encode()))
        assertEquals(CardSort.Default, CardSort.decode("nonsense"))
        assertEquals(CardSort.Default, CardSort.decode(null))
    }

    @Test fun paletteHashSpreadsSimilarNumbers() {
        assertNotEquals(CardPalette.index("4444"), CardPalette.index("8812"))
        assertEquals(CardPalette.index("1234"), CardPalette.index("1234"))
        assertTrue((0..9999).map { CardPalette.index(it.toString().padStart(4, '0')) }.toSet().size == 10)
    }
}
