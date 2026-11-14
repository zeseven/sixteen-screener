package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.data.local.ProfileStore
import com.sixteenscreener.app.domain.ProfileName
import com.sixteenscreener.app.domain.ProfileNameValidation
import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.Profile
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.testing.FakeApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultProfileRepositoryTest {

    private val profileId = ProfileId("3f2b8c1e-6d4a-4f7e-9b1a-2c5d8e7f9a10")
    private val apiClient = FakeApiClient()
    private val store = InMemoryProfileStore()
    private val repository = DefaultProfileRepository(apiClient, store)

    private fun name(raw: String) = (ProfileName.parse(raw) as ProfileNameValidation.Valid).name

    @Test
    fun `no profile on first start`() = runTest {
        assertNull(repository.currentProfile.first())
    }

    @Test
    fun `stored profile is reused without a network call`() = runTest {
        val stored = ActiveProfile(profileId, "Viktor")
        store.save(stored)

        assertEquals(stored, repository.currentProfile.first())
        assertTrue(apiClient.calls.isEmpty())
    }

    @Test
    fun `selecting a profile sends the trimmed name and stores ID and name`() = runTest {
        apiClient.getOrCreateProfile = { ApiResult.Success(Profile(profileId, it, SourceId.FINNHUB)) }

        val result = repository.selectProfile(name("  Viktor "))

        val expected = ActiveProfile(profileId, "Viktor")
        assertEquals(ApiResult.Success(expected), result)
        assertEquals(listOf("getOrCreateProfile(Viktor)"), apiClient.calls)
        assertEquals(expected, repository.currentProfile.first())
    }

    @Test
    fun `stored name is the one returned by the backend`() = runTest {
        apiClient.getOrCreateProfile = { ApiResult.Success(Profile(profileId, "Viktor", SourceId.FINNHUB)) }

        repository.selectProfile(name("viktor"))

        assertEquals("Viktor", repository.currentProfile.first()?.name)
    }

    @Test
    fun `failed selection keeps the stored profile`() = runTest {
        val stored = ActiveProfile(profileId, "Viktor")
        store.save(stored)
        val failure = ApiResult.Failure(ApiError.Network(isTimeout = true))
        apiClient.getOrCreateProfile = { failure }

        val result = repository.selectProfile(name("Anna"))

        assertEquals(failure, result)
        assertEquals(stored, repository.currentProfile.first())
    }

    @Test
    fun `profile not found clears the stored profile`() = runTest {
        store.save(ActiveProfile(profileId, "Viktor"))

        repository.onProfileNotFound(profileId)

        assertNull(repository.currentProfile.first())
    }

    @Test
    fun `profile not found for an older ID keeps the newly stored profile`() = runTest {
        val stored = ActiveProfile(profileId, "Viktor")
        store.save(stored)

        repository.onProfileNotFound(ProfileId("older"))

        assertEquals(stored, repository.currentProfile.first())
    }

    @Test
    fun `not found result of a profile endpoint clears the stored profile and is returned unchanged`() = runTest {
        store.save(ActiveProfile(profileId, "Viktor"))
        val notFound = ApiResult.Failure(ApiError.NotFound(problem = null))

        val result = notFound.clearProfileIfNotFound(repository, profileId)

        assertEquals(notFound, result)
        assertNull(repository.currentProfile.first())
    }

    @Test
    fun `other failures and successes keep the stored profile`() = runTest {
        val stored = ActiveProfile(profileId, "Viktor")
        store.save(stored)

        ApiResult.Failure(ApiError.Http(500, problem = null)).clearProfileIfNotFound(repository, profileId)
        ApiResult.Failure(ApiError.Network(isTimeout = false)).clearProfileIfNotFound(repository, profileId)
        ApiResult.Success(Unit).clearProfileIfNotFound(repository, profileId)

        assertEquals(stored, repository.currentProfile.first())
    }

    private class InMemoryProfileStore : ProfileStore {
        override val profile = MutableStateFlow<ActiveProfile?>(null)

        override suspend fun save(profile: ActiveProfile) {
            this.profile.value = profile
        }

        override suspend fun clear(profileId: ProfileId) {
            if (profile.value?.id == profileId) profile.value = null
        }
    }
}
