package com.sixteenscreener.port.inbound

import com.sixteenscreener.domain.model.CompanySources
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.SourceOverview
import com.sixteenscreener.domain.model.Symbol

/**
 * Sources screen: configured sources with capabilities, limit usage and the connection indicator (ADR-028).
 */
interface SourcesUseCase {
    /**
     * Enabled sources in configuration order with the roles each one is the default source of for [profileId].
     * Unreadable counters count as unused and an unreadable last call as never called; neither fails the request.
     */
    suspend fun sources(profileId: ProfileId): List<SourceOverview>

    /**
     * Source dropdown options of every detailed screen tab of [symbol] for [profileId] (ADR-089): the enabled sources
     * configured for the company's role that provide the tab's data (SEC EDGAR also for the balance sheet of a US company),
     * the profile's default source of the role first, then configuration order, Yahoo Finance last. Calls no source and
     * reads no cache. Throws [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown profile.
     */
    suspend fun companySources(profileId: ProfileId, symbol: Symbol): CompanySources
}
