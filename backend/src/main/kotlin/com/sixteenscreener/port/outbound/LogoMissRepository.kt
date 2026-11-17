package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import java.time.Instant

/**
 * Symbols a logo source has no logo for (`logoMisses` collection), so that source isn't asked again for 14 days.
 * Misses are recorded per logo source and symbol.
 */
interface LogoMissRepository {
    /** When a missing logo of [symbol] in [source] was last recorded, or null when no miss is recorded. */
    suspend fun lastMiss(source: SourceId, symbol: Symbol): Instant?

    /** Records that [source] had no logo for [symbol] at [checkedAt]. */
    suspend fun recordMiss(source: SourceId, symbol: Symbol, checkedAt: Instant)
}
