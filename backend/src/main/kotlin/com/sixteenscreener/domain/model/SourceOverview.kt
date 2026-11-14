package com.sixteenscreener.domain.model

/**
 * Configured source as described to the domain.
 *
 * @property id source ID
 * @property name display name
 * @property capabilities data the source provides
 * @property selectableAsDefault whether a profile can choose it as its default source
 */
data class SourceDescription(
    val id: SourceId,
    val name: String,
    val capabilities: Set<Capability>,
    val selectableAsDefault: Boolean,
)

/**
 * Source as shown on the Sources screen.
 *
 * @property source configured source
 * @property isDefault whether it is the profile's default source
 * @property limits request limits with their usage in the current windows
 * @property lastCall outcome of the last call to the source; null when it was never called
 */
data class SourceOverview(
    val source: SourceDescription,
    val isDefault: Boolean,
    val limits: List<RequestUsage>,
    val lastCall: SourceCallOutcome?,
)
