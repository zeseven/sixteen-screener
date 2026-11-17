package com.sixteenscreener.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class MainTabTest {

    @Test
    fun `tabs are in bottom bar order with exchanges between search and sources`() {
        assertEquals(listOf(MainTab.WATCHLIST, MainTab.SEARCH, MainTab.EXCHANGES, MainTab.SOURCES), MainTab.entries)
    }
}
