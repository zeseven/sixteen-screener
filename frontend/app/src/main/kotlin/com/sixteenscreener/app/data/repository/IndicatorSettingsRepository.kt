package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.data.remote.ApiClient
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.IndicatorSetting
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indicator settings of the active profile. Nothing is kept in memory: the settings screen loads them each time it is opened.
 */
interface IndicatorSettingsRepository {
    /** Loads the settings with `GET /indicator-settings`, in settings order. */
    suspend fun getSettings(): ApiResult<List<IndicatorSetting>>

    /** Stores [settings] with `PUT /indicator-settings`; returns the stored settings. */
    suspend fun replaceSettings(settings: List<IndicatorSetting>): ApiResult<List<IndicatorSetting>>
}

@Singleton
class DefaultIndicatorSettingsRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository,
) : IndicatorSettingsRepository {

    override suspend fun getSettings(): ApiResult<List<IndicatorSetting>> = profileRepository.withActiveProfile { profileId ->
        apiClient.getIndicatorSettings(profileId).clearProfileIfNotFound(profileRepository, profileId)
    }

    override suspend fun replaceSettings(settings: List<IndicatorSetting>): ApiResult<List<IndicatorSetting>> =
        profileRepository.withActiveProfile { profileId ->
            apiClient.replaceIndicatorSettings(profileId, settings).clearProfileIfNotFound(profileRepository, profileId)
        }
}
