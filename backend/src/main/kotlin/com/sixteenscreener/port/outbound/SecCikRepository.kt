package com.sixteenscreener.port.outbound

import java.time.Instant

/**
 * Ticker to SEC Central Index Key mapping (`secCik` collection), so the SEC ticker table is not downloaded on every cold start.
 *
 * Tickers are given in the SEC form (`BRK-B` for the share class `BRK.B`). Records expire 14 days after they are stored.
 */
interface SecCikRepository {
    /** CIK stored for [ticker], or null when none is stored or the record expired at [now]. */
    suspend fun find(ticker: String, now: Instant): Long?

    /** Stores [cik] for [ticker] at [storedAt], replacing a previously stored one. */
    suspend fun save(ticker: String, cik: Long, storedAt: Instant)
}
