package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.Symbol
import java.time.Instant

/**
 * Symbols the logo source has no logo for (`logoMisses` collection), so the source isn't asked again for 14 days.
 */
interface LogoMissRepository {
    /** When a missing logo of [symbol] was last recorded, or null when no miss is recorded. */
    suspend fun lastMiss(symbol: Symbol): Instant?

    /** Records that the logo source had no logo for [symbol] at [checkedAt]. */
    suspend fun recordMiss(symbol: Symbol, checkedAt: Instant)
}
