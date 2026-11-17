package com.sixteenscreener.port.outbound

import com.sixteenscreener.domain.model.IndicatorDefault

/**
 * Storage of the default indicator settings (`config/indicatorDefaults` document, ADR-072).
 */
interface IndicatorDefaultsRepository {
    /**
     * Stored defaults. When none are stored, [initial] is stored first and returned; stored defaults are never
     * overwritten. Stored entries that can't be read (unknown ratio code, missing or mistyped fields) are skipped.
     */
    suspend fun getOrCreate(initial: List<IndicatorDefault>): List<IndicatorDefault>
}
