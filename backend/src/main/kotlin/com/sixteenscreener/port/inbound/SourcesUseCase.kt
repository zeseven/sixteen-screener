package com.sixteenscreener.port.inbound

import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.SourceOverview

/**
 * Sources screen: configured sources with capabilities, limit usage and the connection indicator (ADR-028).
 */
interface SourcesUseCase {
    /**
     * Enabled sources in configuration order, the default source of [profileId] marked.
     * Unreadable counters count as unused and an unreadable last call as never called; neither fails the request.
     */
    suspend fun sources(profileId: ProfileId): List<SourceOverview>
}
