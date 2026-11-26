package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.data.remote.ApiClient
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.BalanceSheet
import com.sixteenscreener.app.domain.model.CompanyProfileData
import com.sixteenscreener.app.domain.model.CompanySources
import com.sixteenscreener.app.domain.model.Dividends
import com.sixteenscreener.app.domain.model.PriceAndRatios
import com.sixteenscreener.app.domain.model.RequestTrigger
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Company data of the detailed screen tabs for the active profile. Nothing is kept in memory: the backend caches source data.
 * A `404` means an unknown symbol here, so it never clears the stored profile.
 * A `null` source means the profile's default source of the company's role, picked by the backend; [RequestTrigger] tells the
 * backend whether the user asked for a refresh.
 */
interface CompanyRepository {
    /** Source dropdown options of each detailed screen tab for the company, in backend order. */
    suspend fun getCompanySources(symbol: Symbol): ApiResult<CompanySources>

    /** Price and ratios from [source]. */
    suspend fun getPriceAndRatios(
        symbol: Symbol,
        source: SourceId? = null,
        trigger: RequestTrigger = RequestTrigger.AUTO,
    ): ApiResult<PriceAndRatios>

    /** Dividends from [source]. */
    suspend fun getDividends(
        symbol: Symbol,
        source: SourceId? = null,
        trigger: RequestTrigger = RequestTrigger.AUTO,
    ): ApiResult<Dividends>

    /** Balance sheet from [source]. */
    suspend fun getBalanceSheet(
        symbol: Symbol,
        source: SourceId? = null,
        trigger: RequestTrigger = RequestTrigger.AUTO,
    ): ApiResult<BalanceSheet>

    /** Company profile from [source], with the profile's user tags of the company whatever the status of the data. */
    suspend fun getCompanyProfile(
        symbol: Symbol,
        source: SourceId? = null,
        trigger: RequestTrigger = RequestTrigger.AUTO,
    ): ApiResult<CompanyProfileData>
}

@Singleton
class DefaultCompanyRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository,
) : CompanyRepository {

    override suspend fun getCompanySources(symbol: Symbol): ApiResult<CompanySources> =
        profileRepository.withActiveProfile { profileId -> apiClient.getCompanySources(profileId, symbol) }

    override suspend fun getPriceAndRatios(symbol: Symbol, source: SourceId?, trigger: RequestTrigger): ApiResult<PriceAndRatios> =
        profileRepository.withActiveProfile { profileId -> apiClient.getPriceAndRatios(profileId, symbol, source, trigger) }

    override suspend fun getDividends(symbol: Symbol, source: SourceId?, trigger: RequestTrigger): ApiResult<Dividends> =
        profileRepository.withActiveProfile { profileId -> apiClient.getDividends(profileId, symbol, source, trigger) }

    override suspend fun getBalanceSheet(symbol: Symbol, source: SourceId?, trigger: RequestTrigger): ApiResult<BalanceSheet> =
        profileRepository.withActiveProfile { profileId -> apiClient.getBalanceSheet(profileId, symbol, source, trigger) }

    override suspend fun getCompanyProfile(symbol: Symbol, source: SourceId?, trigger: RequestTrigger): ApiResult<CompanyProfileData> =
        profileRepository.withActiveProfile { profileId -> apiClient.getCompanyProfile(profileId, symbol, source, trigger) }
}
