package com.sixteenscreener.app.ui.settings

import com.sixteenscreener.app.data.repository.DefaultWatchlistRepository
import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.AppInfo
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.testing.Companies.item
import com.sixteenscreener.app.testing.FakeApiClient
import com.sixteenscreener.app.testing.FakeProfileRepository
import com.sixteenscreener.app.testing.FakeWatchlistStore
import com.sixteenscreener.app.testing.MainDispatcherRule
import com.sixteenscreener.app.ui.profile.ProfileError
import com.sixteenscreener.app.ui.watchlist.WatchlistViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profileRepository = FakeProfileRepository(ActiveProfile(ProfileId("p1"), "Viktor"))
    private val appInfo = AppInfo(version = "0.1.0", backendUrl = "https://backend.example")

    private fun viewModel() = SettingsViewModel(profileRepository, appInfo)

    private val SettingsViewModel.state get() = uiState.value

    @Test
    fun `shows the current profile and app info`() = runTest {
        val viewModel = viewModel()

        assertEquals("Viktor", viewModel.state.currentProfileName)
        assertEquals(appInfo, viewModel.state.appInfo)
        assertFalse(viewModel.state.isSwitched)
    }

    @Test
    fun `switch to an existing name selects its profile`() = runTest {
        profileRepository.selectResult = { ApiResult.Success(ActiveProfile(ProfileId("existing-id"), "Anna")) }
        val viewModel = viewModel()

        viewModel.onNewProfileNameChange("  anna ")
        viewModel.onSwitchProfile()

        assertEquals(listOf("anna"), profileRepository.selectedNames)
        assertEquals(ProfileId("existing-id"), profileRepository.currentProfile.value?.id)
        assertEquals("Anna", viewModel.state.currentProfileName)
        assertTrue(viewModel.state.isSwitched)
        assertEquals("", viewModel.state.newProfileName)
    }

    @Test
    fun `switch to a new name creates and selects the profile`() = runTest {
        val viewModel = viewModel()

        viewModel.onNewProfileNameChange("Newcomer")
        viewModel.onSwitchProfile()

        assertEquals(ProfileId("id-Newcomer"), profileRepository.currentProfile.value?.id)
        assertTrue(viewModel.state.isSwitched)

        viewModel.onSwitchHandled()
        assertFalse(viewModel.state.isSwitched)
    }

    @Test
    fun `blank name is rejected without a request`() = runTest {
        val viewModel = viewModel()

        viewModel.onNewProfileNameChange("   ")
        viewModel.onSwitchProfile()

        assertEquals(ProfileError.NameBlank, viewModel.state.error)
        assertTrue(profileRepository.selectedNames.isEmpty())
        assertFalse(viewModel.state.isSwitched)
    }

    @Test
    fun `too long name is rejected without a request`() = runTest {
        val viewModel = viewModel()

        viewModel.onNewProfileNameChange("a".repeat(41))
        viewModel.onSwitchProfile()

        assertEquals(ProfileError.NameTooLong, viewModel.state.error)
        assertTrue(profileRepository.selectedNames.isEmpty())
    }

    @Test
    fun `failure keeps the current profile and shows the error`() = runTest {
        profileRepository.selectResult = { ApiResult.Failure(ApiError.Network(isTimeout = false)) }
        val viewModel = viewModel()

        viewModel.onNewProfileNameChange("Anna")
        viewModel.onSwitchProfile()

        assertEquals(ProfileId("p1"), profileRepository.currentProfile.value?.id)
        assertEquals("Viktor", viewModel.state.currentProfileName)
        assertEquals(ProfileError.Request(ApiError.Network(isTimeout = false)), viewModel.state.error)
        assertFalse(viewModel.state.isSwitching)
        assertFalse(viewModel.state.isSwitched)
        assertEquals("Anna", viewModel.state.newProfileName)
    }

    @Test
    fun `editing the name clears the error`() = runTest {
        val viewModel = viewModel()
        viewModel.onSwitchProfile()

        viewModel.onNewProfileNameChange("A")

        assertNull(viewModel.state.error)
    }

    @Test
    fun `second switch while one is running is ignored`() = runTest {
        val response = CompletableDeferred<ApiResult<ActiveProfile>>()
        profileRepository.selectResult = { response.await() }
        val viewModel = viewModel()
        viewModel.onNewProfileNameChange("Anna")

        viewModel.onSwitchProfile()
        viewModel.onSwitchProfile()
        assertTrue(viewModel.state.isSwitching)
        response.complete(ApiResult.Success(ActiveProfile(ProfileId("p2"), "Anna")))

        assertEquals(listOf("Anna"), profileRepository.selectedNames)
        assertTrue(viewModel.state.isSwitched)
    }

    @Test
    fun `watchlist reloads with the new profile ID after a switch`() = runTest {
        val apiClient = FakeApiClient()
        val viewerWatchlist = listOf(item("AAPL"))
        val annaWatchlist = listOf(item("SAP.DE", name = "SAP SE"))
        apiClient.getWatchlist = { profileId -> ApiResult.Success(if (profileId == ProfileId("p1")) viewerWatchlist else annaWatchlist) }
        profileRepository.selectResult = { ApiResult.Success(ActiveProfile(ProfileId("p2"), "Anna")) }
        val watchlistRepository = DefaultWatchlistRepository(apiClient, profileRepository, FakeWatchlistStore(), backgroundScope)
        val watchlistViewModel = WatchlistViewModel(watchlistRepository, profileRepository)
        watchlistViewModel.refresh()
        val viewModel = viewModel()

        viewModel.onNewProfileNameChange("Anna")
        viewModel.onSwitchProfile()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("getWatchlist(p1)", "getWatchlist(p2)"), apiClient.calls)
        assertEquals(annaWatchlist, watchlistViewModel.uiState.value.companies)
    }
}
