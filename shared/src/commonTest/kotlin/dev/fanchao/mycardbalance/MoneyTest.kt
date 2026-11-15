package dev.fanchao.mycardbalance

import dev.fanchao.mycardbalance.domain.Currency
import dev.fanchao.mycardbalance.domain.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {
    private val nzd = Currency.of("NZD")
    private val jpy = Currency.of("JPY")

    @Test fun formats() {
        assertEquals("$0.00", Money.format(0, nzd))
        assertEquals("$1,234.50", Money.format(123450, nzd))
        assertEquals("−$5.05", Money.format(-505, nzd))
        assertEquals("+$5.00", Money.format(500, nzd, showSign = true))
        assertEquals("¥12,000", Money.format(12000, jpy))
        assertEquals("1234.50", Money.formatPlain(123450, nzd))
        assertEquals("0.07", Money.formatPlain(7, nzd))
    }

    @Test fun parses() {
        assertEquals(1200L, Money.parse("12", nzd))
        assertEquals(1250L, Money.parse("12.5", nzd))
        assertEquals(1205L, Money.parse("12.05", nzd))
        assertEquals(50L, Money.parse(".5", nzd))
        assertEquals(123456L, Money.parse("$1,234.56", nzd))
        assertEquals(500L, Money.parse("500", jpy))
        assertNull(Money.parse("", nzd))
        assertNull(Money.parse("1.234", nzd))
        assertNull(Money.parse("1.2.3", nzd))
        assertNull(Money.parse("abc", nzd))
        assertNull(Money.parse("1.5", jpy))
    }
}
