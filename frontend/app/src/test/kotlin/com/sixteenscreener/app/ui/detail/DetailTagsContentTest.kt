package com.sixteenscreener.app.ui.detail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.CompanyProfileData
import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.testing.Companies
import com.sixteenscreener.app.testing.CompanyData
import com.sixteenscreener.app.testing.MutableClock
import com.sixteenscreener.app.ui.component.LocalClock
import com.sixteenscreener.app.ui.detail.profile.AddTagDialogTags
import com.sixteenscreener.app.ui.detail.profile.CompanyProfileTags
import com.sixteenscreener.app.ui.detail.profile.CompanyTagsTags
import com.sixteenscreener.app.ui.tag.TagIconRowTags
import com.sixteenscreener.app.ui.theme.SixteenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class DetailTagsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val apple = Companies.identity("AAPL", name = "Apple Inc.")
    private val king = Tag("chess-king", "Dividend king")
    private val income = Tag("coin", "Income")
    private val events = mutableListOf<String>()
    private var uiState by mutableStateOf(DetailUiState(symbol = apple.symbol))

    private fun setContent(state: DetailUiState) {
        uiState = state
        composeRule.setContent {
            SixteenTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalClock provides MutableClock(CompanyData.FETCHED_AT + Duration.ofHours(2))) {
                    DetailContent(
                        uiState = uiState,
                        onBack = {},
                        onRefresh = {},
                        onTabSelected = {},
                        onSourceSelected = { _, _ -> },
                        onToggleWatchlist = {},
                        onMessageShown = { events += "shown" },
                        tagActions = TagActions(
                            onAddRequested = { events += "add requested" },
                            onAddDismissed = { events += "add dismissed" },
                            onAdd = { icon, text -> events += "add $icon $text" },
                            onRemove = { events += "remove ${it.text}" },
                        ),
                    )
                }
            }
        }
    }

    private fun state(
        tags: TagsUiState = TagsUiState(),
        selectedTab: DetailTab = DetailTab.COMPANY_PROFILE,
        companyProfile: TabData<CompanyProfileData> = TabData(CompanyData.companyProfileData()),
    ) = DetailUiState(
        symbol = apple.symbol,
        header = apple,
        inWatchlist = tags.isEditable,
        selectedTab = selectedTab,
        companyProfile = SourceTabState(data = companyProfile),
        tags = tags,
    )

    private fun tagged(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    @Test
    fun `header shows the icons of the tags with their texts for screen readers`() {
        setContent(state(TagsUiState(listOf(king, income), isEditable = true), selectedTab = DetailTab.PRICE_AND_RATIOS))

        tagged(TagIconRowTags.ROW).assertContentDescriptionEquals("Tags: Dividend king, Income")
    }

    @Test
    fun `header shows no tag icons without tags`() {
        setContent(state(selectedTab = DetailTab.PRICE_AND_RATIOS))

        tagged(TagIconRowTags.ROW).assertDoesNotExist()
    }

    @Test
    fun `tag icons don't make the type row taller and end at its right edge`() {
        val many = (1..20).map { Tag("star", "Tag $it") }
        setContent(state(TagsUiState(many, isEditable = true), selectedTab = DetailTab.PRICE_AND_RATIOS))

        val type = tagged(DetailScreenTags.HEADER_TYPE).getUnclippedBoundsInRoot()
        val icons = tagged(TagIconRowTags.ROW).getUnclippedBoundsInRoot()
        assertTrue(icons.bottom - icons.top <= type.bottom - type.top)
        assertTrue(icons.left >= type.right)
        assertEquals((composeRule.onRoot().getUnclippedBoundsInRoot().right - 16.dp).value, icons.right.value, 0.5f)
    }

    @Test
    fun `tags are the first section of the profile tab with icon and text`() {
        setContent(state(TagsUiState(listOf(king, income), isEditable = true)))

        tagged(CompanyTagsTags.TITLE).assertTextEquals("Tags").assert(isHeading())
        val tags = composeRule.onAllNodesWithTag(CompanyTagsTags.TAG)
        tags.assertCountEquals(2)
        tags[0].assertContentDescriptionEquals("Chess king").assertTextEquals("Dividend king")
        tags[1].assertContentDescriptionEquals("Coin").assertTextEquals("Income")
        assertTrue(
            tagged(CompanyTagsTags.TITLE).getUnclippedBoundsInRoot().top <
                composeRule.onAllNodesWithTag(CompanyProfileTags.FIELD)[0].getUnclippedBoundsInRoot().top,
        )
    }

    @Test
    fun `tag with an unknown icon is shown with the fallback icon`() {
        setContent(state(TagsUiState(listOf(Tag("crown", "Royal")), isEditable = true)))

        composeRule.onNodeWithTag(CompanyTagsTags.TAG).assertContentDescriptionEquals("Unknown icon").assertTextEquals("Royal")
    }

    @Test
    fun `tags are shown and editable when the profile request failed without data`() {
        setContent(state(TagsUiState(listOf(income), isEditable = true), companyProfile = TabData(error = ApiError.Network(isTimeout = false))))

        composeRule.onNodeWithTag(CompanyTagsTags.TAG).assertTextEquals("Income")
        tagged(CompanyTagsTags.ADD).assertIsEnabled()
        composeRule.onNodeWithText("Cannot reach server. Check connection").assertExists()
    }

    @Test
    fun `tags are shown and editable when the source doesn't support profiles`() {
        val notSupported = CompanyData.companyProfileData(profile = null, status = DataStatus.NOT_SUPPORTED)
        setContent(state(TagsUiState(listOf(income), isEditable = true), companyProfile = TabData(notSupported)))

        composeRule.onNodeWithTag(CompanyTagsTags.TAG).assertTextEquals("Income")
        tagged(CompanyTagsTags.ADD).assertIsEnabled()
    }

    @Test
    fun `company outside the watchlist explains why it can't be tagged`() {
        setContent(state(TagsUiState(isEditable = false)))

        tagged(CompanyTagsTags.HINT).assertTextEquals("Add the company to the watchlist to tag it")
        tagged(CompanyTagsTags.ADD).assertDoesNotExist()
        tagged(CompanyTagsTags.REMOVE).assertDoesNotExist()
    }

    @Test
    fun `company without tags shows a hint`() {
        setContent(state(TagsUiState(isEditable = true)))

        tagged(CompanyTagsTags.EMPTY).assertTextEquals("No tags yet")
    }

    @Test
    fun `add and remove are passed up`() {
        setContent(state(TagsUiState(listOf(king, income), isEditable = true)))

        tagged(CompanyTagsTags.ADD).performClick()
        composeRule.onNodeWithContentDescription("Remove tag Income").performClick()

        assertEquals(listOf("add requested", "remove Income"), events)
    }

    @Test
    fun `20 tags disable adding with an explanation`() {
        setContent(state(TagsUiState((1..20).map { Tag("star", "Tag $it") }, isEditable = true)))

        tagged(CompanyTagsTags.ADD).assertIsNotEnabled()
        tagged(CompanyTagsTags.HINT).assertTextEquals("A company can have at most 20 tags")
    }

    @Test
    fun `saving disables the buttons and shows progress`() {
        setContent(state(TagsUiState(listOf(king), isEditable = true, isSaving = true)))

        tagged(CompanyTagsTags.ADD).assertIsNotEnabled()
        tagged(CompanyTagsTags.REMOVE).assertIsNotEnabled()
        tagged(CompanyTagsTags.PROGRESS).assertContentDescriptionEquals("Saving tags")
    }

    @Test
    fun `dialog adds a tag once an icon is picked and a text is entered`() {
        setContent(state(TagsUiState(listOf(income), isEditable = true, isAddDialogOpen = true)))
        val confirm = composeRule.onNodeWithTag(AddTagDialogTags.CONFIRM)
        confirm.assertIsNotEnabled()

        composeRule.onNodeWithTag(AddTagDialogTags.TEXT_FIELD).performTextInput("Dividend king")
        confirm.assertIsNotEnabled()
        composeRule.onNodeWithTag(AddTagDialogTags.icon("chess-king")).performScrollTo().performClick().assertIsSelected()
        confirm.assertIsEnabled().performClick()

        assertEquals(listOf("add chess-king Dividend king"), events)
    }

    @Test
    fun `dialog reports a duplicate tag and doesn't allow adding it`() {
        setContent(state(TagsUiState(listOf(income), isEditable = true, isAddDialogOpen = true)))

        composeRule.onNodeWithTag(AddTagDialogTags.TEXT_FIELD).performTextInput("INCOME")
        composeRule.onNodeWithTag(AddTagDialogTags.icon("coin")).performScrollTo().performClick()

        composeRule.onNodeWithTag(AddTagDialogTags.PROBLEM, useUnmergedTree = true).assertTextEquals("The company already has this tag")
        composeRule.onNodeWithTag(AddTagDialogTags.CONFIRM).assertIsNotEnabled()
    }

    @Test
    fun `dialog text is limited to 100 characters`() {
        setContent(state(TagsUiState(isEditable = true, isAddDialogOpen = true)))

        composeRule.onNodeWithTag(AddTagDialogTags.TEXT_FIELD).performTextInput("a".repeat(120))

        composeRule.onNodeWithTag(AddTagDialogTags.TEXT_FIELD).assertTextContains("a".repeat(100))
        composeRule.onNodeWithTag(AddTagDialogTags.PROBLEM, useUnmergedTree = true).assertTextEquals("100 / 100")
    }

    @Test
    fun `picking a tag in use fills the icon and the text`() {
        setContent(state(TagsUiState(isEditable = true, isAddDialogOpen = true, suggestions = listOf(king))))

        composeRule.onNodeWithContentDescription("Use tag Dividend king").performClick()
        composeRule.onNodeWithTag(AddTagDialogTags.CONFIRM).performClick()

        assertEquals(listOf("add chess-king Dividend king"), events)
    }

    @Test
    fun `dialog shows a failed save and can be cancelled`() {
        setContent(state(TagsUiState(isEditable = true, isAddDialogOpen = true, dialogError = ApiError.Network(isTimeout = false))))

        composeRule.onNodeWithTag(AddTagDialogTags.ERROR).performScrollTo()
            .assertTextEquals("Cannot save tags: Cannot reach server. Check connection")
        composeRule.onNodeWithTag(AddTagDialogTags.CANCEL).performClick()

        assertEquals(listOf("add dismissed"), events)
    }
}
