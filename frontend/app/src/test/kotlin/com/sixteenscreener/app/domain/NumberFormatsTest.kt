package com.sixteenscreener.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberFormatsTest {

    @Test
    fun `price has the currency symbol before two decimals`() {
        assertEquals("$189.84", NumberFormats.price(189.84, "USD"))
        assertEquals("\u20AC190.00", NumberFormats.price(190.0, "EUR"))
        assertEquals("$0.13", NumberFormats.price(0.125, "USD"))
    }

    @Test
    fun `price matches the currency code case-insensitively after trimming`() {
        assertEquals("\u00A31.50", NumberFormats.price(1.5, " gbp "))
    }

    @Test
    fun `price groups thousands`() {
        assertEquals("$1,234,567.89", NumberFormats.price(1_234_567.891, "USD"))
    }

    @Test
    fun `price with a currency without symbol is followed by the code`() {
        assertEquals("1,234.50 SEK", NumberFormats.price(1_234.5, "SEK"))
        assertEquals("12.00 CHF", NumberFormats.price(12.0, "CHF"))
    }

    @Test
    fun `price without currency is the number only`() {
        assertEquals("12.50", NumberFormats.price(12.5, null))
        assertEquals("12.50", NumberFormats.price(12.5, " "))
        assertEquals("12.50", NumberFormats.price(12.5, ""))
    }

    @Test
    fun `negative price has the sign before the symbol`() {
        assertEquals("-$1.00", NumberFormats.price(-1.0, "USD"))
        assertEquals("-1.00 SEK", NumberFormats.price(-1.0, "SEK"))
    }

    @Test
    fun `price rounding to zero has no sign`() {
        assertEquals("$0.00", NumberFormats.price(-0.001, "USD"))
    }

    @Test
    fun `missing price is a dash`() {
        assertEquals("-", NumberFormats.price(null, "USD"))
        assertEquals("-", NumberFormats.price(null, null))
    }

    @Test
    fun `price description has two decimals followed by the currency code`() {
        assertEquals("189.84 USD", NumberFormats.priceDescription(189.84, "USD"))
        assertEquals("1,234,567.89 EUR", NumberFormats.priceDescription(1_234_567.891, "EUR"))
        assertEquals("0.13 SEK", NumberFormats.priceDescription(0.125, "SEK"))
        assertEquals("-1.00 USD", NumberFormats.priceDescription(-1.0, "USD"))
    }

    @Test
    fun `price description without currency is the number only`() {
        assertEquals("12.50", NumberFormats.priceDescription(12.5, null))
        assertEquals("12.50", NumberFormats.priceDescription(12.5, " "))
    }

    @Test
    fun `missing price description is a dash`() {
        assertEquals("-", NumberFormats.priceDescription(null, "USD"))
    }

    @Test
    fun `abbreviation boundaries`() {
        assertEquals("999", NumberFormats.abbreviated(999.0))
        assertEquals("1 K", NumberFormats.abbreviated(1_000.0))
        assertEquals("1 M", NumberFormats.abbreviated(999_999.0))
        assertEquals("1 M", NumberFormats.abbreviated(1_000_000.0))
        assertEquals("1 B", NumberFormats.abbreviated(999_999_999.0))
        assertEquals("1 T", NumberFormats.abbreviated(1e12))
    }

    @Test
    fun `abbreviation keeps one decimal`() {
        assertEquals("352.6 B", NumberFormats.abbreviated(352_583_000_000.0))
        assertEquals("1.5 K", NumberFormats.abbreviated(1_500.0))
        assertEquals("2.5 T", NumberFormats.abbreviated(2.45e12))
    }

    @Test
    fun `values below one thousand keep one decimal`() {
        assertEquals("12.3", NumberFormats.abbreviated(12.34))
        assertEquals("1 K", NumberFormats.abbreviated(999.96))
        assertEquals("0", NumberFormats.abbreviated(0.0))
    }

    @Test
    fun `negative values are abbreviated with a sign`() {
        assertEquals("-1.5 M", NumberFormats.abbreviated(-1_500_000.0))
        assertEquals("-999", NumberFormats.abbreviated(-999.0))
        assertEquals("-1 K", NumberFormats.abbreviated(-1_000.0))
        assertEquals("0", NumberFormats.abbreviated(-0.01))
    }

    @Test
    fun `trillions beyond one thousand stay in T`() {
        assertEquals("1,500 T", NumberFormats.abbreviated(1.5e15))
    }

    @Test
    fun `missing values are dashes`() {
        assertEquals("-", NumberFormats.abbreviated(null))
        assertEquals("-", NumberFormats.decimal(null))
        assertEquals("-", NumberFormats.integer(null))
    }

    @Test
    fun `decimal keeps up to two fraction digits`() {
        assertEquals("28.5", NumberFormats.decimal(28.5))
        assertEquals("1.23", NumberFormats.decimal(1.2345))
        assertEquals("1,234", NumberFormats.decimal(1234.0))
        assertEquals("0", NumberFormats.decimal(-0.001))
        assertEquals("-0.5", NumberFormats.decimal(-0.5))
    }

    @Test
    fun `threshold shows the decimals of the step`() {
        assertEquals("25", NumberFormats.threshold(25.0, 1.0))
        assertEquals("25.0", NumberFormats.threshold(25.0, 0.5))
        assertEquals("12.5", NumberFormats.threshold(12.5, 0.5))
        assertEquals("0.30", NumberFormats.threshold(0.3, 0.05))
        assertEquals("100", NumberFormats.threshold(100.0, 5.0))
    }

    @Test
    fun `threshold keeps the precision of a value that is not a multiple of the step`() {
        assertEquals("2.25", NumberFormats.threshold(2.25, 1.0))
        assertEquals("12.37", NumberFormats.threshold(12.37, 0.5))
        assertEquals("0.1235", NumberFormats.threshold(0.123456, 0.1))
    }

    @Test
    fun `integer groups thousands`() {
        assertEquals("164,000", NumberFormats.integer(164_000))
    }
}
