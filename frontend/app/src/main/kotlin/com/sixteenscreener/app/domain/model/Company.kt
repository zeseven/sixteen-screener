package com.sixteenscreener.app.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant

/** Market detected by exchange; companies outside both markets have no market. */
@Serializable
enum class Market { US, EU }

/** Price compared to the previous cached price. */
@Serializable
enum class Trend { UP, DOWN, UNCHANGED }

/**
 * Latest cached price of a company.
 */
@Serializable
data class Quote(
    /** Price in [currency]. */
    val price: Double,
    /** ISO 4217 currency code. */
    val currency: String,
    /** When the price was received from the source. */
    @Serializable(with = InstantSerializer::class) val fetchedAt: Instant,
    /** Latest cached price of the most recent earlier market day, if any. */
    val previousCachedPrice: Double?,
    /** Price compared to [previousCachedPrice]. */
    val trend: Trend,
    /** Source the price was taken from, if reported. */
    val source: SourceId?,
)

/**
 * Data for a company row on the watchlist and search screens.
 */
@Serializable
data class CompanySummary(
    /** Canonical company identifier. */
    val symbol: Symbol,
    /** Ticker displayed in the row. */
    val ticker: String,
    /** Short exchange name displayed under the ticker. */
    val exchange: String,
    /** Company name. */
    val name: String,
    /** Market of the exchange, `null` outside the US and EU markets. */
    val market: Market?,
    /** Sector as provided by the source. */
    val sector: String?,
    /** Logo endpoint URL relative to the backend URL. */
    val logoUrl: String,
    /** Cached quote, `null` when none is available. */
    val quote: Quote?,
    /** Whether the company is in the profile's watchlist (set in search results). */
    val inWatchlist: Boolean,
)
