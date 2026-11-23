package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.ImageFormat
import com.sixteenscreener.domain.model.Symbol

/**
 * Storage of cached company logos of one image format in the GCS bucket
 * (`logos/{symbol}.svg` for SVG, `eodhd-logos/{symbol}.png` and `finnhub-logos/{symbol}.png` for PNG).
 */
interface LogoStorage {
    /** Image format of the stored logos. */
    val format: ImageFormat

    /** Stored logo of [symbol] in [format], or null when none is stored. */
    suspend fun find(symbol: Symbol): ByteArray?

    /** Stores the logo [image] of [symbol] in [format], replacing a previously stored one. */
    suspend fun save(symbol: Symbol, image: ByteArray)
}
