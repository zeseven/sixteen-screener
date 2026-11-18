package com.sixteenscreener.app.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant

/** Market detected by exchange; companies outside both markets have no market. */
@Serializable
enum class Market { US, EU }

/** Price compared to the previous cached price. */
enum class Trend { UP, DOWN, UNCHANGED }

/**
 * Latest cached price of a company.
 */
data class Quote(
    /** Price in [currency]. */
    val price: Double,
    /** ISO 4217 currency code. */
    val currency: String,
    /** When the price was received from the source. */
    val fetchedAt: Instant,
    /** Latest cached price of the most recent earlier market day, if any. */
    val previousCachedPrice: Double?,
    /** Price compared to [previousCachedPrice]. */
    val trend: Trend,
    /** Source the price was taken from, if reported. */
    val source: SourceId?,
)

/**
 * Company identity from the exchange directory, shown by every company row.
 */
interface Company {
    /** Canonical company identifier. */
    val symbol: Symbol

    /** Ticker displayed in the row. */
    val ticker: String

    /** Short exchange name displayed under the ticker. */
    val exchange: String

    /** Company name. */
    val name: String

    /** Market of the exchange, `null` outside the US and EU markets. */
    val market: Market?

    /** Security type as provided by the source, e.g. `Common Stock` or `ADR`; `null` when unknown. */
    val type: String?

    /** Whether the security is a stock, as decided by the backend from [type]. */
    val stock: Boolean

    /** Logo endpoint URL relative to the backend URL. */
    val logoUrl: String
}

/**
 * Company identity alone, e.g. passed to the detailed screen.
 */
@Serializable
data class CompanyIdentity(
    override val symbol: Symbol,
    override val ticker: String,
    override val exchange: String,
    override val name: String,
    override val market: Market?,
    override val type: String?,
    override val stock: Boolean,
    override val logoUrl: String,
) : Company

/** Identity of this company without the data of its row. */
fun Company.identity(): CompanyIdentity = this as? CompanyIdentity ?: CompanyIdentity(symbol, ticker, exchange, name, market, type, stock, logoUrl)

/**
 * Watchlist row without market data.
 */
data class WatchlistItem(
    override val symbol: Symbol,
    override val ticker: String,
    override val exchange: String,
    override val name: String,
    override val market: Market?,
    override val type: String?,
    override val stock: Boolean,
    override val logoUrl: String,
    /** Sector as provided by the profile's default source, `null` when unknown. */
    val sector: String?,
    /** Profile tags of the company. */
    val tags: List<String>,
) : Company {
    companion object {
        /** Watchlist row of [company] before the backend returned the stored item. */
        fun of(company: Company, sector: String? = null): WatchlistItem = WatchlistItem(
            symbol = company.symbol,
            ticker = company.ticker,
            exchange = company.exchange,
            name = company.name,
            market = company.market,
            type = company.type,
            stock = company.stock,
            logoUrl = company.logoUrl,
            sector = sector,
            tags = emptyList(),
        )
    }
}
