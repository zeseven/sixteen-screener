package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.testing.FakeApiClient
import com.sixteenscreener.app.testing.FakeProfileRepository
import com.sixteenscreener.app.testing.IndicatorSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultIndicatorSettingsRepositoryTest {

    private val profile = ActiveProfile(ProfileId("p1"), "Viktor")
    private val apiClient = FakeApiClient()
    private val profileRepository = FakeProfileRepository(profile)
    private val repository = DefaultIndicatorSettingsRepository(apiClient, profileRepository)

    @Test
    fun `settings are loaded for the active profile`() = runTest {
        apiClient.getIndicatorSettings = { ApiResult.Success(IndicatorSettings.all) }

        assertEquals(ApiResult.Success(IndicatorSettings.all), repository.getSettings())
        assertEquals(listOf("getIndicatorSettings(p1)"), apiClient.calls)
    }

    @Test
    fun `settings are replaced for the active profile`() = runTest {
        apiClient.replaceIndicatorSettings = { _, settings -> ApiResult.Success(settings) }

        assertEquals(ApiResult.Success(IndicatorSettings.all), repository.replaceSettings(IndicatorSettings.all))
        assertEquals(listOf("replaceIndicatorSettings(p1, PE,ROA,ROE)"), apiClient.calls)
    }

    @Test
    fun `without a profile nothing is requested`() = runTest {
        profileRepository.currentProfile.value = null

        assertEquals(ApiResult.Failure(ApiError.NoProfile), repository.getSettings())
        assertEquals(ApiResult.Failure(ApiError.NoProfile), repository.replaceSettings(IndicatorSettings.all))
        assertEquals(emptyList<String>(), apiClient.calls)
    }

    @Test
    fun `404 clears the removed profile`() = runTest {
        apiClient.getIndicatorSettings = { ApiResult.Failure(ApiError.NotFound(null)) }

        repository.getSettings()

        assertNull(profileRepository.currentProfile.value)
    }

    @Test
    fun `404 of a save clears the removed profile`() = runTest {
        apiClient.replaceIndicatorSettings = { _, _ -> ApiResult.Failure(ApiError.NotFound(null)) }

        repository.replaceSettings(IndicatorSettings.all)

        assertNull(profileRepository.currentProfile.value)
    }

    @Test
    fun `other failures keep the profile`() = runTest {
        apiClient.replaceIndicatorSettings = { _, _ -> ApiResult.Failure(ApiError.Http(400, null)) }

        repository.replaceSettings(IndicatorSettings.all)

        assertEquals(profile, profileRepository.currentProfile.value)
    }
}
