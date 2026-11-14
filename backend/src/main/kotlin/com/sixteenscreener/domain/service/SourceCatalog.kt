package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.SourceId

class SourceCatalog(
    val defaultSource: SourceId,
    private val capabilitiesBySource: Map<SourceId, Set<Capability>>,
) {
    init {
        require(defaultSource in capabilitiesBySource) { "Default source '$defaultSource' is not configured" }
    }

    fun isKnown(source: SourceId): Boolean = source in capabilitiesBySource

    fun supports(source: SourceId, capability: Capability): Boolean =
        capabilitiesBySource[source].orEmpty().contains(capability)

    fun defaultSourceOf(profile: Profile): SourceId = profile.defaultSource?.takeIf(::isKnown) ?: defaultSource

    fun selectSource(profile: Profile, requested: SourceId?): SourceId =
        requested?.also { if (!isKnown(it)) throw ResourceNotFoundException("Unknown source '$it'") }
            ?: defaultSourceOf(profile)
}
