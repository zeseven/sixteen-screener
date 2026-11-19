package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.data.remote.ApiClient
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.BalanceSheet
import com.sixteenscreener.app.domain.model.CompanyProfileData
import com.sixteenscreener.app.domain.model.PriceAndRatios
import com.sixteenscreener.app.domain.model.RequestTrigger
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Company data of the detailed screen tabs for the active profile. Nothing is kept in memory: the backend caches source data.
 * A `404` means an unknown symbol here, so it never clears the stored profile.
 */
interface CompanyRepository {
    /** Price and ratios from the profile's default source; [trigger] tells the backend whether the user asked for a refresh. */
    suspend fun getPriceAndRatios(symbol: Symbol, trigger: RequestTrigger = RequestTrigger.AUTO): ApiResult<PriceAndRatios>

    /** Balance sheet from [source], or from the profile's default source when `null`. */
    suspend fun getBalanceSheet(
        symbol: Symbol,
        source: SourceId? = null,
        trigger: RequestTrigger = RequestTrigger.AUTO,
    ): ApiResult<BalanceSheet>

    /** Company profile from the profile's default source. */
    suspend fun getCompanyProfile(symbol: Symbol, trigger: RequestTrigger = RequestTrigger.AUTO): ApiResult<CompanyProfileData>
}

@Singleton
class DefaultCompanyRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository,
) : CompanyRepository {

    override suspend fun getPriceAndRatios(symbol: Symbol, trigger: RequestTrigger): ApiResult<PriceAndRatios> =
        profileRepository.withActiveProfile { profileId -> apiClient.getPriceAndRatios(profileId, symbol, trigger = trigger) }

    override suspend fun getBalanceSheet(symbol: Symbol, source: SourceId?, trigger: RequestTrigger): ApiResult<BalanceSheet> =
        profileRepository.withActiveProfile { profileId -> apiClient.getBalanceSheet(profileId, symbol, source, trigger) }

    override suspend fun getCompanyProfile(symbol: Symbol, trigger: RequestTrigger): ApiResult<CompanyProfileData> =
        profileRepository.withActiveProfile { profileId -> apiClient.getCompanyProfile(profileId, symbol, trigger = trigger) }
}
