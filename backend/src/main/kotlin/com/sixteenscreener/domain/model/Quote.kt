package com.sixteenscreener.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Latest stock price as received from a source.
 *
 * @property price last price
 * @property currency ISO 4217 currency code
 */
data class Quote(
    val price: Double,
    val currency: String,
)

/**
 * Price of the latest quote of an earlier market day.
 *
 * @property price latest price of that market day
 * @property marketDate market day in the exchange time zone
 */
data class PreviousPrice(
    val price: Double,
    val marketDate: LocalDate,
)

/**
 * Latest cached quote of a company for one source, carrying the previous market day's price for the trend (ADR-033).
 *
 * @property quote the latest quote
 * @property marketDate market day of [quote] in the exchange time zone
 * @property previousPrice latest price of the most recent earlier market day; null when unknown
 */
data class QuoteRecord(
    val quote: Quote,
    val marketDate: LocalDate,
    val previousPrice: PreviousPrice?,
) {
    /** Trend of [quote] against [previousPrice]. */
    val trend: PriceTrend get() = PriceTrend.of(quote.price, previousPrice?.price)

    /**
     * Record that replaces this one when [newQuote] of [newMarketDate] is written (ADR-033): a later market day moves
     * this record's price into [previousPrice]; the same or an earlier market day keeps the current [previousPrice].
     */
    fun advance(newQuote: Quote, newMarketDate: LocalDate): QuoteRecord =
        QuoteRecord(
            quote = newQuote,
            marketDate = newMarketDate,
            previousPrice = if (newMarketDate > marketDate) PreviousPrice(quote.price, marketDate) else previousPrice,
        )

    companion object {
        /** Record for [quote] of [marketDate] replacing [current] (null when no quote was stored before). */
        fun next(current: QuoteRecord?, quote: Quote, marketDate: LocalDate): QuoteRecord =
            current?.advance(quote, marketDate) ?: QuoteRecord(quote, marketDate, previousPrice = null)
    }
}

/**
 * One entry of the per-day quote history (ADR-033): the latest price of a market day.
 *
 * @property source source of the quote
 * @property symbol company symbol
 * @property marketDate market day in the exchange time zone
 * @property quote latest quote of that day
 * @property fetchedAt when the quote was received
 */
data class QuoteHistoryEntry(
    val source: SourceId,
    val symbol: Symbol,
    val marketDate: LocalDate,
    val quote: Quote,
    val fetchedAt: Instant,
)

/**
 * Price compared to the previous cached price.
 */
enum class PriceTrend {
    /** Price above the previous price. */
    UP,

    /** Price below the previous price. */
    DOWN,

    /** Price equal to the previous price, or the previous price is absent. */
    UNCHANGED,
    ;

    companion object {
        /** Trend of [price] against [previousPrice]. */
        fun of(price: Double, previousPrice: Double?): PriceTrend = when {
            previousPrice == null -> UNCHANGED
            price > previousPrice -> UP
            price < previousPrice -> DOWN
            else -> UNCHANGED
        }
    }
}
