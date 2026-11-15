package com.sixteenscreener.app.testing

import com.sixteenscreener.app.data.remote.ApiClient
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.BalanceSheet
import com.sixteenscreener.app.domain.model.CompanyProfileData
import com.sixteenscreener.app.domain.model.CompanySummary
import com.sixteenscreener.app.domain.model.PriceAndRatios
import com.sixteenscreener.app.domain.model.Profile
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.SearchResults
import com.sixteenscreener.app.domain.model.Source
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol

class FakeApiClient : ApiClient {

    val calls = mutableListOf<String>()

    var getOrCreateProfile: suspend (String) -> ApiResult<Profile> = { notStubbed() }
    var getProfile: suspend (ProfileId) -> ApiResult<Profile> = { notStubbed() }
    var getWatchlist: suspend (ProfileId) -> ApiResult<List<CompanySummary>> = { notStubbed() }
    var refreshWatchlistRows: suspend (ProfileId, List<Symbol>) -> ApiResult<List<CompanySummary>> = { _, _ -> notStubbed() }
    var addToWatchlist: suspend (ProfileId, Symbol) -> ApiResult<Unit> = { _, _ -> notStubbed() }
    var removeFromWatchlist: suspend (ProfileId, Symbol) -> ApiResult<Unit> = { _, _ -> notStubbed() }
    var search: suspend (ProfileId, String) -> ApiResult<SearchResults> = { _, _ -> notStubbed() }
    var getPriceAndRatios: suspend (ProfileId, Symbol, SourceId?, Boolean) -> ApiResult<PriceAndRatios> =
        { _, _, _, _ -> notStubbed() }
    var getBalanceSheet: suspend (ProfileId, Symbol, SourceId?, Boolean) -> ApiResult<BalanceSheet> =
        { _, _, _, _ -> notStubbed() }
    var getCompanyProfile: suspend (ProfileId, Symbol, SourceId?, Boolean) -> ApiResult<CompanyProfileData> =
        { _, _, _, _ -> notStubbed() }
    var getSources: suspend (ProfileId) -> ApiResult<List<Source>> = { notStubbed() }

    override suspend fun getOrCreateProfile(name: String) = record("getOrCreateProfile($name)") { getOrCreateProfile.invoke(name) }

    override suspend fun getProfile(profileId: ProfileId) = record("getProfile(${profileId.value})") { getProfile.invoke(profileId) }

    override suspend fun getWatchlist(profileId: ProfileId) =
        record("getWatchlist(${profileId.value})") { getWatchlist.invoke(profileId) }

    override suspend fun refreshWatchlistRows(profileId: ProfileId, symbols: List<Symbol>) =
        record("refreshWatchlistRows(${profileId.value}, ${symbols.joinToString(",") { it.value }})") {
            refreshWatchlistRows.invoke(profileId, symbols)
        }

    override suspend fun addToWatchlist(profileId: ProfileId, symbol: Symbol) =
        record("addToWatchlist(${profileId.value}, ${symbol.value})") { addToWatchlist.invoke(profileId, symbol) }

    override suspend fun removeFromWatchlist(profileId: ProfileId, symbol: Symbol) =
        record("removeFromWatchlist(${profileId.value}, ${symbol.value})") { removeFromWatchlist.invoke(profileId, symbol) }

    override suspend fun search(profileId: ProfileId, query: String) =
        record("search(${profileId.value}, $query)") { search.invoke(profileId, query) }

    override suspend fun getPriceAndRatios(profileId: ProfileId, symbol: Symbol, source: SourceId?, refresh: Boolean) =
        record("getPriceAndRatios(${profileId.value}, ${symbol.value}, ${source?.value}, $refresh)") {
            getPriceAndRatios.invoke(profileId, symbol, source, refresh)
        }

    override suspend fun getBalanceSheet(profileId: ProfileId, symbol: Symbol, source: SourceId?, refresh: Boolean) =
        record("getBalanceSheet(${profileId.value}, ${symbol.value}, ${source?.value}, $refresh)") {
            getBalanceSheet.invoke(profileId, symbol, source, refresh)
        }

    override suspend fun getCompanyProfile(profileId: ProfileId, symbol: Symbol, source: SourceId?, refresh: Boolean) =
        record("getCompanyProfile(${profileId.value}, ${symbol.value}, ${source?.value}, $refresh)") {
            getCompanyProfile.invoke(profileId, symbol, source, refresh)
        }

    override suspend fun getSources(profileId: ProfileId) = record("getSources(${profileId.value})") { getSources.invoke(profileId) }

    private inline fun <T> record(call: String, block: () -> T): T {
        calls += call
        return block()
    }

    private fun notStubbed(): Nothing = throw AssertionError("Unexpected API call")
}
