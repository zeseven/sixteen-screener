package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.DataStatus
import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.domain.model.SourceRole
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.SymbolLookup
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
    private val marketResolver: MarketResolver,
    private val clock: Clock,
) : ProfileUseCase {

    override suspend fun getOrCreate(rawName: String): ProfileRegistration {
        val name = ProfileName.of(rawName)
        val now = clock.now()
        val candidate = Profile(ProfileId.random(), name, emptyList(), createdAt = now, updatedAt = now)
        val creation = profiles.getOrCreate(candidate)
        return ProfileRegistration(settingsOf(creation.profile), creation.created)
    }

    override suspend fun profile(id: ProfileId): ProfileSettings = settingsOf(profiles.existing(id))

    override suspend fun addToWatchlist(id: ProfileId, symbol: Symbol, searchResultName: String?): WatchlistItem {
        val requestedName = requestedNameOf(searchResultName)
        val profile = profiles.existing(id)
        val lookup = symbolDirectory.lookup(symbol)
        profile.watchlistEntry(symbol)?.let { return identities.watchlistItemOf(it, lookup.listing) }
        val name = nameOf(symbol, lookup, requestedName)
        if (profile.watchlist.size >= Profile.MAX_WATCHLIST_SIZE) throw watchlistFull()
        val entry = WatchlistEntry(symbol, name, sectorOf(symbol), tags = emptyList())
        return when (val update = profiles.addToWatchlist(id, entry, clock.now())) {
            is WatchlistUpdate.Updated -> identities.watchlistItemOf(update.profile.watchlistEntry(symbol) ?: entry, lookup.listing)
            WatchlistUpdate.ProfileNotFound -> throw profileNotFound(id)
            WatchlistUpdate.WatchlistFull -> throw watchlistFull()
        }
    }

    override suspend fun removeFromWatchlist(id: ProfileId, symbol: Symbol) {
        if (profiles.removeFromWatchlist(id, symbol, clock.now()) == WatchlistUpdate.ProfileNotFound) {
            throw profileNotFound(id)
        }
    }

    private fun requestedNameOf(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.codePointCount(0, trimmed.length) > WatchlistEntry.MAX_NAME_LENGTH) {
            throw InvalidInputException("Company name must not be longer than ${WatchlistEntry.MAX_NAME_LENGTH} characters")
        }
        return trimmed
    }

    private fun nameOf(symbol: Symbol, lookup: SymbolLookup, requestedName: String?): String = when {
        lookup is SymbolLookup.Listed -> lookup.listing.name
        lookup is SymbolLookup.NotCovered && marketResolver.exchangeOf(symbol.exchangeCode) != null ->
            requestedName ?: symbol.ticker
        else -> throw ResourceNotFoundException("Unknown symbol '$symbol'")
    }

    private suspend fun sectorOf(symbol: Symbol): String? =
        companyData.unexpiredCompanyProfile(catalog.defaultSourceOf(SourceRole.of(symbol)), symbol)
            .takeIf { it.status in SECTOR_SOURCES }
            ?.data
            ?.sector

    private fun watchlistFull() = InvalidInputException("Watchlist is full (${Profile.MAX_WATCHLIST_SIZE} companies)")

    private fun settingsOf(profile: Profile) = ProfileSettings(
        id = profile.id,
        name = profile.name,
        defaultSources = catalog.defaultSources,
        watchlist = profile.watchlistSymbols,
    )

    private companion object {
        val SECTOR_SOURCES = setOf(DataStatus.FRESH, DataStatus.CACHED, DataStatus.LIMIT_REACHED, DataStatus.SOURCE_ERROR)
    }
}
