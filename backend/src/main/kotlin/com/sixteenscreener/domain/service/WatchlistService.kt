package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.CompanySummary
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.port.inbound.WatchlistUseCase
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
        val symbols = profile.watchlist
        if (symbols.isEmpty()) return emptyList()
        val listings = symbolDirectory.findAll(symbols)
        val data = companyData.watchlistData(catalog.defaultSourceOf(profile), symbols, listings)
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
