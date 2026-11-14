package com.sixteenscreener.app.ui.profile

import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.ProblemDetails
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.testing.FakeProfileRepository
import com.sixteenscreener.app.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeProfileRepository()
    private val viewModel = ProfileViewModel(repository)

    private fun submit(name: String) {
        viewModel.onNameChange(name)
        viewModel.onContinue()
    }

    @Test
    fun `valid name selects the trimmed profile and stores it`() = runTest {
        submit("  Viktor  ")

        assertEquals(listOf("Viktor"), repository.selectedNames)
        assertEquals(ActiveProfile(ProfileId("id-Viktor"), "Viktor"), repository.currentProfile.value)
        assertEquals(ProfileUiState(name = "  Viktor  "), viewModel.uiState.value)
    }

    @Test
    fun `empty name is rejected without a request`() {
        submit("")

        assertEquals(ProfileError.NameBlank, viewModel.uiState.value.error)
        assertTrue(repository.selectedNames.isEmpty())
    }

    @Test
    fun `whitespace only name is rejected without a request`() {
        submit("   ")

        assertEquals(ProfileError.NameBlank, viewModel.uiState.value.error)
        assertTrue(repository.selectedNames.isEmpty())
    }

    @Test
    fun `forty one characters are rejected without a request`() {
        submit("a".repeat(41))

        assertEquals(ProfileError.NameTooLong, viewModel.uiState.value.error)
        assertTrue(repository.selectedNames.isEmpty())
    }

    @Test
    fun `forty characters and one character are accepted`() {
        submit("a".repeat(40))
        submit("b")

        assertEquals(listOf("a".repeat(40), "b"), repository.selectedNames)
    }

    @Test
    fun `editing the name clears the error`() {
        submit("")

        viewModel.onNameChange("V")

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `submitting state lasts until the backend answers`() = runTest {
        val response = CompletableDeferred<ApiResult<ActiveProfile>>()
        repository.selectResult = { response.await() }

        submit("Viktor")
        assertTrue(viewModel.uiState.value.isSubmitting)

        viewModel.onContinue()
        assertEquals(1, repository.selectedNames.size)

        response.complete(ApiResult.Success(ActiveProfile(ProfileId("id"), "Viktor")))
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `network error is shown and retry succeeds`() = runTest {
        val failure = ApiError.Network(isTimeout = false)
        repository.selectResult = { ApiResult.Failure(failure) }

        submit("Viktor")

        assertEquals(ProfileError.Request(failure), viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertNull(repository.currentProfile.value)

        repository.selectResult = { ApiResult.Success(ActiveProfile(ProfileId("id"), it.value)) }
        viewModel.onContinue()

        assertNull(viewModel.uiState.value.error)
        assertEquals(ActiveProfile(ProfileId("id"), "Viktor"), repository.currentProfile.value)
    }

    @Test
    fun `backend error keeps the problem details`() = runTest {
        val failure = ApiError.Http(400, ProblemDetails(type = null, title = "Bad Request", status = 400, detail = "Name is invalid"))
        repository.selectResult = { ApiResult.Failure(failure) }

        submit("Viktor")

        assertEquals(ProfileError.Request(failure), viewModel.uiState.value.error)
    }
}
