package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.SearchHit
import com.sixteenscreener.domain.model.SearchQuery
import com.sixteenscreener.domain.model.SearchStatus
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.port.inbound.SearchResult
import com.sixteenscreener.port.inbound.SearchResults
import com.sixteenscreener.port.inbound.SearchUseCase
import com.sixteenscreener.port.outbound.MarketDataSource
import com.sixteenscreener.port.outbound.ProfileRepository
import com.sixteenscreener.port.outbound.SymbolDirectory

class SearchService(
    private val profiles: ProfileRepository,
    private val catalog: SourceCatalog,
    private val marketDataSources: Map<SourceId, MarketDataSource>,
    private val symbolDirectory: SymbolDirectory,
    private val identities: CompanyIdentityResolver,
    private val guard: SourceCallGuard,
    private val outcomes: SourceCallOutcomes,
    private val responses: SearchResponseCache,
) : SearchUseCase {

    private data class SourceAnswer(val status: SearchStatus, val hits: List<SearchHit>)

    override suspend fun search(profileId: ProfileId, rawQuery: String): SearchResults {
        val query = SearchQuery.of(rawQuery)
        val profile = profiles.existing(profileId)
        val source = catalog.defaultSourceOf(profile)
        val answer = answer(source, query)
        val hits = answer.hits.distinctBy { it.symbol }.matching(query).take(MAX_RESULTS)
        val symbols = hits.map { it.symbol }
        val lookups = if (symbols.isEmpty()) emptyMap() else symbolDirectory.lookupAll(symbols)
        val watchlist = profile.watchlistSymbols.toSet()
        val items = hits.map { hit ->
            SearchResult(
                identity = identities.identify(hit.symbol, lookups[hit.symbol]?.listing, hit.name, hit.type),
                inWatchlist = hit.symbol in watchlist,
            )
        }
        return SearchResults(source, answer.status, items)
    }

    private fun List<SearchHit>.matching(query: SearchQuery): List<SearchHit> {
        if (!query.matchesTickersOnly) return this
        val (exactTickers, prefixedTickers) = this
            .filter { it.symbol.ticker.startsWith(query.value, ignoreCase = true) }
            .partition { it.symbol.ticker.equals(query.value, ignoreCase = true) }
        return exactTickers + prefixedTickers
    }

    private suspend fun answer(source: SourceId, query: SearchQuery): SourceAnswer {
        val searchable = marketDataSources[source]?.takeIf { catalog.supports(source, Capability.SEARCH) }
            ?: return SourceAnswer(SearchStatus.SOURCE_ERROR, emptyList())
        responses.get(source, query)?.let { return SourceAnswer(SearchStatus.OK, it) }
        val result = outcomes.recording { recorder -> guard.single(source, recorder) { searchable.search(query.value) } }
        return when (result) {
            is SourceResult.Success -> {
                responses.put(source, query, result.data)
                SourceAnswer(SearchStatus.OK, result.data)
            }
            is SourceResult.NoData -> SourceAnswer(SearchStatus.OK, emptyList())
            is SourceResult.LimitReached -> SourceAnswer(SearchStatus.LIMIT_REACHED, emptyList())
            is SourceResult.NotSupported, is SourceResult.SourceError -> SourceAnswer(SearchStatus.SOURCE_ERROR, emptyList())
        }
    }

    companion object {
        /** Maximum number of results in a search response (ADR-054). */
        const val MAX_RESULTS = 25
    }
}
