package com.sixteenscreener.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencySymbolsTest {

    @Test
    fun `dollar currencies have the dollar sign`() {
        listOf("USD", "CAD", "AUD", "NZD", "HKD", "SGD", "TWD", "MXN", "ARS", "CLP", "COP").forEach {
            assertEquals(it, "$", CurrencySymbols.of(it))
        }
    }

    @Test
    fun `other mapped currencies have their own symbol`() {
        mapOf(
            "EUR" to "€",
            "GBP" to "£",
            "JPY" to "¥",
            "CNY" to "¥",
            "INR" to "₹",
            "KRW" to "₩",
            "ILS" to "₪",
            "TRY" to "₺",
            "RUB" to "₽",
            "THB" to "฿",
            "VND" to "₫",
            "PHP" to "₱",
            "NGN" to "₦",
            "ZAR" to "R",
        ).forEach { (currency, symbol) -> assertEquals(currency, symbol, CurrencySymbols.of(currency)) }
    }

    @Test
    fun `every symbol is a single character`() {
        listOf("USD", "EUR", "GBP", "JPY", "CNY", "INR", "KRW", "ILS", "TRY", "RUB", "THB", "VND", "PHP", "NGN", "ZAR").forEach {
            assertEquals(it, 1, CurrencySymbols.of(it)?.length)
        }
    }

    @Test
    fun `lookup ignores case and surrounding whitespace`() {
        assertEquals("$", CurrencySymbols.of("usd"))
        assertEquals("€", CurrencySymbols.of(" Eur "))
        assertEquals("£", CurrencySymbols.of("\tgbp\n"))
    }

    @Test
    fun `unknown currency has no symbol`() {
        listOf("SEK", "NOK", "CHF", "BRL", "DKK", "XYZ").forEach { assertNull(it, CurrencySymbols.of(it)) }
    }

    @Test
    fun `blank currency has no symbol`() {
        assertNull(CurrencySymbols.of(""))
        assertNull(CurrencySymbols.of("  "))
    }
}
