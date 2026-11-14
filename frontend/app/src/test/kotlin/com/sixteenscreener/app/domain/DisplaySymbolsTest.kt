package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.Market
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplaySymbolsTest {

    @Test
    fun `US flag is regional indicators U and S`() {
        assertEquals(listOf(0x1F1FA, 0x1F1F8), DisplaySymbols.flagOf(Market.US)?.codePoints()?.toArray()?.toList())
    }

    @Test
    fun `EU flag is regional indicators E and U`() {
        assertEquals(listOf(0x1F1EA, 0x1F1FA), DisplaySymbols.flagOf(Market.EU)?.codePoints()?.toArray()?.toList())
    }

    @Test
    fun `no flag without market`() {
        assertNull(DisplaySymbols.flagOf(null))
    }

    @Test
    fun `clock is the three o'clock face`() {
        assertEquals(listOf(0x1F552), DisplaySymbols.CLOCK.codePoints().toArray().toList())
    }
}
