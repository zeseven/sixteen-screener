package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.ImageFormat
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol

/**
 * External source of company logos in one image format (SVG from logoapi.sh, PNG from Finnhub's static logo host).
 */
interface LogoProvider {
    /** Configured ID of the source. */
    val id: SourceId

    /** Image format of the downloaded logos. */
    val format: ImageFormat

    /**
     * Downloads the logo of [symbol] in [format]. Returns [SourceResult.NoData] when the source has no logo for the
     * company (e.g. `404`, a response in another format or a response that is too large); failures are returned as
     * [SourceResult] variants, never thrown.
     */
    suspend fun logo(symbol: Symbol): SourceResult<ByteArray>
}
