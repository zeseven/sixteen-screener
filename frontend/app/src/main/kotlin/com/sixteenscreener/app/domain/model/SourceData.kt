package com.sixteenscreener.app.domain.model

import java.time.Instant
import java.time.LocalDate

/** Freshness or failure reason of data returned for one source. */
enum class DataStatus { FRESH, CACHED, LIMIT_REACHED, SOURCE_ERROR, NOT_SUPPORTED, NO_DATA, UNKNOWN }

/**
 * Origin and freshness of company data returned for one source.
 */
data class SourceDataMeta(
    /** Source the data was taken from. */
    val source: SourceId,
    /** Freshness of the data, or why it is missing or stale. */
    val status: DataStatus,
    /** When the data was received from the source; `null` when no data is returned. */
    val fetchedAt: Instant?,
)

/** Unit of a ratio value: `PERCENT` values are percents (`15` is 15%), `CURRENCY` values are amounts per share. */
enum class RatioUnit { PERCENT, RATIO, CURRENCY, UNKNOWN }

/** Category of a ratio; indicator settings are grouped by it. */
enum class RatioCategory {
    VALUATION, PROFITABILITY, MARGINS, GROWTH, LIQUIDITY, LEVERAGE, EFFICIENCY, DIVIDENDS, RISK, PER_SHARE, UNKNOWN
}

/**
 * Financial ratio of a company.
 */
data class Ratio(
    /** Ratio code shared across sources, e.g. `PE`. */
    val code: String,
    /** Full display name without the percent suffix, e.g. `P/E`. */
    val name: String,
    /** Unit of [value]. */
    val unit: RatioUnit,
    /** Ratio value, `null` when the source provides none. */
    val value: Double?,
)

/**
 * Data of the "Price and ratio" tab.
 */
data class PriceAndRatios(
    /** Origin and freshness. */
    val meta: SourceDataMeta,
    /** Latest price, `null` when none is available. */
    val quote: Quote?,
    /** Ratios provided by the source. */
    val ratios: List<Ratio>,
    /** Indicators of the profile's settings in display order; each colors the ratio with the same code. */
    val indicators: List<Indicator> = emptyList(),
)

/** Fiscal period of a balance sheet. */
enum class FiscalPeriod { FY, Q1, Q2, Q3, Q4, UNKNOWN }

/**
 * As-reported balance sheet line item.
 */
data class BalanceSheetItem(
    /** Line item identifier as provided by the source, e.g. a US GAAP concept. */
    val concept: String,
    /** Line item label as provided by the source. */
    val label: String,
    /** Reported value. */
    val value: Double,
    /** Unit of [value], e.g. `USD`. */
    val unit: String?,
)

/**
 * Balance sheet of one fiscal period.
 */
data class BalanceSheetPeriod(
    /** Last day of the period. */
    val periodEnd: LocalDate,
    /** Fiscal year the period belongs to. */
    val fiscalYear: Int,
    /** Fiscal period within the year. */
    val fiscalPeriod: FiscalPeriod,
    /** Filing date, if known. */
    val filedAt: LocalDate?,
    /** Filing form, e.g. `10-K`. */
    val form: String?,
    /** Line items in source order. */
    val items: List<BalanceSheetItem>,
)

/**
 * Data of the "Balance sheet" tab.
 */
data class BalanceSheet(
    /** Origin and freshness. */
    val meta: SourceDataMeta,
    /** Periods sorted by period end, newest first. */
    val periods: List<BalanceSheetPeriod>,
)

/**
 * Company profile as provided by a source.
 */
data class CompanyProfile(
    /** Canonical company identifier. */
    val symbol: Symbol,
    /** Ticker. */
    val ticker: String,
    /** Short exchange name. */
    val exchange: String,
    /** Company name. */
    val name: String,
    /** Market of the exchange, `null` outside the US and EU markets. */
    val market: Market?,
    /** Security type as provided by the source, e.g. `Common Stock` or `ADR`; `null` when unknown. */
    val type: String?,
    /** Whether the security is a stock, as decided by the backend from [type]. */
    val stock: Boolean,
    /** Whether the exchange is the company's home exchange. */
    val homeExchange: Boolean,
    /** ISO 3166-1 alpha-2 country code. */
    val country: String?,
    /** ISO 4217 currency code. */
    val currency: String?,
    /** Sector as provided by the source. */
    val sector: String?,
    /** Industry as provided by the source. */
    val industry: String?,
    /** Date of the initial public offering. */
    val ipoDate: LocalDate?,
    /** Market capitalization as provided by the source. */
    val marketCapitalization: Double?,
    /** Number of shares outstanding as provided by the source. */
    val sharesOutstanding: Double?,
    /** Number of employees. */
    val employees: Long?,
    /** Company website URL. */
    val website: String?,
    /** Business description. */
    val description: String?,
)

/**
 * Data of the "Company profile" tab.
 */
data class CompanyProfileData(
    /** Origin and freshness. */
    val meta: SourceDataMeta,
    /** Profile, `null` when no data is returned. */
    val profile: CompanyProfile?,
)
