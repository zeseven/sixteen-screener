package com.sixteenscreener.app.data.remote

import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.BalanceSheet
import com.sixteenscreener.app.domain.model.CompanyProfileData
import com.sixteenscreener.app.domain.model.CompanySources
import com.sixteenscreener.app.domain.model.Dividends
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
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.domain.model.WatchlistItem

/**
 * Backend API client with one function per operation of `docs/openapi.yaml` used by the app.
 * All functions are main-safe and never throw for HTTP, network or parsing failures.
 */
interface ApiClient {
    /** `POST /profiles`: returns the profile with [name], creating it if it doesn't exist. */
    suspend fun getOrCreateProfile(name: String): ApiResult<Profile>

    /** `GET /profiles/{profileId}`. */
    suspend fun getProfile(profileId: ProfileId): ApiResult<Profile>

    /** `GET /profiles/{profileId}/watchlist`. */
    suspend fun getWatchlist(profileId: ProfileId): ApiResult<List<WatchlistItem>>

    /**
     * `GET /profiles/{profileId}/watchlist/market-data?symbols=`: market data of the given watchlist rows (1 to 50 [symbols])
     * in watchlist order; symbols not in the watchlist are skipped.
     */
    suspend fun getWatchlistMarketData(profileId: ProfileId, symbols: List<Symbol>): ApiResult<List<MarketData>>

    /**
     * `PUT /profiles/{profileId}/watchlist/{symbol}`; idempotent, returns the stored watchlist item. The company [name] shown
     * to the user is sent (cut to 200 characters) for symbols missing from the exchange directory; a blank name sends no body.
     */
    suspend fun addToWatchlist(profileId: ProfileId, symbol: Symbol, name: String): ApiResult<WatchlistItem>

    /** `DELETE /profiles/{profileId}/watchlist/{symbol}`; idempotent. */
    suspend fun removeFromWatchlist(profileId: ProfileId, symbol: Symbol): ApiResult<Unit>

    /** `GET /profiles/{profileId}/search?query=`. */
    suspend fun search(profileId: ProfileId, query: String): ApiResult<SearchResults>

    /**
     * `GET /profiles/{profileId}/companies/{symbol}/sources`: the source dropdown options of each detailed screen tab, in backend
     * order.
     */
    suspend fun getCompanySources(profileId: ProfileId, symbol: Symbol): ApiResult<CompanySources>

    /**
     * `GET /profiles/{profileId}/companies/{symbol}/price-ratios`; [source] `null` means the profile's default source of the
     * company's role, picked by the backend.
     */
    suspend fun getPriceAndRatios(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId? = null,
        trigger: RequestTrigger = RequestTrigger.AUTO,
    ): ApiResult<PriceAndRatios>

    /**
     * `GET /profiles/{profileId}/companies/{symbol}/dividends`; [source] `null` means the profile's default source of the
     * company's role, picked by the backend.
     */
    suspend fun getDividends(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId? = null,
        trigger: RequestTrigger = RequestTrigger.AUTO,
    ): ApiResult<Dividends>

    /**
     * `GET /profiles/{profileId}/companies/{symbol}/balance-sheet`; [source] `null` means the profile's default source of the
     * company's role, picked by the backend.
     */
    suspend fun getBalanceSheet(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId? = null,
        trigger: RequestTrigger = RequestTrigger.AUTO,
    ): ApiResult<BalanceSheet>

    /**
     * `GET /profiles/{profileId}/companies/{symbol}/profile`; [source] `null` means the profile's default source of the
     * company's role, picked by the backend.
     */
    suspend fun getCompanyProfile(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId? = null,
        trigger: RequestTrigger = RequestTrigger.AUTO,
    ): ApiResult<CompanyProfileData>

    /**
     * `PUT /profiles/{profileId}/companies/{symbol}/tags`: replaces all tags of a watchlist company with [tags]; returns the stored
     * tags.
     */
    suspend fun setCompanyTags(profileId: ProfileId, symbol: Symbol, tags: List<Tag>): ApiResult<List<Tag>>

    /** `GET /profiles/{profileId}/tags`: distinct tags used by the profile, sorted by text ignoring case, then by icon. */
    suspend fun getProfileTags(profileId: ProfileId): ApiResult<List<Tag>>

    /** `GET /profiles/{profileId}/sources`. */
    suspend fun getSources(profileId: ProfileId): ApiResult<List<Source>>

    /** `GET /profiles/{profileId}/indicator-settings`: the profile's indicator settings in settings order. */
    suspend fun getIndicatorSettings(profileId: ProfileId): ApiResult<List<IndicatorSetting>>

    /**
     * `PUT /profiles/{profileId}/indicator-settings`: replaces the configurable part of [settings] (switch, thresholds and levels);
     * returns the stored settings.
     */
    suspend fun replaceIndicatorSettings(profileId: ProfileId, settings: List<IndicatorSetting>): ApiResult<List<IndicatorSetting>>

    /** `GET /exchanges`: the static exchange table in backend order. */
    suspend fun getExchanges(): ApiResult<List<Exchange>>
}
