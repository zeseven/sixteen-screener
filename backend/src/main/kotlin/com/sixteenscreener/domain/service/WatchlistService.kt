package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.model.WatchlistItem
import com.sixteenscreener.domain.model.WatchlistMarketData
import com.sixteenscreener.port.inbound.WatchlistUseCase
import com.sixteenscreener.port.inbound.WatchlistUseCase.Companion.MAX_MARKET_DATA_SYMBOLS
import com.sixteenscreener.port.outbound.ProfileRepository
import com.sixteenscreener.port.outbound.SymbolDirectory

class WatchlistService(
    private val profiles: ProfileRepository,
    private val catalog: SourceCatalog,
    private val symbolDirectory: SymbolDirectory,
    private val companyData: CompanyDataService,
    private val identities: CompanyIdentityResolver,
    private val indicatorSettings: IndicatorSettingsService,
) : WatchlistUseCase {

    override suspend fun watchlist(profileId: ProfileId): List<WatchlistItem> {
        val entries = profiles.existing(profileId).watchlist
        if (entries.isEmpty()) return emptyList()
        val lookups = symbolDirectory.lookupAll(entries.map { it.symbol })
        return entries.map { identities.watchlistItemOf(it, lookups[it.symbol]?.listing) }
    }

    override suspend fun marketData(profileId: ProfileId, symbols: List<Symbol>): List<WatchlistMarketData> {
        if (symbols.size !in 1..MAX_MARKET_DATA_SYMBOLS) {
            throw InvalidInputException("Expected 1 to $MAX_MARKET_DATA_SYMBOLS symbols, got ${symbols.size}")
        }
        val profile = profiles.existing(profileId)
        val requested = symbols.toSet()
        val rows = profile.watchlistSymbols.filter { it in requested }
        if (rows.isEmpty()) return emptyList()
        val settings = indicatorSettings.settingsOf(profile)
        val data = companyData.watchlistData(catalog.defaultSourceOf(profile), rows, symbolDirectory.lookupAll(rows))
        return rows.map { symbol ->
            val (quote, ratios) = data.getValue(symbol)
            val indicators = IndicatorEvaluator.indicators(settings, ratios.data, quote.data?.quote?.price)
            WatchlistMarketData(symbol, quote, ratios, indicators)
        }
    }
}
