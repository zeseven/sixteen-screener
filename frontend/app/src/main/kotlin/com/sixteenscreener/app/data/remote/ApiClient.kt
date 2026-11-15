package com.sixteenscreener.app.data.remote

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

/**
 * Backend API client with one function per alpha operation of `docs/openapi.yaml`.
 * All functions are main-safe and never throw for HTTP, network or parsing failures.
 */
interface ApiClient {
    /** `POST /profiles`: returns the profile with [name], creating it if it doesn't exist. */
    suspend fun getOrCreateProfile(name: String): ApiResult<Profile>

    /** `GET /profiles/{profileId}`. */
    suspend fun getProfile(profileId: ProfileId): ApiResult<Profile>

    /** `GET /profiles/{profileId}/watchlist`. */
    suspend fun getWatchlist(profileId: ProfileId): ApiResult<List<CompanySummary>>

    /** `GET /profiles/{profileId}/watchlist/refresh?symbols=`: the given watchlist rows (1 to 50 [symbols]) in watchlist order. */
    suspend fun refreshWatchlistRows(profileId: ProfileId, symbols: List<Symbol>): ApiResult<List<CompanySummary>>

    /** `PUT /profiles/{profileId}/watchlist/{symbol}`; idempotent. */
    suspend fun addToWatchlist(profileId: ProfileId, symbol: Symbol): ApiResult<Unit>

    /** `DELETE /profiles/{profileId}/watchlist/{symbol}`; idempotent. */
    suspend fun removeFromWatchlist(profileId: ProfileId, symbol: Symbol): ApiResult<Unit>

    /** `GET /profiles/{profileId}/search?query=`. */
    suspend fun search(profileId: ProfileId, query: String): ApiResult<SearchResults>

    /** `GET /profiles/{profileId}/companies/{symbol}/price-ratios`; [source] `null` means the profile's default source. */
    suspend fun getPriceAndRatios(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId? = null,
        refresh: Boolean = false,
    ): ApiResult<PriceAndRatios>

    /** `GET /profiles/{profileId}/companies/{symbol}/balance-sheet`; [source] `null` means the profile's default source. */
    suspend fun getBalanceSheet(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId? = null,
        refresh: Boolean = false,
    ): ApiResult<BalanceSheet>

    /** `GET /profiles/{profileId}/companies/{symbol}/profile`; [source] `null` means the profile's default source. */
    suspend fun getCompanyProfile(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId? = null,
        refresh: Boolean = false,
    ): ApiResult<CompanyProfileData>

    /** `GET /profiles/{profileId}/sources`. */
    suspend fun getSources(profileId: ProfileId): ApiResult<List<Source>>
}
