package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.Symbol
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchlistRowRefreshTest {

    private val rows = (0 until 20).map { Symbol("S$it") }

    private fun symbols(range: IntRange) = range.map { Symbol("S$it") }

    private fun select(
        rows: List<Symbol> = this.rows,
        visible: VisibleRows = VisibleRows(0, 4),
        refreshed: Set<Symbol> = emptySet(),
        inFlight: Set<Symbol> = emptySet(),
        force: Boolean = false,
    ) = WatchlistRowRefresh.symbolsToRefresh(rows, visible, refreshed, inFlight, force)

    @Test
    fun `visible rows and five rows below them are selected`() {
        assertEquals(symbols(0..9), select(visible = VisibleRows(0, 4)))
    }

    @Test
    fun `empty list selects nothing`() {
        assertEquals(emptyList<Symbol>(), select(rows = emptyList()))
    }

    @Test
    fun `fewer rows than visible plus buffer selects all rows`() {
        assertEquals(symbols(0..2), select(rows = symbols(0..2), visible = VisibleRows(0, 7)))
    }

    @Test
    fun `buffer is clipped at the end of the list`() {
        assertEquals(symbols(15..19), select(visible = VisibleRows(15, 18)))
    }

    @Test
    fun `visible range beyond the list selects nothing`() {
        assertEquals(emptyList<Symbol>(), select(rows = symbols(0..2), visible = VisibleRows(5, 9)))
    }

    @Test
    fun `scrolling selects only rows not refreshed yet`() {
        assertEquals(symbols(10..12), select(visible = VisibleRows(3, 7), refreshed = symbols(0..9).toSet()))
    }

    @Test
    fun `rows in flight are never selected again`() {
        assertEquals(symbols(5..9), select(inFlight = symbols(0..4).toSet(), force = true))
    }

    @Test
    fun `force selects refreshed rows again`() {
        assertEquals(symbols(0..9), select(refreshed = symbols(0..9).toSet(), force = true))
    }

    @Test
    fun `negative first index is treated as the top of the list`() {
        assertEquals(symbols(0..6), select(visible = VisibleRows(-3, 1)))
    }

    @Test
    fun `duplicate symbols are selected once`() {
        assertEquals(listOf(Symbol("A"), Symbol("B")), select(rows = listOf(Symbol("A"), Symbol("B"), Symbol("A"))))
    }

    @Test
    fun `custom buffer is applied`() {
        assertEquals(symbols(2..5), WatchlistRowRefresh.symbolsToRefresh(rows, VisibleRows(2, 4), emptySet(), emptySet(), false, buffer = 1))
    }
}
