package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.data.remote.ApiClient
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.BalanceSheet
import com.sixteenscreener.app.domain.model.CompanyProfileData
import com.sixteenscreener.app.domain.model.PriceAndRatios
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Company data of the detailed screen tabs for the active profile. Nothing is kept in memory: the backend caches source data.
 * A `404` means an unknown symbol here, so it never clears the stored profile.
 */
interface CompanyRepository {
    /** Price and ratios from the profile's default source; [refresh] asks the backend to fetch from the source. */
    suspend fun getPriceAndRatios(symbol: Symbol, refresh: Boolean = false): ApiResult<PriceAndRatios>

    /** Balance sheet from [source], or from the profile's default source when `null`. */
    suspend fun getBalanceSheet(symbol: Symbol, source: SourceId? = null, refresh: Boolean = false): ApiResult<BalanceSheet>

    /** Company profile from the profile's default source. */
    suspend fun getCompanyProfile(symbol: Symbol, refresh: Boolean = false): ApiResult<CompanyProfileData>
}

@Singleton
class DefaultCompanyRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository,
) : CompanyRepository {

    override suspend fun getPriceAndRatios(symbol: Symbol, refresh: Boolean): ApiResult<PriceAndRatios> =
        profileRepository.withActiveProfile { profileId -> apiClient.getPriceAndRatios(profileId, symbol, refresh = refresh) }

    override suspend fun getBalanceSheet(symbol: Symbol, source: SourceId?, refresh: Boolean): ApiResult<BalanceSheet> =
        profileRepository.withActiveProfile { profileId -> apiClient.getBalanceSheet(profileId, symbol, source, refresh) }

    override suspend fun getCompanyProfile(symbol: Symbol, refresh: Boolean): ApiResult<CompanyProfileData> =
        profileRepository.withActiveProfile { profileId -> apiClient.getCompanyProfile(profileId, symbol, refresh = refresh) }
}
