package com.sixteenscreener.app.ui.detail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixteenscreener.app.domain.BalanceSheetTable
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.BalanceSheet
import com.sixteenscreener.app.domain.model.CompanyProfileData
import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.Dividends
import com.sixteenscreener.app.domain.model.PriceAndRatios
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.SourceOption
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.testing.AccessibilityMatchers
import com.sixteenscreener.app.testing.Companies
import com.sixteenscreener.app.testing.CompanyData
import com.sixteenscreener.app.testing.CompanyData.item
import com.sixteenscreener.app.testing.CompanyData.period
import com.sixteenscreener.app.testing.MutableClock
import com.sixteenscreener.app.ui.component.CompanyRowTags
import com.sixteenscreener.app.ui.component.LocalClock
import com.sixteenscreener.app.ui.detail.balancesheet.BalanceSheetTags
import com.sixteenscreener.app.ui.detail.dividends.DividendsTags
import com.sixteenscreener.app.ui.detail.priceratios.PriceAndRatiosTags
import com.sixteenscreener.app.ui.detail.profile.CompanyProfileTags
import com.sixteenscreener.app.ui.theme.LightColorScheme
import com.sixteenscreener.app.ui.theme.SixteenTheme
import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clock = MutableClock(CompanyData.FETCHED_AT + Duration.ofHours(2))
    private val apple = Companies.identity("AAPL", name = "Apple Inc.")
    private val sourceNames = mapOf(SourceId.FINNHUB to "Finnhub", SourceId.SEC_EDGAR to "SEC EDGAR")

    private val events = mutableListOf<String>()
    private val weakColor = LightColorScheme.onSurfaceVariant
    private val errorColor = LightColorScheme.error
    private var uiState by mutableStateOf(DetailUiState(symbol = apple.symbol))

    private fun setContent(state: DetailUiState) {
        uiState = state
        composeRule.setContent {
            SixteenTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalClock provides clock) {
                    DetailContent(
                        uiState = uiState,
                        onBack = { events += "back" },
                        onRefresh = { events += "refresh" },
                        onTabSelected = { events += "tab $it" },
                        onSourceSelected = { tab, source -> events += "source $tab ${source.value}" },
                        onToggleWatchlist = { events += "toggle" },
                        onMessageShown = { events += "shown" },
                    )
                }
            }
        }
    }

    private fun state(
        selectedTab: DetailTab = DetailTab.PRICE_AND_RATIOS,
        priceAndRatios: TabData<PriceAndRatios> = TabData(CompanyData.priceAndRatios()),
        dividends: TabData<Dividends> = TabData(),
        balanceSheet: SourceTabState<BalanceSheetView> = SourceTabState(),
        companyProfile: TabData<CompanyProfileData> = TabData(),
        dividendOptions: List<SourceOption> = emptyList(),
        profileOptions: List<SourceOption> = emptyList(),
    ) = DetailUiState(
        symbol = apple.symbol,
        header = apple,
        headerQuote = CompanyData.priceAndRatios().quote,
        selectedTab = selectedTab,
        priceAndRatios = SourceTabState(data = priceAndRatios),
        dividends = SourceTabState(options = dividendOptions, selectedSource = dividendOptions.firstOrNull()?.id, data = dividends),
        balanceSheet = balanceSheet,
        companyProfile = SourceTabState(options = profileOptions, selectedSource = profileOptions.firstOrNull()?.id, data = companyProfile),
        sourceNames = sourceNames,
    )

    private val finnhubOption = SourceOption(SourceId.FINNHUB, "Finnhub", isDefault = true, automaticRequests = true)
    private val secEdgarOption = SourceOption(SourceId.SEC_EDGAR, "SEC EDGAR", isDefault = false, automaticRequests = true)
    private val yahooOption = SourceOption(SourceId("yahoo"), "Yahoo Finance", isDefault = false, automaticRequests = false)

    private fun balanceSheetState(sheet: BalanceSheet, selected: SourceId = sheet.meta.source) = SourceTabState(
        options = listOf(finnhubOption, secEdgarOption, yahooOption),
        selectedSource = selected,
        data = TabData(BalanceSheetView(sheet.meta, BalanceSheetTable.of(sheet.periods))),
    )

    private fun texts(tag: String, useUnmergedTree: Boolean = true): List<String> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree).fetchSemanticsNodes()
        .map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } }

    private fun tagged(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun SemanticsNodeInteraction.textColor(): Color {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.style.color
    }

    @Test
    fun `header shows the company row`() {
        setContent(state())

        composeRule.onNodeWithText("NASDAQ").assertExists()
        composeRule.onNodeWithContentDescription("Add Apple Inc. to the watchlist").performClick()
        assertEquals(listOf("toggle"), events)
    }

    @Test
    fun `type of a common stock is shown under the header in the weak color`() {
        setContent(state())

        tagged(DetailScreenTags.HEADER_TYPE).assertTextEquals("type: Common Stock")
        assertEquals(weakColor, tagged(DetailScreenTags.HEADER_TYPE).textColor())
    }

    @Test
    fun `type of a security that is not a stock is shown in the error color`() {
        setContent(state().copy(header = Companies.identity("NSRGY", type = "ADR", stock = false)))

        tagged(DetailScreenTags.HEADER_TYPE).assertTextEquals("type: ADR")
        assertEquals(errorColor, tagged(DetailScreenTags.HEADER_TYPE).textColor())
    }

    @Test
    fun `unknown type is shown in the weak color`() {
        setContent(state().copy(header = Companies.identity(type = null)))

        tagged(DetailScreenTags.HEADER_TYPE).assertTextEquals("type: unknown")
        assertEquals(weakColor, tagged(DetailScreenTags.HEADER_TYPE).textColor())
    }

    @Test
    fun `home exchange is marked after the type`() {
        setContent(state().copy(homeExchange = true))

        tagged(DetailScreenTags.HEADER_TYPE).assertTextEquals("type: Common Stock (\u2705 home exchange)")
    }

    @Test
    fun `home exchange mark appears only when the loaded profile reports it`() {
        setContent(state())
        tagged(DetailScreenTags.HEADER_TYPE).assertTextEquals("type: Common Stock")

        uiState = uiState.copy(homeExchange = true)

        tagged(DetailScreenTags.HEADER_TYPE).assertTextEquals("type: Common Stock (\u2705 home exchange)")
    }

    @Test
    fun `no type row while the company is unknown`() {
        setContent(DetailUiState(symbol = Symbol("MSFT")))

        tagged(DetailScreenTags.HEADER_TYPE).assertDoesNotExist()
    }

    @Test
    fun `header shows the price of the company`() {
        setContent(state())

        tagged(CompanyRowTags.PRICE).assertTextEquals("$189.84")
    }

    @Test
    fun `header shows a dash without a price`() {
        setContent(state().copy(headerQuote = null))

        tagged(CompanyRowTags.PRICE).assertTextEquals("-")
    }

    @Test
    fun `header shows the symbol while the company is unknown`() {
        setContent(DetailUiState(symbol = Symbol("MSFT")))

        tagged(DetailScreenTags.HEADER_SYMBOL).assertTextEquals("MSFT")
    }

    @Test
    fun `company in the watchlist offers removal`() {
        setContent(state().copy(inWatchlist = true))

        composeRule.onNodeWithContentDescription("Remove Apple Inc. from the watchlist").assertExists()
    }

    @Test
    fun `refresh and tab clicks are passed up`() {
        setContent(state())

        tagged(DetailScreenTags.REFRESH).performClick()
        composeRule.onNodeWithText("Balance sheet").performClick()

        assertEquals(listOf("refresh", "tab BALANCE_SHEET"), events)
    }

    @Test
    fun `source name and data age are shown`() {
        setContent(state())

        tagged(SourceDataFrameTags.SOURCE).assertTextEquals("Source: Finnhub, updated 2 hours ago")
    }

    @Test
    fun `source without fetch time shows only its name`() {
        val data = CompanyData.priceAndRatios(quote = null, ratios = emptyList(), status = DataStatus.NO_DATA)
        setContent(state(priceAndRatios = TabData(data.copy(meta = data.meta.copy(fetchedAt = null)))))

        tagged(SourceDataFrameTags.SOURCE).assertTextEquals("Source: Finnhub")
    }

    @Test
    fun `price with currency, cache indicator and ratios with missing values`() {
        setContent(state())

        tagged(PriceAndRatiosTags.PRICE).assertTextEquals("$189.84")
        tagged(PriceAndRatiosTags.CACHE_INDICATOR).assertExists()
        assertEquals(listOf("P/E, 31.25", "P/B, -"), texts(PriceAndRatiosTags.RATIO, useUnmergedTree = false))
    }

    @Test
    fun `every status is rendered with cached data`() {
        val expectedNotices = mapOf(
            DataStatus.FRESH to null,
            DataStatus.CACHED to null,
            DataStatus.LIMIT_REACHED to "Source request limit reached. Showing cached data if available.",
            DataStatus.SOURCE_ERROR to "Source is not available. Showing cached data if available.",
            DataStatus.NOT_SUPPORTED to "This source does not provide this data for the company.",
            DataStatus.NO_DATA to "The source has no data for this company.",
            DataStatus.NOT_REQUESTED to "No data yet. Tap refresh to load.",
            DataStatus.UNKNOWN to "Unknown data status.",
        )
        setContent(state())
        expectedNotices.forEach { (status, notice) ->
            uiState = state(priceAndRatios = TabData(CompanyData.priceAndRatios(status = status)))
            composeRule.waitForIdle()

            assertEquals(status.name, listOfNotNull(notice), texts(SourceDataFrameTags.STATUS_NOTICE))
            tagged(PriceAndRatiosTags.PRICE).assertTextEquals("$189.84")
        }
    }

    @Test
    fun `every status is rendered with empty data as dashes`() {
        setContent(state())
        DataStatus.entries.forEach { status ->
            uiState = state(
                priceAndRatios = TabData(CompanyData.priceAndRatios(quote = null, ratios = emptyList(), status = status)),
                companyProfile = TabData(CompanyData.companyProfileData(profile = null, status = status)),
            )
            composeRule.waitForIdle()
            tagged(PriceAndRatiosTags.PRICE).assertTextEquals("-")
            tagged(PriceAndRatiosTags.CACHE_INDICATOR).assertDoesNotExist()

            uiState = uiState.copy(selectedTab = DetailTab.COMPANY_PROFILE)
            composeRule.waitForIdle()
            assertEquals(status.name, List(9) { "-" }, texts(CompanyProfileTags.FIELD, useUnmergedTree = false).map { it.substringAfterLast(", ") })
            tagged(CompanyProfileTags.WEBSITE).assertTextEquals("-")
            tagged(CompanyProfileTags.DESCRIPTION).assertTextEquals("-")
        }
    }

    @Test
    fun `tabs are shown in the order price and ratio, dividends, balance sheet, company profile`() {
        setContent(state())

        val lefts = listOf("Price and ratio", "Dividends", "Balance sheet", "Company profile")
            .map { composeRule.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left }
        assertEquals(lefts.sorted(), lefts)
    }

    @Test
    fun `dividends tab shows the source, the yield and the dividends newest first`() {
        val dividends = CompanyData.dividends(
            CompanyData.dividend(LocalDate.of(2026, 8, 11), 0.26, paymentDate = LocalDate.of(2026, 8, 14)),
            CompanyData.dividend(LocalDate.of(2026, 5, 12), 0.25),
            dividendYield = 0.44,
        )
        setContent(state(selectedTab = DetailTab.DIVIDENDS, dividends = TabData(dividends)))

        tagged(SourceDataFrameTags.SOURCE).assertTextEquals("Source: Finnhub, updated 2 hours ago")
        tagged(DividendsTags.YIELD_VALUE).assertTextEquals("0.44")
        composeRule.onNodeWithText("Dividend yield, %", useUnmergedTree = true).assertExists()
        assertEquals(listOf("Ex-date 2026-08-11", "Ex-date 2026-05-12"), texts(DividendsTags.EX_DATE))
        assertEquals(listOf("paid 2026-08-14"), texts(DividendsTags.PAYMENT_DATE))
        assertEquals(listOf("$0.26", "$0.25"), texts(DividendsTags.AMOUNT))
        tagged(DividendsTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun `dividend is announced with its ex-date and amount`() {
        val dividends = CompanyData.dividends(CompanyData.dividend(LocalDate.of(2026, 5, 11), 2.35, currency = "EUR"))
        setContent(state(selectedTab = DetailTab.DIVIDENDS, dividends = TabData(dividends)))

        composeRule.onNodeWithTag(DividendsTags.DIVIDEND).assertContentDescriptionEquals("Ex-date 2026-05-11, 2.35 EUR")
    }

    @Test
    fun `dividends tab without dividends and yield shows dashes`() {
        setContent(state(selectedTab = DetailTab.DIVIDENDS, dividends = TabData(CompanyData.dividends(dividendYield = null))))

        tagged(DividendsTags.YIELD_VALUE).assertTextEquals("-")
        tagged(DividendsTags.EMPTY).assertTextEquals("-")
        tagged(DividendsTags.DIVIDEND).assertDoesNotExist()
    }

    @Test
    fun `dividends not supported by the source show the notice and dashes`() {
        val data = CompanyData.dividends(dividendYield = null, status = DataStatus.NOT_SUPPORTED)
        setContent(state(selectedTab = DetailTab.DIVIDENDS, dividends = TabData(data)))

        assertEquals(listOf("This source does not provide this data for the company."), texts(SourceDataFrameTags.STATUS_NOTICE))
        tagged(DividendsTags.EMPTY).assertTextEquals("-")
    }

    @Test
    fun `dividends not requested show the hint to refresh and dashes`() {
        val data = CompanyData.dividends(dividendYield = null, status = DataStatus.NOT_REQUESTED)
        setContent(state(selectedTab = DetailTab.DIVIDENDS, dividends = TabData(data)))

        assertEquals(listOf("No data yet. Tap refresh to load."), texts(SourceDataFrameTags.STATUS_NOTICE))
        tagged(DividendsTags.YIELD_VALUE).assertTextEquals("-")
        tagged(DividendsTags.EMPTY).assertTextEquals("-")
    }

    @Test
    fun `dividends loading without data shows progress only`() {
        setContent(state(selectedTab = DetailTab.DIVIDENDS, dividends = TabData(isLoading = true)))

        tagged(SourceDataFrameTags.PROGRESS).assertExists()
        tagged(DividendsTags.YIELD).assertDoesNotExist()
    }

    @Test
    fun `not requested data shows dashes with a hint to refresh`() {
        val data = CompanyData.priceAndRatios(quote = null, ratios = emptyList(), status = DataStatus.NOT_REQUESTED)
        setContent(state(priceAndRatios = TabData(data.copy(meta = data.meta.copy(fetchedAt = null)))))

        tagged(PriceAndRatiosTags.PRICE).assertTextEquals("-")
        assertEquals(listOf("No data yet. Tap refresh to load."), texts(SourceDataFrameTags.STATUS_NOTICE))
        tagged(SourceDataFrameTags.ERROR).assertDoesNotExist()
    }

    @Test
    fun `failed request without data shows the error`() {
        setContent(state(priceAndRatios = TabData(error = ApiError.Network(isTimeout = false))))

        tagged(SourceDataFrameTags.ERROR).assertExists()
        composeRule.onNodeWithText("Cannot reach server. Check connection").assertExists()
    }

    @Test
    fun `failed refresh keeps the data and shows the error`() {
        setContent(state(priceAndRatios = TabData(CompanyData.priceAndRatios(), error = ApiError.Network(isTimeout = false))))

        composeRule.onNodeWithText("Cannot reach server. Check connection").assertExists()
        tagged(PriceAndRatiosTags.PRICE).assertTextEquals("$189.84")
    }

    @Test
    fun `loading shows progress`() {
        setContent(state(priceAndRatios = TabData(isLoading = true)))

        tagged(SourceDataFrameTags.PROGRESS).assertExists()
    }

    @Test
    fun `balance sheet with one year shows abbreviated values and the unit`() {
        val sheet = CompanyData.balanceSheet(period(2026, item("Assets", 352_583_000_000.0, label = "Total assets"), item("Cash", 999.0)))
        setContent(state(selectedTab = DetailTab.BALANCE_SHEET, balanceSheet = balanceSheetState(sheet)))

        assertEquals(listOf("2026"), texts(BalanceSheetTags.YEAR_HEADER))
        assertEquals(listOf("Total assets", "Cash"), texts(BalanceSheetTags.LABEL))
        assertEquals(listOf("352.6 B", "999"), texts(BalanceSheetTags.VALUE))
        composeRule.onNodeWithText("Values in USD").assertExists()
    }

    @Test
    fun `balance sheet rows are aligned and missing values are dashes`() {
        val sheet = CompanyData.balanceSheet(
            period(2026, item("Assets", 2e12, label = "Total assets"), item("Goodwill", 1_000.0)),
            period(2025, item("Old", -1_500_000.0), item("Assets", 1e12, label = "Total assets")),
        )
        setContent(state(selectedTab = DetailTab.BALANCE_SHEET, balanceSheet = balanceSheetState(sheet)))

        assertEquals(listOf("2026", "2025"), texts(BalanceSheetTags.YEAR_HEADER))
        assertEquals(listOf("Total assets", "Goodwill", "Old"), texts(BalanceSheetTags.LABEL))
        assertEquals(listOf("2 T", "1 K", "-", "1 T", "-", "-1.5 M"), texts(BalanceSheetTags.VALUE))
    }

    @Test
    fun `balance sheet with mixed units shows the unit per row`() {
        val sheet = CompanyData.balanceSheet(period(2026, item("Assets", 1.0, unit = "USD"), item("Shares", 2.0, unit = "shares")))
        setContent(state(selectedTab = DetailTab.BALANCE_SHEET, balanceSheet = balanceSheetState(sheet)))

        assertEquals(listOf("Assets (USD)", "Shares (shares)"), texts(BalanceSheetTags.LABEL))
    }

    @Test
    fun `balance sheet with many years scrolls horizontally while labels stay visible`() {
        val periods = (2010..2026).map { year -> period(year, item("Assets", year * 1e9, label = "Total assets")) }
        setContent(state(selectedTab = DetailTab.BALANCE_SHEET, balanceSheet = balanceSheetState(CompanyData.balanceSheet(*periods.toTypedArray()))))

        composeRule.onNodeWithText("2026").assertIsDisplayed()
        composeRule.onNodeWithText("2010").assertIsNotDisplayed()

        composeRule.onNodeWithText("2010").performScrollTo()

        composeRule.onNodeWithText("2010").assertIsDisplayed()
        composeRule.onNodeWithText("Total assets").assertIsDisplayed()
    }

    @Test
    fun `balance sheet without periods shows a dash`() {
        setContent(state(selectedTab = DetailTab.BALANCE_SHEET, balanceSheet = balanceSheetState(CompanyData.balanceSheet(status = DataStatus.NO_DATA))))

        tagged(BalanceSheetTags.EMPTY).assertTextEquals("-")
        composeRule.onNodeWithText("The source has no data for this company.").assertExists()
    }

    @Test
    fun `SEC EDGAR for a non US company shows the not supported notice`() {
        val notSupported = BalanceSheet(CompanyData.meta(SourceId.SEC_EDGAR, DataStatus.NOT_SUPPORTED, fetchedAt = null), emptyList())
        setContent(state(selectedTab = DetailTab.BALANCE_SHEET, balanceSheet = balanceSheetState(notSupported)))

        tagged(SourceDataFrameTags.SOURCE).assertTextEquals("Source: SEC EDGAR")
        composeRule.onNodeWithText("This source does not provide this data for the company.").assertExists()
    }

    @Test
    fun `source dropdown offers the options of the tab and passes the picked source up with its tab`() {
        setContent(state(selectedTab = DetailTab.BALANCE_SHEET, balanceSheet = balanceSheetState(CompanyData.balanceSheet())))

        tagged(SourceDropdownTags.DROPDOWN).performClick()
        composeRule.onNodeWithText("SEC EDGAR").performClick()

        assertEquals(listOf("source BALANCE_SHEET sec-edgar"), events)
    }

    @Test
    fun `company profile shows every field and the website as a link`() {
        setContent(state(selectedTab = DetailTab.COMPANY_PROFILE, companyProfile = TabData(CompanyData.companyProfileData())))

        assertEquals(
            listOf(
                "Country, US",
                "Currency, USD",
                "Sector, Technology",
                "Industry, Consumer Electronics",
                "IPO date, 1980-12-12",
                "Market capitalization, 3.5 T",
                "Shares outstanding, 15.2 B",
                "Employees, 164,000",
                "Website, https://www.apple.com",
            ),
            texts(CompanyProfileTags.FIELD, useUnmergedTree = false),
        )
        tagged(CompanyProfileTags.DESCRIPTION).assertTextEquals("Designs smartphones.")
        val link = composeRule.onNodeWithTag(CompanyProfileTags.WEBSITE, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().getLinkAnnotations(0, Int.MAX_VALUE).single().item
        assertEquals("https://www.apple.com", (link as LinkAnnotation.Url).url)
    }

    @Test
    fun `price and ratio section titles are headings`() {
        setContent(state())

        val titles = composeRule.onAllNodesWithTag(PriceAndRatiosTags.SECTION_TITLE, useUnmergedTree = true)
        titles.assertCountEquals(2)
        titles.assertAll(isHeading())
    }

    @Test
    fun `company profile description title is a heading`() {
        setContent(state(selectedTab = DetailTab.COMPANY_PROFILE, companyProfile = TabData(CompanyData.companyProfileData())))

        tagged(CompanyProfileTags.DESCRIPTION_TITLE).assert(isHeading())
    }

    @Test
    fun `status notice and request error are announced as live regions`() {
        setContent(
            state(
                priceAndRatios = TabData(
                    CompanyData.priceAndRatios(status = DataStatus.LIMIT_REACHED),
                    error = ApiError.Network(isTimeout = false),
                ),
            ),
        )

        tagged(SourceDataFrameTags.STATUS_NOTICE).assert(AccessibilityMatchers.isPoliteLiveRegion)
        tagged(SourceDataFrameTags.ERROR).assert(AccessibilityMatchers.isPoliteLiveRegion)
    }

    @Test
    fun `balance sheet headers are headings and values are described with their row and year`() {
        val sheet = CompanyData.balanceSheet(
            period(2026, item("Assets", 2e12, label = "Total assets"), item("Goodwill", 1_000.0)),
            period(2025, item("Assets", 1e12, label = "Total assets")),
        )
        setContent(state(selectedTab = DetailTab.BALANCE_SHEET, balanceSheet = balanceSheetState(sheet)))

        composeRule.onAllNodesWithTag(BalanceSheetTags.YEAR_HEADER, useUnmergedTree = true).assertAll(isHeading())
        composeRule.onNodeWithText("Values in USD").assert(isHeading())
        val descriptions = composeRule.onAllNodesWithTag(BalanceSheetTags.VALUE, useUnmergedTree = true).fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.ContentDescription].single() }
        assertEquals(
            listOf("Total assets, 2026: 2 T", "Goodwill, 2026: 1 K", "Total assets, 2025: 1 T", "Goodwill, 2025: -"),
            descriptions,
        )
    }

    @Test
    fun `source dropdown lists the options in the given order and marks the default and manual-only sources`() {
        setContent(state(selectedTab = DetailTab.BALANCE_SHEET, balanceSheet = balanceSheetState(CompanyData.balanceSheet())))

        composeRule.onNodeWithText("Source: Finnhub (default)").assertExists()
        tagged(SourceDropdownTags.DROPDOWN).performClick()

        val tops = listOf("Finnhub (default)", "SEC EDGAR", "Yahoo Finance (manual only)")
            .map { label -> composeRule.onNodeWithText(label).getUnclippedBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
        assertEquals(3, tops.distinct().size)
    }

    @Test
    fun `manual-only selected source shows the refresh hint of its not requested data`() {
        val notRequested = CompanyData.dividends(dividendYield = null, status = DataStatus.NOT_REQUESTED)
        setContent(
            state(
                selectedTab = DetailTab.DIVIDENDS,
                dividends = TabData(notRequested.copy(meta = notRequested.meta.copy(source = SourceId("yahoo"), fetchedAt = null))),
                dividendOptions = listOf(yahooOption),
            ),
        )

        composeRule.onNodeWithText("Source: Yahoo Finance (manual only)").assertExists()
        composeRule.onNodeWithText("No data yet. Tap refresh to load.").assertExists()
    }

    @Test
    fun `tab without options shows no dropdown`() {
        setContent(state())

        tagged(SourceDropdownTags.DROPDOWN).assertDoesNotExist()
        tagged(SourceDropdownTags.NO_SOURCE).assertDoesNotExist()
        tagged(PriceAndRatiosTags.PRICE).assertExists()
    }

    @Test
    fun `tab whose data no source provides shows a message instead of the dropdown and data`() {
        setContent(
            state(
                selectedTab = DetailTab.BALANCE_SHEET,
                balanceSheet = SourceTabState(hasNoSource = true),
            ),
        )

        tagged(SourceDropdownTags.DROPDOWN).assertDoesNotExist()
        tagged(SourceDropdownTags.NO_SOURCE).assertTextEquals("No source provides this data for this company.")
        tagged(SourceDataFrameTags.PROGRESS).assertDoesNotExist()
    }

    @Test
    fun `company profile without a source keeps its tags section and shows the message`() {
        setContent(
            state(selectedTab = DetailTab.COMPANY_PROFILE).let { it.copy(companyProfile = SourceTabState(hasNoSource = true)) },
        )

        tagged(SourceDropdownTags.NO_SOURCE).assertExists()
        composeRule.onNodeWithText("Country").assertDoesNotExist()
    }

    @Test
    fun `company profile dropdown passes the picked profile source up`() {
        setContent(
            state(
                selectedTab = DetailTab.COMPANY_PROFILE,
                companyProfile = TabData(CompanyData.companyProfileData()),
                profileOptions = listOf(finnhubOption, yahooOption),
            ),
        )

        tagged(SourceDropdownTags.DROPDOWN).performClick()
        composeRule.onNodeWithText("Yahoo Finance (manual only)").performClick()

        assertEquals(listOf("source COMPANY_PROFILE yahoo"), events)
    }

    @Test
    fun `source dropdown marks the selected source`() {
        setContent(state(selectedTab = DetailTab.BALANCE_SHEET, balanceSheet = balanceSheetState(CompanyData.balanceSheet())))

        tagged(SourceDropdownTags.DROPDOWN).performClick()

        composeRule.onNode(hasText("Finnhub (default)") and isSelected()).assertExists()
        composeRule.onNode(hasText("SEC EDGAR") and isNotSelected()).assertExists()
    }

    @Test
    fun `header row is read as one item next to its watchlist button`() {
        setContent(state())

        composeRule.onNodeWithTag(CompanyRowTags.ROW).assert(hasText("AAPL")).assert(hasText("Apple Inc."))
    }
}
