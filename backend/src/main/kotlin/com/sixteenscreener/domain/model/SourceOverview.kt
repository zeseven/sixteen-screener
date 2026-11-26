package com.sixteenscreener.domain.model

/**
 * Configured source as described to the domain.
 *
 * @property id source ID
 * @property name display name
 * @property capabilities data the source provides
 * @property roles roles the source is configured for (ADR-083)
 * @property automaticRequests whether the source may be called without a manual request (ADR-083)
 */
data class SourceDescription(
    val id: SourceId,
    val name: String,
    val capabilities: Set<Capability>,
    val roles: Set<SourceRole>,
    val automaticRequests: Boolean,
)

/**
 * Source as shown on the Sources screen.
 *
 * @property source configured source
 * @property defaultFor roles it is the profile's default source of, in role order
 * @property limits request limits with their usage in the current windows
 * @property lastCall outcome of the last call to the source; null when it was never called
 * @property totalCalls number of calls to the source that never resets; null when the source has no total counter
 */
data class SourceOverview(
    val source: SourceDescription,
    val defaultFor: List<SourceRole>,
    val limits: List<RequestUsage>,
    val lastCall: SourceCallOutcome?,
    val totalCalls: Long? = null,
) {
    /** Whether it is the profile's default [SourceRole.SEARCH] source. */
    val isDefault: Boolean get() = SourceRole.SEARCH in defaultFor
}

/**
 * Source offered by the source dropdown of a detailed screen tab (ADR-089).
 *
 * @property source configured source
 * @property isDefault whether it is the profile's default source of the company's role
 */
data class SourceOption(
    val source: SourceDescription,
    val isDefault: Boolean,
)

/**
 * Source dropdown options of every detailed screen tab of one company, each list in display order (ADR-089).
 *
 * @property priceRatios sources of quotes or ratios
 * @property dividends sources of dividends
 * @property balanceSheet sources of balance sheets
 * @property profile sources of company profiles
 */
data class CompanySources(
    val priceRatios: List<SourceOption>,
    val dividends: List<SourceOption>,
    val balanceSheet: List<SourceOption>,
    val profile: List<SourceOption>,
)
