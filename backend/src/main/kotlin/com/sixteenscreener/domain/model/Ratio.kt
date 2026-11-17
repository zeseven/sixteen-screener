package com.sixteenscreener.domain.model

/**
 * Unit of a ratio value. Values keep the units of the source.
 */
enum class RatioUnit {
    /** Percent: `15` is 15%. */
    PERCENT,

    /** Plain ratio or multiple. */
    RATIO,

    /** Amount per share in the currency of the company. */
    CURRENCY,
}

/**
 * Category of a ratio, used to group ratios on the indicator settings page.
 */
enum class RatioCategory {
    VALUATION,
    PROFITABILITY,
    MARGINS,
    GROWTH,
    LIQUIDITY,
    LEVERAGE,
    EFFICIENCY,
    DIVIDENDS,
    RISK,
    PER_SHARE,
}

/**
 * Ratio codes shared across sources, in display and settings order (ADR-074).
 *
 * @property displayName full name shown in the app, without a unit suffix
 * @property shortName name of at most [MAX_SHORT_NAME_LENGTH] characters shown on the company row
 * @property unit unit of the values
 * @property category category of the ratio
 */
enum class RatioCode(
    val displayName: String,
    val shortName: String,
    val unit: RatioUnit,
    val category: RatioCategory,
) {
    PE("P/E", "P/E", RatioUnit.RATIO, RatioCategory.VALUATION),
    PB("P/B", "P/B", RatioUnit.RATIO, RatioCategory.VALUATION),
    PS("P/S", "P/S", RatioUnit.RATIO, RatioCategory.VALUATION),
    EV_EBITDA("EV/EBITDA", "EV/EB", RatioUnit.RATIO, RatioCategory.VALUATION),
    EV_REVENUE("EV/Revenue", "EV/R", RatioUnit.RATIO, RatioCategory.VALUATION),
    PCF("P/CF", "P/CF", RatioUnit.RATIO, RatioCategory.VALUATION),
    PFCF("P/FCF", "P/FCF", RatioUnit.RATIO, RatioCategory.VALUATION),
    PEG("PEG", "PEG", RatioUnit.RATIO, RatioCategory.VALUATION),
    ROE("ROE", "ROE", RatioUnit.PERCENT, RatioCategory.PROFITABILITY),
    ROA("ROA", "ROA", RatioUnit.PERCENT, RatioCategory.PROFITABILITY),
    ROI("ROI", "ROI", RatioUnit.PERCENT, RatioCategory.PROFITABILITY),
    GROSS_MARGIN("Gross margin", "GrM", RatioUnit.PERCENT, RatioCategory.MARGINS),
    OPERATING_MARGIN("Operating margin", "OpM", RatioUnit.PERCENT, RatioCategory.MARGINS),
    PRETAX_MARGIN("Pretax margin", "PtM", RatioUnit.PERCENT, RatioCategory.MARGINS),
    NET_MARGIN("Net margin", "NetM", RatioUnit.PERCENT, RatioCategory.MARGINS),
    REVENUE_GROWTH("Revenue growth YoY", "RevG", RatioUnit.PERCENT, RatioCategory.GROWTH),
    REVENUE_GROWTH_5Y("Revenue growth 5Y", "RevG5", RatioUnit.PERCENT, RatioCategory.GROWTH),
    EPS_GROWTH("EPS growth YoY", "EPSG", RatioUnit.PERCENT, RatioCategory.GROWTH),
    EPS_GROWTH_5Y("EPS growth 5Y", "EPSG5", RatioUnit.PERCENT, RatioCategory.GROWTH),
    DIVIDEND_GROWTH_5Y("Dividend growth 5Y", "DivG5", RatioUnit.PERCENT, RatioCategory.GROWTH),
    CURRENT_RATIO("Current ratio", "CurR", RatioUnit.RATIO, RatioCategory.LIQUIDITY),
    QUICK_RATIO("Quick ratio", "QckR", RatioUnit.RATIO, RatioCategory.LIQUIDITY),
    DEBT_TO_EQUITY("Debt/Equity", "D/E", RatioUnit.RATIO, RatioCategory.LEVERAGE),
    LT_DEBT_TO_EQUITY("LT debt/Equity", "LTD/E", RatioUnit.RATIO, RatioCategory.LEVERAGE),
    INTEREST_COVERAGE("Interest coverage", "IntC", RatioUnit.RATIO, RatioCategory.LEVERAGE),
    ASSET_TURNOVER("Asset turnover", "AstT", RatioUnit.RATIO, RatioCategory.EFFICIENCY),
    INVENTORY_TURNOVER("Inventory turnover", "InvT", RatioUnit.RATIO, RatioCategory.EFFICIENCY),
    RECEIVABLES_TURNOVER("Receivables turnover", "RecT", RatioUnit.RATIO, RatioCategory.EFFICIENCY),
    DIVIDEND_YIELD("Dividend yield", "DivY", RatioUnit.PERCENT, RatioCategory.DIVIDENDS),
    PAYOUT_RATIO("Payout ratio", "Payout", RatioUnit.PERCENT, RatioCategory.DIVIDENDS),
    BETA("Beta", "Beta", RatioUnit.RATIO, RatioCategory.RISK),
    EPS("EPS", "EPS", RatioUnit.CURRENCY, RatioCategory.PER_SHARE),
    BOOK_VALUE_PER_SHARE("Book value per share", "BVPS", RatioUnit.CURRENCY, RatioCategory.PER_SHARE),
    ;

    /** Whether the ratio can be an indicator; per share amounts can't. */
    val isIndicator: Boolean get() = category != RatioCategory.PER_SHARE

    companion object {
        /** Maximum length of [shortName]. */
        const val MAX_SHORT_NAME_LENGTH = 6

        /** Ratios that can be indicators, in settings order. */
        val indicators: List<RatioCode> = entries.filter { it.isIndicator }

        /** Code named [raw], or null when there is none. */
        fun parseOrNull(raw: String): RatioCode? = entries.firstOrNull { it.name == raw }
    }
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

    /** Unit of [value]. */
    val unit: RatioUnit get() = code.unit
}

/**
 * Ratios of a company from one source.
 *
 * @property items ratios in display order
 */
data class Ratios(
    val items: List<Ratio>,
) {
    /** Value of [code], or null when it is absent. */
    fun valueOf(code: RatioCode): Double? = items.firstOrNull { it.code == code }?.value

    companion object {
        /** Ratios with one item per [RatioCode] in display order, taking the values from [values]. */
        fun of(values: Map<RatioCode, Double?>): Ratios = Ratios(RatioCode.entries.map { Ratio(it, values[it]) })
    }
}
