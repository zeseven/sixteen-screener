package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.DataStatus
import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.WatchlistEntry
import com.sixteenscreener.domain.model.WatchlistItem
import com.sixteenscreener.port.inbound.ProfileRegistration
import com.sixteenscreener.port.inbound.ProfileSettings
import com.sixteenscreener.port.inbound.ProfileUseCase
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.ProfileRepository
import com.sixteenscreener.port.outbound.SymbolDirectory
import com.sixteenscreener.port.outbound.WatchlistUpdate

class ProfileService(
    private val profiles: ProfileRepository,
    private val symbolDirectory: SymbolDirectory,
    private val catalog: SourceCatalog,
    private val companyData: CompanyDataService,
    private val identities: CompanyIdentityResolver,
    private val clock: Clock,
) : ProfileUseCase {

    override suspend fun getOrCreate(rawName: String): ProfileRegistration {
        val name = ProfileName.of(rawName)
        val now = clock.now()
        val candidate = Profile(ProfileId.random(), name, defaultSource = null, emptyList(), createdAt = now, updatedAt = now)
        val creation = profiles.getOrCreate(candidate)
        return ProfileRegistration(settingsOf(creation.profile), creation.created)
    }

    override suspend fun profile(id: ProfileId): ProfileSettings = settingsOf(profiles.existing(id))

    override suspend fun addToWatchlist(id: ProfileId, symbol: Symbol): WatchlistItem {
        val profile = profiles.existing(id)
        profile.watchlistEntry(symbol)?.let { return identities.watchlistItemOf(it, symbolDirectory.find(symbol)) }
        val listing = symbolDirectory.find(symbol) ?: throw ResourceNotFoundException("Unknown symbol '$symbol'")
        if (profile.watchlist.size >= Profile.MAX_WATCHLIST_SIZE) throw watchlistFull()
        val entry = WatchlistEntry(symbol, sectorOf(profile, symbol), tags = emptyList())
        return when (val update = profiles.addToWatchlist(id, entry, clock.now())) {
            is WatchlistUpdate.Updated -> identities.watchlistItemOf(update.profile.watchlistEntry(symbol) ?: entry, listing)
            WatchlistUpdate.ProfileNotFound -> throw profileNotFound(id)
            WatchlistUpdate.WatchlistFull -> throw watchlistFull()
        }
    }

    override suspend fun removeFromWatchlist(id: ProfileId, symbol: Symbol) {
        if (profiles.removeFromWatchlist(id, symbol, clock.now()) == WatchlistUpdate.ProfileNotFound) {
            throw profileNotFound(id)
        }
    }

    private suspend fun sectorOf(profile: Profile, symbol: Symbol): String? =
        companyData.unexpiredCompanyProfile(catalog.defaultSourceOf(profile), symbol)
            .takeIf { it.status in LOADED }
            ?.data
            ?.sector

    private fun watchlistFull() = InvalidInputException("Watchlist is full (${Profile.MAX_WATCHLIST_SIZE} companies)")

    private fun settingsOf(profile: Profile) = ProfileSettings(
        id = profile.id,
        name = profile.name,
        defaultSource = catalog.defaultSourceOf(profile),
        watchlist = profile.watchlistSymbols,
    )

    private companion object {
        val LOADED = setOf(DataStatus.FRESH, DataStatus.CACHED)
    }
}
