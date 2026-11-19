package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceRole
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.ProfileRepository
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

class WatchlistCompanySync(
    private val profiles: ProfileRepository,
    private val catalog: SourceCatalog,
    private val clock: Clock,
) {

    suspend fun profileFetched(profile: Profile, source: SourceId, symbol: Symbol, companyProfile: CompanyProfile) {
        if (source != catalog.defaultSourceOf(SourceRole.of(symbol))) return
        val entry = profile.watchlistEntry(symbol) ?: return
        val name = companyProfile.name?.takeIf { it.isNotBlank() } ?: entry.name
        if (entry.name == name && entry.sector == companyProfile.sector) return
        try {
            profiles.updateWatchlistCompany(profile.id, symbol, name, companyProfile.sector, clock.now())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to update the watchlist company of '{}' in profile '{}'", symbol, profile.id, e)
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(WatchlistCompanySync::class.java)
    }
}
