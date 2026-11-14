package com.sixteenscreener.app.ui.settings

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.AppInfo
import com.sixteenscreener.app.ui.profile.ProfileError
import com.sixteenscreener.app.ui.theme.SixteenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private fun setContent(uiState: SettingsUiState) = composeRule.setContent {
        SixteenTheme(dynamicColor = false) {
            SettingsContent(
                uiState = uiState,
                onBack = { events += "back" },
                onNewProfileNameChange = { events += "name $it" },
                onSwitchProfile = { events += "switch" },
            )
        }
    }

    private fun state(
        currentProfileName: String? = "Viktor",
        isSwitching: Boolean = false,
        error: ProfileError? = null,
    ) = SettingsUiState(
        currentProfileName = currentProfileName,
        isSwitching = isSwitching,
        error = error,
        appInfo = AppInfo(version = "0.1.0", backendUrl = "https://backend.example"),
    )

    private fun tagged(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    @Test
    fun `shows the current profile, version and backend URL`() {
        setContent(state())

        tagged(SettingsScreenTags.CURRENT_PROFILE).assertTextEquals("Viktor")
        tagged(SettingsScreenTags.VERSION).assertTextEquals("0.1.0")
        tagged(SettingsScreenTags.BACKEND_URL).assertTextEquals("https://backend.example")
    }

    @Test
    fun `unknown current profile is shown as a dash`() {
        setContent(state(currentProfileName = null))

        tagged(SettingsScreenTags.CURRENT_PROFILE).assertTextEquals("-")
    }

    @Test
    fun `typing and switching are passed up`() {
        setContent(state())

        tagged(SettingsScreenTags.NAME_FIELD).performTextInput("Anna")
        tagged(SettingsScreenTags.SWITCH_BUTTON).performClick()

        assertEquals(listOf("name Anna", "switch"), events)
    }

    @Test
    fun `validation error is shown`() {
        setContent(state(error = ProfileError.NameBlank))
        composeRule.onNodeWithText("Enter a profile name").assertExists()
    }

    @Test
    fun `request error is shown`() {
        setContent(state(error = ProfileError.Request(ApiError.Network(isTimeout = false))))
        composeRule.onNodeWithText("Cannot reach server. Check connection").assertExists()
    }

    @Test
    fun `switch button is disabled while switching`() {
        setContent(state(isSwitching = true))

        tagged(SettingsScreenTags.SWITCH_BUTTON).assertIsNotEnabled()
    }
}
