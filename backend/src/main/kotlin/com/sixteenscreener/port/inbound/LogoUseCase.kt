package com.sixteenscreener.port.inbound

import com.sixteenscreener.domain.model.Logo
import com.sixteenscreener.domain.model.Symbol

/**
 * Company logos: from the bucket, downloaded from the logo source on the first request, or the default logo.
 */
interface LogoUseCase {
    /** SVG logo of [symbol]; the default logo when the source has none or can't be asked. Never fails for source errors. */
    suspend fun logo(symbol: Symbol): Logo
}
