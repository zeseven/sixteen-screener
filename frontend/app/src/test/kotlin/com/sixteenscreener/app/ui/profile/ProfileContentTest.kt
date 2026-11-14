package com.sixteenscreener.app.ui.profile

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ProblemDetails
import com.sixteenscreener.app.ui.theme.SixteenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        uiState: ProfileUiState,
        onNameChange: (String) -> Unit = {},
        onContinue: () -> Unit = {},
    ) = composeRule.setContent {
        SixteenTheme(dynamicColor = false) {
            ProfileContent(uiState = uiState, onNameChange = onNameChange, onContinue = onContinue)
        }
    }

    @Test
    fun `typing and continue are forwarded`() {
        val typed = mutableListOf<String>()
        var continued = 0
        setContent(ProfileUiState(), onNameChange = { typed += it }, onContinue = { continued++ })

        composeRule.onNodeWithTag(ProfileScreenTags.NAME_FIELD).performTextInput("Viktor")
        composeRule.onNodeWithTag(ProfileScreenTags.CONTINUE_BUTTON).performClick()

        assertEquals(listOf("Viktor"), typed)
        assertEquals(1, continued)
    }

    @Test
    fun `network error is shown and continue stays enabled for retry`() {
        setContent(ProfileUiState(name = "Viktor", error = ProfileError.Request(ApiError.Network(isTimeout = true))))

        composeRule.onNodeWithText("Cannot reach server. Check connection").assertExists()
        composeRule.onNodeWithTag(ProfileScreenTags.CONTINUE_BUTTON).assertIsEnabled()
    }

    @Test
    fun `backend problem detail is shown`() {
        val problem = ProblemDetails(type = null, title = "Bad Request", status = 400, detail = "Name is invalid")
        setContent(ProfileUiState(name = "Viktor", error = ProfileError.Request(ApiError.Http(400, problem))))

        composeRule.onNodeWithText("Name is invalid").assertExists()
    }

    @Test
    fun `validation errors are shown`() {
        setContent(ProfileUiState(name = "a".repeat(41), error = ProfileError.NameTooLong))

        composeRule.onNodeWithText("Use at most 40 characters").assertExists()
    }

    @Test
    fun `continue is disabled while submitting`() {
        setContent(ProfileUiState(name = "Viktor", isSubmitting = true))

        composeRule.onNodeWithTag(ProfileScreenTags.CONTINUE_BUTTON).assertIsNotEnabled()
    }
}
