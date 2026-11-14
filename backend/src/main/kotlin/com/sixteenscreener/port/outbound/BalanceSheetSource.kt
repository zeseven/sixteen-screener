package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol

/**
 * Source of annual balance sheets (e.g. Finnhub reported financials, SEC EDGAR company facts).
 *
 * The caller reserves one call per request against the request limits of the source. An implementation that needs a further
 * call for a lookup (the SEC ticker table download) reserves it itself; the returned [SourceResult.outcome] is the outcome of
 * the last call made. Source failures are returned as [SourceResult] variants, never thrown.
 */
interface BalanceSheetSource {
    /** Configured ID of the source. */
    val id: SourceId

    /** Annual balance sheet periods of [symbol], newest first; quarterly filings are not returned. */
    suspend fun balanceSheet(symbol: Symbol): SourceResult<BalanceSheet>
}
