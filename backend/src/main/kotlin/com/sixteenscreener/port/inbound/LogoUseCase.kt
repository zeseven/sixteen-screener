package com.sixteenscreener.port.inbound

import com.sixteenscreener.domain.model.Logo
import com.sixteenscreener.domain.model.Symbol

/**
 * Company logos: from the bucket, downloaded from a logo source on the first request, or the default logo.
 */
interface LogoUseCase {
    /**
     * SVG or PNG logo of [symbol]; the default SVG logo when no source has one or the sources can't be asked.
     * Never fails for storage or source errors.
     */
    suspend fun logo(symbol: Symbol): Logo
}
