package com.sixteenscreener.domain.model

/**
 * Kind of data a source can provide.
 */
enum class Capability {
    /** Company search by ticker or name. */
    SEARCH,

    /** Latest stock price. */
    QUOTE,

    /** Price and financial ratios. */
    RATIOS,

    /** Dividend history. */
    DIVIDENDS,

    /** Annual balance sheet. */
    BALANCE_SHEET,

    /** Company profile (name, sector, industry, etc.). */
    COMPANY_PROFILE,

    /** Company logo. */
    LOGO,
}
