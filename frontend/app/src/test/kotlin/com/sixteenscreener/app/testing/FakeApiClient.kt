package com.sixteenscreener.app.testing

import com.sixteenscreener.app.data.remote.ApiClient
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.BalanceSheet
import com.sixteenscreener.app.domain.model.CompanyProfileData
import com.sixteenscreener.app.domain.model.Exchange
import com.sixteenscreener.app.domain.model.IndicatorSetting
import com.sixteenscreener.app.domain.model.MarketData
import com.sixteenscreener.app.domain.model.PriceAndRatios
import com.sixteenscreener.app.domain.model.Profile
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.RequestTrigger
import com.sixteenscreener.app.domain.model.SearchResults
import com.sixteenscreener.app.domain.model.Source
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.WatchlistItem

class FakeApiClient : ApiClient {

    val calls = mutableListOf<String>()

    var getOrCreateProfile: suspend (String) -> ApiResult<Profile> = { notStubbed() }
    var getProfile: suspend (ProfileId) -> ApiResult<Profile> = { notStubbed() }
    var getWatchlist: suspend (ProfileId) -> ApiResult<List<WatchlistItem>> = { notStubbed() }
    var getWatchlistMarketData: suspend (ProfileId, List<Symbol>) -> ApiResult<List<MarketData>> = { _, _ -> notStubbed() }
    var addToWatchlist: suspend (ProfileId, Symbol, String) -> ApiResult<WatchlistItem> = { _, _, _ -> notStubbed() }
    var removeFromWatchlist: suspend (ProfileId, Symbol) -> ApiResult<Unit> = { _, _ -> notStubbed() }
    var search: suspend (ProfileId, String) -> ApiResult<SearchResults> = { _, _ -> notStubbed() }
    var getPriceAndRatios: suspend (ProfileId, Symbol, SourceId?, RequestTrigger) -> ApiResult<PriceAndRatios> =
        { _, _, _, _ -> notStubbed() }
    var getBalanceSheet: suspend (ProfileId, Symbol, SourceId?, RequestTrigger) -> ApiResult<BalanceSheet> =
        { _, _, _, _ -> notStubbed() }
    var getCompanyProfile: suspend (ProfileId, Symbol, SourceId?, RequestTrigger) -> ApiResult<CompanyProfileData> =
        { _, _, _, _ -> notStubbed() }
    var getSources: suspend (ProfileId) -> ApiResult<List<Source>> = { notStubbed() }
    var getExchanges: suspend () -> ApiResult<List<Exchange>> = { notStubbed() }
    var getIndicatorSettings: suspend (ProfileId) -> ApiResult<List<IndicatorSetting>> = { notStubbed() }
    var replaceIndicatorSettings: suspend (ProfileId, List<IndicatorSetting>) -> ApiResult<List<IndicatorSetting>> =
        { _, _ -> notStubbed() }

    override suspend fun getOrCreateProfile(name: String) = record("getOrCreateProfile($name)") { getOrCreateProfile.invoke(name) }

    override suspend fun getProfile(profileId: ProfileId) = record("getProfile(${profileId.value})") { getProfile.invoke(profileId) }

    override suspend fun getWatchlist(profileId: ProfileId) =
        record("getWatchlist(${profileId.value})") { getWatchlist.invoke(profileId) }

    override suspend fun getWatchlistMarketData(profileId: ProfileId, symbols: List<Symbol>) =
        record("getWatchlistMarketData(${profileId.value}, ${symbols.joinToString(",") { it.value }})") {
            getWatchlistMarketData.invoke(profileId, symbols)
        }

    override suspend fun addToWatchlist(profileId: ProfileId, symbol: Symbol, name: String) =
        record("addToWatchlist(${profileId.value}, ${symbol.value})") { addToWatchlist.invoke(profileId, symbol, name) }

    override suspend fun removeFromWatchlist(profileId: ProfileId, symbol: Symbol) =
        record("removeFromWatchlist(${profileId.value}, ${symbol.value})") { removeFromWatchlist.invoke(profileId, symbol) }

    override suspend fun search(profileId: ProfileId, query: String) =
        record("search(${profileId.value}, $query)") { search.invoke(profileId, query) }

    override suspend fun getPriceAndRatios(profileId: ProfileId, symbol: Symbol, source: SourceId?, trigger: RequestTrigger) =
        record("getPriceAndRatios(${profileId.value}, ${symbol.value}, ${source?.value}, ${trigger.name})") {
            getPriceAndRatios.invoke(profileId, symbol, source, trigger)
        }

    override suspend fun getBalanceSheet(profileId: ProfileId, symbol: Symbol, source: SourceId?, trigger: RequestTrigger) =
        record("getBalanceSheet(${profileId.value}, ${symbol.value}, ${source?.value}, ${trigger.name})") {
            getBalanceSheet.invoke(profileId, symbol, source, trigger)
        }

    override suspend fun getCompanyProfile(profileId: ProfileId, symbol: Symbol, source: SourceId?, trigger: RequestTrigger) =
        record("getCompanyProfile(${profileId.value}, ${symbol.value}, ${source?.value}, ${trigger.name})") {
            getCompanyProfile.invoke(profileId, symbol, source, trigger)
        }

    override suspend fun getSources(profileId: ProfileId) = record("getSources(${profileId.value})") { getSources.invoke(profileId) }

    override suspend fun getExchanges() = record("getExchanges()") { getExchanges.invoke() }

    override suspend fun getIndicatorSettings(profileId: ProfileId) =
        record("getIndicatorSettings(${profileId.value})") { getIndicatorSettings.invoke(profileId) }

    override suspend fun replaceIndicatorSettings(profileId: ProfileId, settings: List<IndicatorSetting>) =
        record("replaceIndicatorSettings(${profileId.value}, ${settings.joinToString(",") { it.ratioCode }})") {
            replaceIndicatorSettings.invoke(profileId, settings)
        }

    private inline fun <T> record(call: String, block: () -> T): T {
        calls += call
        return block()
    }

    private fun notStubbed(): Nothing = throw AssertionError("Unexpected API call")
}
