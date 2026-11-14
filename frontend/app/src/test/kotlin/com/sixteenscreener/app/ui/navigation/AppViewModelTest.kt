package com.sixteenscreener.app.ui.navigation

import com.sixteenscreener.app.data.repository.ProfileRepository
import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.testing.FakeProfileRepository
import com.sixteenscreener.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AppViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profile = ActiveProfile(ProfileId("id"), "Viktor")

    @Test
    fun `unknown until storage is read`() {
        val repository = object : ProfileRepository by FakeProfileRepository() {
            override val currentProfile = MutableSharedFlow<ActiveProfile?>()
        }

        assertNull(AppViewModel(repository).hasProfile.value)
    }

    @Test
    fun `no stored profile`() {
        assertEquals(false, AppViewModel(FakeProfileRepository()).hasProfile.value)
    }

    @Test
    fun `stored profile`() {
        assertEquals(true, AppViewModel(FakeProfileRepository(profile)).hasProfile.value)
    }

    @Test
    fun `follows selection and removal of the profile`() = runTest {
        val repository = FakeProfileRepository()
        val viewModel = AppViewModel(repository)

        repository.currentProfile.value = profile
        assertEquals(true, viewModel.hasProfile.value)

        repository.onProfileNotFound(profile.id)
        assertEquals(false, viewModel.hasProfile.value)
    }
}
