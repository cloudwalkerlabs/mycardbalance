package dev.fanchao.mycardbalance

import dev.fanchao.mycardbalance.domain.CardIdentity
import dev.fanchao.mycardbalance.domain.CardNetwork
import dev.fanchao.mycardbalance.domain.CardNumber
import dev.fanchao.mycardbalance.domain.CardNumberResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CardNumberTest {
    @Test fun fullVisaNumberKeepsOnlyLastFour() {
        assertEquals(
            CardNumberResult.Valid(CardIdentity("1111", CardNetwork.VISA)),
            CardNumber.parse("4111 1111 1111 1111"),
        )
    }

    @Test fun detectsMastercardIncluding2Series() {
        assertEquals(CardNetwork.MASTERCARD, (CardNumber.parse("5555555555554444") as CardNumberResult.Valid).identity.network)
        assertEquals(CardNetwork.MASTERCARD, (CardNumber.parse("2223003122003222") as CardNumberResult.Valid).identity.network)
    }

    @Test fun lastFourOnlyKeepsFallbackNetwork() {
        assertEquals(
            CardNumberResult.Valid(CardIdentity("4242", CardNetwork.VISA)),
            CardNumber.parse("4242", fallbackNetwork = CardNetwork.VISA),
        )
    }

    @Test fun rejectsBadInput() {
        assertIs<CardNumberResult.Invalid>(CardNumber.parse("4111 1111 1111 1112")) // Luhn
        assertIs<CardNumberResult.Invalid>(CardNumber.parse("123"))
        assertIs<CardNumberResult.Invalid>(CardNumber.parse("12345678"))
        assertIs<CardNumberResult.Invalid>(CardNumber.parse(""))
    }
}
