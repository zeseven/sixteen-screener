package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol

/**
 * Source of annual balance sheets (e.g. Finnhub reported financials, SEC EDGAR company facts).
 *
 * Makes at most one billable call per request (lookups cached by the implementation, such as the SEC ticker-to-CIK table,
 * don't count); request limits are checked by the caller. Source failures are returned as [SourceResult] variants, never thrown.
 */
interface BalanceSheetSource {
    /** Configured ID of the source. */
    val id: SourceId

    /** Annual balance sheet periods of [symbol], newest first; quarterly filings are not returned. */
    suspend fun balanceSheet(symbol: Symbol): SourceResult<BalanceSheet>
}
