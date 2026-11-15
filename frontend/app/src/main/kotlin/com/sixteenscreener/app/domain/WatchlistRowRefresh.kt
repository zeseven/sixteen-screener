package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.Symbol
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Indices of the first and last visible row within the watchlist as displayed (after filtering). */
data class VisibleRows(val first: Int, val last: Int)

/** Which watchlist rows get their market data loaded: the displayed rows plus a buffer below them (ADR-055, ADR-058). */
object WatchlistRowRefresh {
    const val BUFFER_ROWS = 5
    const val MAX_SYMBOLS_PER_REQUEST = 50
    val SETTLE_DELAY: Duration = 300.milliseconds

    /**
     * Symbols of [rows] from the first visible row to [buffer] rows below the last visible one, in display order.
     * Rows already [refreshed] since the watchlist was loaded are skipped unless [force] is set; rows [inFlight] are always skipped.
     */
    fun symbolsToRefresh(
        rows: List<Symbol>,
        visible: VisibleRows,
        refreshed: Set<Symbol>,
        inFlight: Set<Symbol>,
        force: Boolean,
        buffer: Int = BUFFER_ROWS,
    ): List<Symbol> {
        val first = visible.first.coerceAtLeast(0)
        val last = (visible.last + buffer).coerceAtMost(rows.lastIndex)
        if (first > last) return emptyList()
        return rows.subList(first, last + 1)
            .filterNot { it in inFlight || (!force && it in refreshed) }
            .distinct()
    }
}
