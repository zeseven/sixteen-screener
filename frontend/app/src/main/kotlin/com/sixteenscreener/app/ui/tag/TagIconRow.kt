package com.sixteenscreener.app.ui.tag

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.ui.theme.SixteenTheme

object TagIconRowTags {
    const val ROW = "tag_icon_row"
}

private val ICON_SIZE = 14.dp
private val ICON_SPACING = 4.dp

/**
 * Icons of [tags] without their texts, aligned to the end; icons that don't fit into the given width are replaced by `…`.
 * The row is never taller than a line of small label text. Screen readers get the texts of all tags.
 */
@Composable
fun TagIconRow(tags: List<Tag>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    val description = stringResource(R.string.tags_header_description, tags.joinToString(", ") { it.text })
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Layout(
        content = {
            tags.forEach { tag ->
                Icon(
                    painter = painterResource(TagIcons.of(tag.icon).drawable),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(ICON_SIZE),
                )
            }
            Text(
                text = stringResource(R.string.tags_header_more),
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        },
        modifier = modifier
            .testTag(TagIconRowTags.ROW)
            .clearAndSetSemantics { contentDescription = description },
    ) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val icons = measurables.dropLast(1).map { it.measure(looseConstraints) }
        val ellipsis = measurables.last().measure(looseConstraints)
        val spacing = ICON_SPACING.roundToPx()
        val fit = TagIconsFit.of(icons.size, constraints.maxWidth, icons.first().width, spacing, ellipsis.width)
        val shown = icons.take(fit.visibleIcons) + listOfNotNull(ellipsis.takeIf { fit.showsEllipsis })
        val contentWidth = shown.sumOf { it.width } + spacing * (shown.size - 1).coerceAtLeast(0)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else contentWidth
        val height = (shown.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width.coerceAtLeast(constraints.minWidth), height) {
            shown.fold(width - contentWidth) { x, placeable ->
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x + placeable.width + spacing
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 120)
@Composable
private fun TagIconRowPreview() {
    SixteenTheme(dynamicColor = false) {
        TagIconRow(listOf(Tag("chess-king", "Dividend king"), Tag("coin", "Income"), Tag("rocket-takeoff", "Growth"), Tag("eye", "Watch")))
    }
}
