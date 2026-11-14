package com.sixteenscreener.domain.model

import java.time.Instant

/**
 * Type of cached company data; [T] is the type of the cached value.
 *
 * @property capability capability a source needs to provide this data
 */
sealed class DataType<T : Any>(val name: String, val capability: Capability) {
    /** Latest quote with the previous market day's price. */
    data object QUOTE : DataType<QuoteRecord>("QUOTE", Capability.QUOTE)

    /** Price ratios. */
    data object RATIOS : DataType<Ratios>("RATIOS", Capability.RATIOS)

    /** Annual balance sheet. */
    data object BALANCE_SHEET : DataType<BalanceSheet>("BALANCE_SHEET", Capability.BALANCE_SHEET)

    /** Company profile. */
    data object COMPANY_PROFILE : DataType<CompanyProfile>("COMPANY_PROFILE", Capability.COMPANY_PROFILE)

    companion object {
        /** All data types. */
        val entries: List<DataType<*>> get() = listOf(QUOTE, RATIOS, BALANCE_SHEET, COMPANY_PROFILE)
    }
}

/**
 * Identifies cached data of one type for one company and source.
 *
 * @property source source the data comes from
 * @property symbol company symbol
 * @property type data type
 */
data class CacheKey<T : Any>(
    val source: SourceId,
    val symbol: Symbol,
    val type: DataType<T>,
)

/**
 * Cached value with the time it was received from the source.
 *
 * @property data cached value
 * @property fetchedAt when the value was received from the source
 */
data class CacheEntry<out T : Any>(
    val data: T,
    val fetchedAt: Instant,
)

/**
 * Result of a bulk cache read, keyed by [CacheKey]; keys without cached data are absent.
 */
class CacheEntries(entries: Map<CacheKey<*>, CacheEntry<*>>) {
    private val entries: Map<CacheKey<*>, CacheEntry<*>> = entries.toMap()

    /** Cached entry for [key], or null when nothing is cached. */
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: CacheKey<T>): CacheEntry<T>? = entries[key] as CacheEntry<T>?

    /** Keys with cached data. */
    val keys: Set<CacheKey<*>> get() = entries.keys

    /** Number of cached entries. */
    val size: Int get() = entries.size
}
