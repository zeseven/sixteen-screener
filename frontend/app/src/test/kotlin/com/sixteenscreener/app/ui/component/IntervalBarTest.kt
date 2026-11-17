package com.sixteenscreener.app.ui.component

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixteenscreener.app.domain.model.IndicatorIntervals
import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.IndicatorScale
import com.sixteenscreener.app.testing.IndicatorSettings.intervals
import com.sixteenscreener.app.ui.theme.SixteenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntervalBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val scale = IndicatorScale(max = 60.0, step = 1.0)
    private var intervals by mutableStateOf(intervals(15.0, 25.0, 40.0))
    private val changes = mutableListOf<IndicatorIntervals>()

    private fun setContent(initial: IndicatorIntervals = intervals(15.0, 25.0, 40.0), muted: Boolean = false) {
        intervals = initial
        composeRule.setContent {
            SixteenTheme(dynamicColor = false) {
                IntervalBar(
                    intervals = intervals,
                    scale = scale,
                    ratioName = "P/E",
                    onIntervalsChange = {
                        changes += it
                        intervals = it
                    },
                    muted = muted,
                    modifier = Modifier.width(BAR_WIDTH),
                )
            }
        }
    }

    private val geometry: IntervalBarGeometry
        get() = with(composeRule.density) {
            IntervalBarGeometry(BAR_WIDTH.toPx(), IntervalBarDimensions.BelowZeroWidth.toPx(), IntervalBarDimensions.Gap.toPx(), scale.max)
        }

    private val trackY: Float
        get() = with(composeRule.density) { (IntervalBarDimensions.TrackTop + IntervalBarDimensions.TrackHeight / 2).toPx() }

    private fun bar() = composeRule.onNodeWithTag(IntervalBarTags.BAR)

    private fun tagged(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun drag(from: Double, to: Double) = bar().performTouchInput {
        swipe(Offset(geometry.xOf(from), trackY), Offset(geometry.xOf(to), trackY), durationMillis = 300)
    }

    private fun dragX(fromX: Float, toX: Float) = bar().performTouchInput {
        swipe(Offset(fromX, trackY), Offset(toX, trackY), durationMillis = 300)
    }

    private fun tapAt(value: Double) = bar().performTouchInput { click(Offset(geometry.xOf(value), trackY)) }

    private fun SemanticsNodeInteraction.customAction(label: String) {
        val action = fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == label }
        composeRule.runOnIdle { action.action() }
    }

    @Test
    fun `dragging a marker moves it and snaps to the step`() {
        setContent()

        drag(25.0, 31.3)

        assertEquals(listOf(15.0, 31.0, 40.0), intervals.thresholds)
    }

    @Test
    fun `dragging a marker past its neighbour stops at the neighbour`() {
        setContent()

        drag(25.0, 55.0)

        assertEquals(listOf(15.0, 40.0, 40.0), intervals.thresholds)
    }

    @Test
    fun `dragging below 0 and beyond the scale clamps to the scale`() {
        setContent()

        dragX(geometry.xOf(15.0), 0f)
        assertEquals(0.0, intervals.thresholds[0], 0.0)

        dragX(geometry.xOf(40.0), with(composeRule.density) { BAR_WIDTH.toPx() } - 1f)
        assertEquals(60.0, intervals.thresholds[2], 0.0)
    }

    @Test
    fun `coinciding markers are separated by the drag direction`() {
        setContent(intervals(25.0, 25.0, 25.0))

        drag(25.0, 35.0)
        assertEquals(listOf(25.0, 25.0, 35.0), intervals.thresholds)

        drag(25.0, 10.0)
        assertEquals(listOf(10.0, 25.0, 35.0), intervals.thresholds)
    }

    @Test
    fun `dragging away from markers changes nothing`() {
        setContent(intervals(5.0, 10.0, 15.0))

        drag(45.0, 55.0)

        assertEquals(emptyList<IndicatorIntervals>(), changes)
    }

    @Test
    fun `an unsnapped threshold keeps its value until it is dragged`() {
        setContent(intervals(15.0, 22.37, 40.0))

        tapAt(5.0)
        assertEquals(22.37, intervals.thresholds[1], 0.0)

        drag(22.37, 30.2)
        assertEquals(30.0, intervals.thresholds[1], 0.0)
    }

    @Test
    fun `tapping an interval cycles its level`() {
        setContent()

        tapAt(20.0)
        assertEquals(IndicatorLevel.GOOD, intervals.levels[1])
        tapAt(20.0)
        assertEquals(IndicatorLevel.WARNING, intervals.levels[1])
        tapAt(20.0)
        assertEquals(IndicatorLevel.BAD, intervals.levels[1])
        tapAt(20.0)
        assertEquals(IndicatorLevel.NEUTRAL, intervals.levels[1])
        assertEquals(listOf(IndicatorLevel.GOOD, IndicatorLevel.WARNING, IndicatorLevel.BAD), intervals.levels.let { listOf(it[0], it[2], it[3]) })
    }

    @Test
    fun `tapping the below-zero zone cycles its level`() {
        setContent()

        bar().performTouchInput { click(Offset(geometry.belowZeroWidth / 2, trackY)) }

        assertEquals(IndicatorLevel.NEUTRAL, intervals.belowZeroLevel)
        assertEquals(intervals(15.0, 25.0, 40.0).levels, intervals.levels)
    }

    @Test
    fun `muted bar stays editable`() {
        setContent(muted = true)

        tapAt(50.0)

        assertEquals(IndicatorLevel.NEUTRAL, intervals.levels[3])
    }

    @Test
    fun `markers describe themselves with their value and range`() {
        setContent()

        tagged(IntervalBarTags.marker(1))
            .assertContentDescriptionEquals("P/E threshold 2")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "25"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(25f, 15f..40f)))
        tagged(IntervalBarTags.marker(0))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(15f, 0f..25f)))
    }

    @Test
    fun `set progress moves a marker without snapping and clamps it`() {
        setContent()

        tagged(IntervalBarTags.marker(1)).performSemanticsAction(SemanticsActions.SetProgress) { it(22.5f) }
        assertEquals(22.5, intervals.thresholds[1], 0.0)

        tagged(IntervalBarTags.marker(1)).performSemanticsAction(SemanticsActions.SetProgress) { it(50f) }
        assertEquals(40.0, intervals.thresholds[1], 0.0)
    }

    @Test
    fun `increase and decrease move a marker by one step`() {
        setContent(intervals(15.0, 22.5, 40.0))

        tagged(IntervalBarTags.marker(1)).customAction("Increase")
        assertEquals(23.0, intervals.thresholds[1], 0.0)

        tagged(IntervalBarTags.marker(1)).customAction("Decrease")
        tagged(IntervalBarTags.marker(1)).customAction("Decrease")
        assertEquals(21.0, intervals.thresholds[1], 0.0)
    }

    @Test
    fun `increase stops at the neighbour`() {
        setContent(intervals(15.0, 40.0, 40.0))

        tagged(IntervalBarTags.marker(1)).customAction("Increase")

        assertEquals(40.0, intervals.thresholds[1], 0.0)
    }

    @Test
    fun `intervals announce their range and level and change color on click`() {
        setContent()

        tagged(IntervalBarTags.BELOW_ZERO).assertContentDescriptionEquals("P/E below 0")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "bad"))
        tagged(IntervalBarTags.interval(0)).assertContentDescriptionEquals("P/E from 0 to 15")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "good"))
        tagged(IntervalBarTags.interval(2)).assertContentDescriptionEquals("P/E from 25 to 40")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "warning"))
        tagged(IntervalBarTags.interval(3)).assertContentDescriptionEquals("P/E from 40")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "bad"))

        tagged(IntervalBarTags.interval(2)).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(IndicatorLevel.BAD, intervals.levels[2])
        assertEquals("Change color", tagged(IntervalBarTags.interval(2)).fetchSemanticsNode().config[SemanticsActions.OnClick].label)
    }

    @Test
    fun `long press on a marker opens a dialog that accepts an exact value`() {
        setContent()

        bar().performTouchInput { longClick(Offset(geometry.xOf(25.0), trackY)) }
        composeRule.onNodeWithText("P/E threshold 2").assertExists()
        composeRule.onNodeWithTag(IntervalBarTags.VALUE_FIELD).performTextReplacement("22,37")
        composeRule.onNodeWithTag(IntervalBarTags.VALUE_CONFIRM).performClick()

        assertEquals(listOf(15.0, 22.37, 40.0), intervals.thresholds)
        composeRule.onNodeWithTag(IntervalBarTags.VALUE_FIELD).assertDoesNotExist()
    }

    @Test
    fun `the value dialog rejects values outside the neighbours and non-numbers`() {
        setContent()

        tagged(IntervalBarTags.marker(1)).customAction("Enter exact value")
        composeRule.onNodeWithTag(IntervalBarTags.VALUE_FIELD).performTextReplacement("41")
        composeRule.onNodeWithTag(IntervalBarTags.VALUE_CONFIRM).performClick()
        composeRule.onNodeWithText("Enter a value from 15 to 40").assertExists()

        composeRule.onNodeWithTag(IntervalBarTags.VALUE_FIELD).performTextReplacement("abc")
        composeRule.onNodeWithTag(IntervalBarTags.VALUE_CONFIRM).performClick()
        composeRule.onNodeWithText("Enter a number").assertExists()

        assertEquals(emptyList<IndicatorIntervals>(), changes)
    }

    @Test
    fun `long press away from markers opens no dialog`() {
        setContent()

        bar().performTouchInput { longClick(Offset(geometry.xOf(33.0), trackY)) }

        composeRule.onNodeWithTag(IntervalBarTags.VALUE_FIELD).assertDoesNotExist()
        assertEquals(emptyList<IndicatorIntervals>(), changes)
    }

    private companion object {
        val BAR_WIDTH = 300.dp
    }
}
