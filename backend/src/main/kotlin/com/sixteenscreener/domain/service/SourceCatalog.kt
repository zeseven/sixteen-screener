package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.DefaultSources
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceRole

class SourceCatalog(
    val defaultSources: DefaultSources,
    private val capabilitiesBySource: Map<SourceId, Set<Capability>>,
    private val rolesBySource: Map<SourceId, Set<SourceRole>>,
    private val manualOnlySources: Set<SourceId> = emptySet(),
) {
    init {
        SourceRole.entries.forEach { role ->
            val source = defaultSources.of(role)
            require(isKnown(source)) { "Default $role source '$source' is not configured" }
            require(role in rolesOf(source)) { "Default $role source '$source' is not configured for the role" }
        }
    }

    fun isKnown(source: SourceId): Boolean = source in capabilitiesBySource

    fun supports(source: SourceId, capability: Capability): Boolean =
        capabilitiesBySource[source].orEmpty().contains(capability)

    fun automaticRequests(source: SourceId): Boolean = source !in manualOnlySources

    fun rolesOf(source: SourceId): Set<SourceRole> = rolesBySource[source].orEmpty()

    fun serves(source: SourceId, role: SourceRole, capability: Capability): Boolean =
        role in rolesOf(source) ||
            (source == US_BALANCE_SHEET_SOURCE && role == SourceRole.US && capability == Capability.BALANCE_SHEET)

    fun defaultSourceOf(role: SourceRole): SourceId = defaultSources.of(role)

    fun selectSource(requested: SourceId?, role: SourceRole): SourceId =
        requested?.also { if (!isKnown(it)) throw ResourceNotFoundException("Unknown source '$it'") }
            ?: defaultSourceOf(role)

    companion object {
        /** Additional balance sheet source of US companies, accepted without the `US` role (ADR-083). */
        val US_BALANCE_SHEET_SOURCE: SourceId = SourceId.of("sec-edgar")

        /** Source listed after all others in the source dropdown: the last option for data others don't provide (ADR-089). */
        val LAST_OPTION_SOURCE: SourceId = SourceId.of("yahoo")

        /** Source whose company profile sets the watchlist sector of a company that has none (ADR-090). */
        val SECTOR_FALLBACK_SOURCE: SourceId = SourceId.of("yahoo")
    }
}
