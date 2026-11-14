package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.Symbol

/**
 * Storage of cached company logos (`logos/{symbol}.svg` in the GCS bucket).
 */
interface LogoStorage {
    /** Stored SVG logo of [symbol], or null when none is stored. */
    suspend fun find(symbol: Symbol): ByteArray?

    /** Stores the SVG logo [svg] of [symbol], replacing a previously stored one. */
    suspend fun save(symbol: Symbol, svg: ByteArray)
}
