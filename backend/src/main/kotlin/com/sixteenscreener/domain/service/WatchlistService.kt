package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.CompanySummary
import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.inbound.WatchlistUseCase
import com.sixteenscreener.port.inbound.WatchlistUseCase.Companion.MAX_REFRESH_SYMBOLS
import com.sixteenscreener.port.outbound.ProfileRepository
import com.sixteenscreener.port.outbound.SymbolDirectory

class WatchlistService(
    private val profiles: ProfileRepository,
    private val catalog: SourceCatalog,
    private val symbolDirectory: SymbolDirectory,
    private val companyData: CompanyDataService,
    private val identities: CompanyIdentityResolver,
) : WatchlistUseCase {

    override suspend fun watchlist(profileId: ProfileId): List<CompanySummary> {
        val profile = profiles.existing(profileId)
        return rows(profile, profile.watchlist, refreshStaleQuotes = false)
    }

    override suspend fun refreshWatchlistRows(profileId: ProfileId, symbols: List<Symbol>): List<CompanySummary> {
        if (symbols.size !in 1..MAX_REFRESH_SYMBOLS) {
            throw InvalidInputException("Expected 1 to $MAX_REFRESH_SYMBOLS symbols, got ${symbols.size}")
        }
        val profile = profiles.existing(profileId)
        val requested = symbols.toSet()
        return rows(profile, profile.watchlist.filter { it in requested }, refreshStaleQuotes = true)
    }

    private suspend fun rows(
        profile: Profile,
        symbols: List<Symbol>,
        refreshStaleQuotes: Boolean,
    ): List<CompanySummary> {
        if (symbols.isEmpty()) return emptyList()
        val listings = symbolDirectory.findAll(symbols)
        val data = companyData.watchlistData(catalog.defaultSourceOf(profile), symbols, listings, refreshStaleQuotes)
        return symbols.map { symbol ->
            val company = data.getValue(symbol)
            val cachedProfile = company.profile.data
            CompanySummary(
                identity = identities.identify(symbol, listings[symbol], cachedProfile?.name),
                sector = cachedProfile?.sector,
                quote = company.quote,
            )
        }
    }
}
