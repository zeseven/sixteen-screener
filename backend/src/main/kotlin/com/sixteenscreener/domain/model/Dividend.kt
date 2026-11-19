package com.sixteenscreener.domain.model

import java.time.LocalDate

/**
 * One dividend as provided by a source.
 *
 * @property exDate ex-dividend date
 * @property paymentDate payment date; null when the source doesn't provide it
 * @property recordDate record date; null when the source doesn't provide it
 * @property declarationDate declaration date; null when the source doesn't provide it
 * @property amount dividend per share in [currency]
 * @property currency ISO 4217 currency code
 */
data class Dividend(
    val exDate: LocalDate,
    val paymentDate: LocalDate?,
    val recordDate: LocalDate?,
    val declarationDate: LocalDate?,
    val amount: Double,
    val currency: String,
)

/**
 * Dividends of a company from one source, sorted by ex-date, newest first; empty when the company paid none in the
 * period the source provides.
 *
 * @property items the dividends
 */
data class Dividends(val items: List<Dividend>) {

    /**
     * Trailing twelve months dividend yield in percent: the dividends with an ex-date within the year up to [asOf],
     * divided by the price of [quote]. Null when the price is not positive or one of these dividends is paid in
     * another currency than the quote.
     */
    fun trailingYield(quote: Quote, asOf: LocalDate): Double? {
        if (quote.price <= 0) return null
        val trailing = items.filter { it.exDate > asOf.minusYears(1) && it.exDate <= asOf }
        if (trailing.any { !it.currency.equals(quote.currency, ignoreCase = true) }) return null
        return trailing.sumOf { it.amount } / quote.price * PERCENT
    }

    private companion object {
        const val PERCENT = 100.0
    }
}

/**
 * Dividends of a company from one source (detailed screen, "Dividends" tab).
 *
 * @property dividends dividends with their status
 * @property dividendYield trailing twelve months dividend yield in percent, from the latest cached quote of the same
 * source; null without such a quote or dividends
 */
data class DividendsData(
    val dividends: SourceData<Dividends>,
    val dividendYield: Double?,
)
