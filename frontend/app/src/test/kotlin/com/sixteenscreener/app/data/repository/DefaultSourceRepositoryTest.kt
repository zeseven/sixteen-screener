package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.Capability
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.Source
import com.sixteenscreener.app.testing.FakeApiClient
import com.sixteenscreener.app.testing.FakeProfileRepository
import com.sixteenscreener.app.testing.Sources.source
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSourceRepositoryTest {

    private val apiClient = FakeApiClient()
    private val profileRepository = FakeProfileRepository(ActiveProfile(ProfileId("p1"), "Viktor"))

    private val finnhub = source("finnhub", "Finnhub", isDefault = true)
    private val tiingo = source("tiingo", "Tiingo", capabilities = listOf(Capability.QUOTE))

    private fun TestScope.repository() = DefaultSourceRepository(apiClient, profileRepository, backgroundScope)

    private fun runRepositoryTest(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher()) { block() }

    private fun stubSources(vararg sources: Source) {
        apiClient.getSources = { ApiResult.Success(sources.toList()) }
    }

    @Test
    fun `refresh caches the sources`() = runRepositoryTest {
        stubSources(finnhub, tiingo)
        val repository = repository()

        repository.refresh()

        assertEquals(listOf(finnhub, tiingo), repository.sources.value)
        assertEquals(listOf("getSources(p1)"), apiClient.calls)
    }

    @Test
    fun `failed refresh keeps the cached sources`() = runRepositoryTest {
        stubSources(finnhub)
        val repository = repository()
        repository.refresh()
        apiClient.getSources = { ApiResult.Failure(ApiError.Network(isTimeout = false)) }

        repository.refresh()

        assertEquals(listOf(finnhub), repository.sources.value)
    }

    @Test
    fun `not found clears the stored profile`() = runRepositoryTest {
        apiClient.getSources = { ApiResult.Failure(ApiError.NotFound(null)) }

        repository().refresh()

        assertNull(profileRepository.currentProfile.value)
    }

    @Test
    fun `profile change drops the cached sources`() = runRepositoryTest {
        stubSources(finnhub)
        val repository = repository()
        repository.refresh()

        profileRepository.currentProfile.value = ActiveProfile(ProfileId("p2"), "Other")

        assertNull(repository.sources.value)
    }
}
