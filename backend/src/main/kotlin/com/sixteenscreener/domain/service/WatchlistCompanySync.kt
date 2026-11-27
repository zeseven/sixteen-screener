package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceRole
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.WatchlistEntry
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
        val entry = profile.watchlistEntry(symbol) ?: return
        val update = when (source) {
            catalog.defaultSourceOf(SourceRole.of(symbol)) -> defaultSourceUpdate(entry, companyProfile)
            SourceCatalog.SECTOR_FALLBACK_SOURCE -> fallbackSectorUpdate(entry, companyProfile)
            else -> null
        } ?: return
        try {
            profiles.updateWatchlistCompany(profile.id, symbol, update.name, update.sector, clock.now())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to update the watchlist company of '{}' in profile '{}'", symbol, profile.id, e)
        }
    }

    private fun defaultSourceUpdate(entry: WatchlistEntry, companyProfile: CompanyProfile): CompanyUpdate? {
        val name = companyProfile.name?.takeIf { it.isNotBlank() } ?: entry.name
        return CompanyUpdate(name, companyProfile.sector).takeUnless { it.name == entry.name && it.sector == entry.sector }
    }

    private fun fallbackSectorUpdate(entry: WatchlistEntry, companyProfile: CompanyProfile): CompanyUpdate? {
        if (entry.sector != null) return null
        val sector = companyProfile.sector?.takeIf { it.isNotBlank() } ?: return null
        return CompanyUpdate(entry.name, sector)
    }

    private data class CompanyUpdate(val name: String, val sector: String?)

    private companion object {
        private val logger = LoggerFactory.getLogger(WatchlistCompanySync::class.java)
    }
}
