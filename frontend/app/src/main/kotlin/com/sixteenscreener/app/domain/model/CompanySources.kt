package com.sixteenscreener.app.domain.model

/**
 * Source offered in the source dropdown of a detailed screen tab.
 */
data class SourceOption(
    /** Source identifier, sent as the `source` parameter of the tab's request. */
    val id: SourceId,
    /** Display name. */
    val name: String,
    /** Whether this is the profile's default source of the company's role. */
    val isDefault: Boolean,
    /** Whether the source may be called without a manual refresh; `false` for manual-only sources. */
    val automaticRequests: Boolean,
)

/**
 * Sources the source dropdown of each detailed screen tab offers for one company, in backend order: the profile's default source
 * first when it provides the tab's data. An empty list means no source provides the tab's data for the company.
 */
data class CompanySources(
    /** Options of the "Price and ratio" tab. */
    val priceRatios: List<SourceOption>,
    /** Options of the "Dividends" tab. */
    val dividends: List<SourceOption>,
    /** Options of the "Balance sheet" tab. */
    val balanceSheet: List<SourceOption>,
    /** Options of the "Company profile" tab. */
    val profile: List<SourceOption>,
)
