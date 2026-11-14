package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol

/**
 * External source of company logos in SVG format (e.g. logoapi.sh).
 */
interface LogoProvider {
    /** Configured ID of the source. */
    val id: SourceId

    /**
     * Downloads the SVG logo of [symbol]. Returns [SourceResult.NoData] when the source has no logo for the company
     * (e.g. `404` or a response that is not SVG); failures are returned as [SourceResult] variants, never thrown.
     */
    suspend fun logo(symbol: Symbol): SourceResult<ByteArray>
}
