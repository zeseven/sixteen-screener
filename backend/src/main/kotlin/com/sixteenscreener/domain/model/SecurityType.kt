package com.sixteenscreener.domain.model

/**
 * Security types as provided by the source, e.g. `Common Stock`, `REIT`, `ADR`, `Preference`.
 */
object SecurityType {
    private val STOCK_TYPES = setOf("common stock", "reit")

    /**
     * True when [type] is a stock: `Common Stock` or `REIT`, compared case-insensitively after trimming, or unknown
     * (null or blank). Depositary receipts, preference shares, exchange-traded products and other types are not stocks.
     */
    fun isStock(type: String?): Boolean = type.isNullOrBlank() || type.trim().lowercase() in STOCK_TYPES
}
