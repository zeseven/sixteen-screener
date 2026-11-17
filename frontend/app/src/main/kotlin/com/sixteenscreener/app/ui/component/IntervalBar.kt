package com.sixteenscreener.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.IntervalEditing
import com.sixteenscreener.app.domain.IntervalZone
import com.sixteenscreener.app.domain.NumberFormats
import com.sixteenscreener.app.domain.ThresholdInput
import com.sixteenscreener.app.domain.model.IndicatorIntervals
import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.IndicatorScale
import com.sixteenscreener.app.ui.theme.IndicatorColors
import com.sixteenscreener.app.ui.theme.SixteenTheme
import kotlin.math.roundToInt

object IntervalBarTags {
    const val BAR = "interval_bar"
    const val BELOW_ZERO = "interval_bar_below_zero"
    const val VALUE_FIELD = "interval_bar_value_field"
    const val VALUE_CONFIRM = "interval_bar_value_confirm"

    fun marker(index: Int) = "interval_bar_marker_$index"

    fun interval(index: Int) = "interval_bar_interval_$index"
}

/** Fixed dimensions of the [IntervalBar]. */
object IntervalBarDimensions {
    val Height: Dp = 56.dp
    val BubbleHeight: Dp = 22.dp
    val TrackTop: Dp = 28.dp
    val TrackHeight: Dp = 16.dp
    val BelowZeroWidth: Dp = 16.dp
    val Gap: Dp = 4.dp
    val MarkerWidth: Dp = 6.dp
    val MarkerHeight: Dp = 26.dp
    val TouchRadius: Dp = 24.dp
}

/**
 * Indicator settings bar of one ratio: the below-zero zone at the left edge, then the scale from 0 to [scale] max split into four
 * intervals by three threshold markers, each part filled in the color of its level.
 *
 * A marker is dragged (snapping to the step, clamped to its neighbours and the scale) with a value bubble above it; a long press
 * on a marker opens a dialog for an exact value, which is kept unsnapped. A tap on an interval or the below-zero zone cycles its
 * level. Every marker and interval is a semantics node with actions for the same edits. [muted] draws the bar faded (a switched
 * off ratio) while it stays editable.
 */
@Composable
fun IntervalBar(
    intervals: IndicatorIntervals,
    scale: IndicatorScale,
    ratioName: String,
    onIntervalsChange: (IndicatorIntervals) -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val currentIntervals by rememberUpdatedState(intervals)
    val currentOnChange by rememberUpdatedState(onIntervalsChange)
    var draggedMarker by remember { mutableStateOf<Int?>(null) }
    var editedMarker by rememberSaveable { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    val colors = barColors(muted)
    val textMeasurer = rememberTextMeasurer()
    val bubbleStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.inverseOnSurface)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(IntervalBarDimensions.Height)
            .testTag(IntervalBarTags.BAR),
    ) {
        val geometry = with(density) {
            IntervalBarGeometry(
                width = constraints.maxWidth.toFloat(),
                belowZeroWidth = IntervalBarDimensions.BelowZeroWidth.toPx(),
                gap = IntervalBarDimensions.Gap.toPx(),
                scaleMax = scale.max,
            )
        }
        val currentGeometry by rememberUpdatedState(geometry)
        val touchRadius = with(density) { IntervalBarDimensions.TouchRadius.toPx() }
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(scale) {
                    var startX = 0f
                    var marker: Int? = null
                    detectHorizontalDragGestures(
                        onDragStart = { startX = it.x },
                        onDragEnd = {
                            marker = null
                            draggedMarker = null
                        },
                        onDragCancel = {
                            marker = null
                            draggedMarker = null
                        },
                    ) { change, dragAmount ->
                        val index = marker
                            ?: currentGeometry.markerAt(startX, currentIntervals, touchRadius, dragAmount)
                                .also { marker = it; draggedMarker = it }
                            ?: return@detectHorizontalDragGestures
                        change.consume()
                        val value = currentGeometry.valueAt(change.position.x)
                        currentOnChange(IntervalEditing.withDraggedThreshold(currentIntervals, index, value, scale))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val zone = currentGeometry.zoneAt(offset.x, currentIntervals)
                            currentOnChange(IntervalEditing.withNextLevel(currentIntervals, zone))
                        },
                        onLongPress = { offset ->
                            currentGeometry.markerAt(offset.x, currentIntervals, touchRadius)?.let { editedMarker = it }
                        },
                    )
                },
        ) {
            drawBar(geometry, intervals, colors)
            draggedMarker?.let { index ->
                val text = NumberFormats.threshold(intervals.thresholds[index], scale.step)
                drawBubble(textMeasurer, text, bubbleStyle, geometry.xOf(intervals.thresholds[index]), colors.bubble)
            }
        }
        ZoneNodes(geometry, intervals, scale, ratioName) { zone -> onIntervalsChange(IntervalEditing.withNextLevel(intervals, zone)) }
        intervals.thresholds.indices.forEach { index ->
            MarkerNode(
                index = index,
                geometry = geometry,
                intervals = intervals,
                scale = scale,
                ratioName = ratioName,
                onIntervalsChange = onIntervalsChange,
                onEnterValue = { editedMarker = index },
            )
        }
    }
    editedMarker?.let { index ->
        ThresholdValueDialog(
            title = stringResource(R.string.interval_bar_threshold, ratioName, index + 1),
            initialValue = NumberFormats.threshold(intervals.thresholds[index], scale.step),
            bounds = IntervalEditing.thresholdBounds(intervals, index, scale),
            step = scale.step,
            onConfirm = { value ->
                editedMarker = null
                onIntervalsChange(IntervalEditing.withThreshold(intervals, index, value, scale))
            },
            onDismiss = { editedMarker = null },
        )
    }
}

private data class BarColors(
    val indicatorColors: IndicatorColors,
    val alpha: Float,
    val marker: Color,
    val bubble: Color,
) {
    fun fillOf(level: IndicatorLevel): Color = indicatorColors.fillOf(level).copy(alpha = alpha)
}

@Composable
private fun barColors(muted: Boolean) = BarColors(
    indicatorColors = SixteenTheme.indicatorColors,
    alpha = if (muted) MUTED_ALPHA else 1f,
    marker = MaterialTheme.colorScheme.onSurface.copy(alpha = if (muted) MUTED_ALPHA else 1f),
    bubble = MaterialTheme.colorScheme.inverseSurface,
)

private const val MUTED_ALPHA = 0.38f

private fun DrawScope.drawBar(geometry: IntervalBarGeometry, intervals: IndicatorIntervals, colors: BarColors) {
    val trackTop = IntervalBarDimensions.TrackTop.toPx()
    val trackHeight = IntervalBarDimensions.TrackHeight.toPx()
    drawRect(colors.fillOf(intervals.belowZeroLevel), Offset(0f, trackTop), Size(geometry.belowZeroWidth, trackHeight))
    intervalRanges(geometry, intervals).forEachIndexed { index, (start, end) ->
        drawRect(colors.fillOf(intervals.levels[index]), Offset(start, trackTop), Size(end - start, trackHeight))
    }
    val markerWidth = IntervalBarDimensions.MarkerWidth.toPx()
    val markerHeight = IntervalBarDimensions.MarkerHeight.toPx()
    val markerTop = trackTop + trackHeight / 2 - markerHeight / 2
    intervals.thresholds.forEach { threshold ->
        drawRoundRect(
            color = colors.marker,
            topLeft = Offset(geometry.xOf(threshold) - markerWidth / 2, markerTop),
            size = Size(markerWidth, markerHeight),
            cornerRadius = CornerRadius(markerWidth / 2),
        )
    }
}

private fun DrawScope.drawBubble(textMeasurer: TextMeasurer, text: String, style: TextStyle, x: Float, color: Color) {
    val layout = textMeasurer.measure(text, style)
    val padding = 6.dp.toPx()
    val bubbleWidth = layout.size.width + 2 * padding
    val bubbleHeight = IntervalBarDimensions.BubbleHeight.toPx()
    val left = (x - bubbleWidth / 2).coerceIn(0f, (size.width - bubbleWidth).coerceAtLeast(0f))
    drawRoundRect(color, Offset(left, 0f), Size(bubbleWidth, bubbleHeight), CornerRadius(bubbleHeight / 2))
    drawText(layout, topLeft = Offset(left + padding, (bubbleHeight - layout.size.height) / 2))
}

/** Horizontal pixel range of each of the four intervals. */
private fun intervalRanges(geometry: IntervalBarGeometry, intervals: IndicatorIntervals): List<Pair<Float, Float>> {
    val bounds = listOf(0.0) + intervals.thresholds + geometry.scaleMax
    return bounds.zipWithNext { lower, upper -> geometry.xOf(lower) to geometry.xOf(maxOf(lower, upper)) }
}

@Composable
private fun ZoneNodes(
    geometry: IntervalBarGeometry,
    intervals: IndicatorIntervals,
    scale: IndicatorScale,
    ratioName: String,
    onCycle: (IntervalZone) -> Unit,
) {
    val changeColor = stringResource(R.string.interval_bar_change_color)
    val zones = listOf<Pair<IntervalZone, Pair<Float, Float>>>(IntervalZone.BelowZero to (0f to geometry.belowZeroWidth)) +
        intervalRanges(geometry, intervals).mapIndexed { index, range -> IntervalZone.Interval(index) to range }
    zones.forEach { (zone, range) ->
        val description = zoneDescription(zone, intervals, scale, ratioName)
        val level = stringResource(indicatorLevelLabel(IntervalEditing.levelOf(intervals, zone)))
        PositionedNode(
            left = range.first,
            width = range.second - range.first,
            top = IntervalBarDimensions.TrackTop,
            height = IntervalBarDimensions.TrackHeight,
            modifier = Modifier
                .semantics {
                    contentDescription = description
                    stateDescription = level
                    role = Role.Button
                    onClick(label = changeColor) {
                        onCycle(zone)
                        true
                    }
                }
                .testTag(if (zone is IntervalZone.Interval) IntervalBarTags.interval(zone.index) else IntervalBarTags.BELOW_ZERO),
        )
    }
}

@Composable
private fun zoneDescription(zone: IntervalZone, intervals: IndicatorIntervals, scale: IndicatorScale, ratioName: String): String {
    val bounds = listOf(0.0) + intervals.thresholds
    return when (zone) {
        IntervalZone.BelowZero -> stringResource(R.string.interval_bar_below_zero, ratioName)
        is IntervalZone.Interval -> {
            val lower = NumberFormats.threshold(bounds[zone.index], scale.step)
            val upper = intervals.thresholds.getOrNull(zone.index)
            if (upper == null) {
                stringResource(R.string.interval_bar_from, ratioName, lower)
            } else {
                stringResource(R.string.interval_bar_range, ratioName, lower, NumberFormats.threshold(upper, scale.step))
            }
        }
    }
}

@Composable
private fun MarkerNode(
    index: Int,
    geometry: IntervalBarGeometry,
    intervals: IndicatorIntervals,
    scale: IndicatorScale,
    ratioName: String,
    onIntervalsChange: (IndicatorIntervals) -> Unit,
    onEnterValue: () -> Unit,
) {
    val value = intervals.thresholds[index]
    val bounds = IntervalEditing.thresholdBounds(intervals, index, scale)
    val description = stringResource(R.string.interval_bar_threshold, ratioName, index + 1)
    val formatted = NumberFormats.threshold(value, scale.step)
    val increase = stringResource(R.string.interval_bar_increase)
    val decrease = stringResource(R.string.interval_bar_decrease)
    val enterValue = stringResource(R.string.interval_bar_enter_value)
    val touchRadius = with(LocalDensity.current) { IntervalBarDimensions.TouchRadius.toPx() }
    fun moveTo(target: Double): Boolean {
        onIntervalsChange(IntervalEditing.withThreshold(intervals, index, target, scale))
        return true
    }
    PositionedNode(
        left = geometry.xOf(value) - touchRadius,
        width = 2 * touchRadius,
        top = 0.dp,
        height = IntervalBarDimensions.Height,
        modifier = Modifier
            .semantics {
                contentDescription = description
                stateDescription = formatted
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.toFloat(),
                    range = bounds.start.toFloat()..bounds.endInclusive.toFloat(),
                )
                setProgress { target -> moveTo(target.toDouble()) }
                customActions = listOf(
                    CustomAccessibilityAction(increase) { moveTo(IntervalEditing.stepUp(value, scale.step)) },
                    CustomAccessibilityAction(decrease) { moveTo(IntervalEditing.stepDown(value, scale.step)) },
                    CustomAccessibilityAction(enterValue) {
                        onEnterValue()
                        true
                    },
                )
            }
            .testTag(IntervalBarTags.marker(index)),
    )
}

@Composable
private fun PositionedNode(left: Float, width: Float, top: Dp, height: Dp, modifier: Modifier = Modifier) {
    val widthDp = with(LocalDensity.current) { width.coerceAtLeast(0f).toDp() }
    Box(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToPx()) }
            .size(widthDp, height)
            .then(modifier),
    )
}

@Composable
private fun ThresholdValueDialog(
    title: String,
    initialValue: String,
    bounds: ClosedFloatingPointRange<Double>,
    step: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialValue) }
    var input by remember { mutableStateOf<ThresholdInput?>(null) }
    val lower = NumberFormats.threshold(bounds.start, step)
    val upper = NumberFormats.threshold(bounds.endInclusive, step)
    val error = when (input) {
        ThresholdInput.NotANumber -> stringResource(R.string.interval_bar_value_not_a_number)
        is ThresholdInput.OutOfRange -> stringResource(R.string.interval_bar_value_out_of_range, lower, upper)
        is ThresholdInput.Valid, null -> null
    }
    val confirm: () -> Unit = {
        when (val parsed = ThresholdInput.parse(text, bounds)) {
            is ThresholdInput.Valid -> onConfirm(parsed.value)
            else -> input = parsed
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    input = null
                },
                label = { Text(stringResource(R.string.interval_bar_value_label, lower, upper)) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.testTag(IntervalBarTags.VALUE_FIELD),
            )
        },
        confirmButton = {
            TextButton(onClick = confirm, modifier = Modifier.testTag(IntervalBarTags.VALUE_CONFIRM)) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

@Preview(showBackground = true)
@Composable
private fun IntervalBarPreview() {
    SixteenTheme(dynamicColor = false) {
        Surface {
            IntervalBar(
                intervals = IndicatorIntervals(
                    thresholds = listOf(15.0, 25.0, 40.0),
                    levels = listOf(IndicatorLevel.GOOD, IndicatorLevel.NEUTRAL, IndicatorLevel.WARNING, IndicatorLevel.BAD),
                    belowZeroLevel = IndicatorLevel.BAD,
                ),
                scale = IndicatorScale(max = 60.0, step = 1.0),
                ratioName = "P/E",
                onIntervalsChange = {},
            )
        }
    }
}
