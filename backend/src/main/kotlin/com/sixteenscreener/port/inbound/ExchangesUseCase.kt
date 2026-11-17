package com.sixteenscreener.port.inbound

import com.sixteenscreener.domain.model.ExchangeEntry

/**
 * Exchanges screen: the static exchange table (ADR-070).
 */
interface ExchangesUseCase {
    /**
     * One row per known symbol suffix and one row per US exchange with an empty suffix, sorted by suffix, then MIC.
     * Exchanges that are neither US nor the target of a suffix are not listed. Makes no source requests.
     */
    fun exchanges(): List<ExchangeEntry>
}
