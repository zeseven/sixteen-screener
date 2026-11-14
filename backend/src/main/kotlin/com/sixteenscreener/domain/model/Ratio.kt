package com.sixteenscreener.domain.model

/**
 * Ratio codes shared across sources.
 *
 * @property displayName name shown in the app
 */
enum class RatioCode(val displayName: String) {
    PE("P/E"),
    PB("P/B"),
    PS("P/S"),
    EV_EBITDA("EV/EBITDA"),
    ROE("ROE"),
    ROA("ROA"),
    NET_MARGIN("Net margin"),
    DEBT_TO_EQUITY("Debt/Equity"),
    CURRENT_RATIO("Current ratio"),
    DIVIDEND_YIELD("Dividend yield"),
    EPS("EPS"),
    BETA("Beta"),
}

/**
 * Value of one ratio as provided by a source.
 *
 * @property code ratio code
 * @property value ratio value; null when the source doesn't provide it
 */
data class Ratio(
    val code: RatioCode,
    val value: Double?,
) {
    /** Display name of the ratio. */
    val name: String get() = code.displayName
}

/**
 * Ratios of a company from one source.
 *
 * @property items ratios in display order
 */
data class Ratios(
    val items: List<Ratio>,
)
