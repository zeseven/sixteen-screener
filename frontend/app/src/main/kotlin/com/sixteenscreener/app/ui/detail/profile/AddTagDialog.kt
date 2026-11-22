package com.sixteenscreener.app.ui.detail.profile

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.TagProblem
import com.sixteenscreener.app.domain.TagRules
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.ui.component.apiErrorMessage
import com.sixteenscreener.app.ui.tag.TagIcon
import com.sixteenscreener.app.ui.tag.TagIcons
import com.sixteenscreener.app.ui.theme.SixteenTheme

object AddTagDialogTags {
    const val TEXT_FIELD = "add_tag_text_field"
    const val SUGGESTION = "add_tag_suggestion"
    const val CONFIRM = "add_tag_confirm"
    const val CANCEL = "add_tag_cancel"
    const val PROBLEM = "add_tag_problem"
    const val ERROR = "add_tag_error"

    fun icon(id: String) = "add_tag_icon_$id"
}

/**
 * Dialog adding a tag to a company with [tags]: an icon picked from the grouped icon set and a text of up to
 * [TagRules.MAX_TEXT_LENGTH] characters, both required. The profile's [suggestions] fill both fields when tapped.
 */
@Composable
fun AddTagDialog(
    tags: List<Tag>,
    suggestions: List<Tag>,
    isSaving: Boolean,
    error: ApiError?,
    onAdd: (icon: String, text: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var icon by rememberSaveable { mutableStateOf<String?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    val problem = TagRules.problemOfAdding(tags, icon, text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (suggestions.isNotEmpty()) {
                    Suggestions(suggestions) { tag ->
                        icon = tag.icon
                        text = tag.text
                    }
                }
                TextInput(text = text, problem = problem.takeIf { it == TagProblem.DUPLICATE || it == TagProblem.TEXT_TOO_LONG }) {
                    text = TagRules.limitText(it)
                }
                SectionTitle(stringResource(R.string.tag_dialog_icon))
                TagIcons.groups.forEach { group ->
                    Text(
                        text = stringResource(group.title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { heading() },
                    )
                    IconGrid(icons = group.icons, selected = icon, onSelect = { icon = it })
                }
                error?.let {
                    Text(
                        text = stringResource(R.string.tags_save_failed, apiErrorMessage(it)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag(AddTagDialogTags.ERROR),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { icon?.let { onAdd(it, text) } },
                enabled = problem == null && !isSaving,
                modifier = Modifier.testTag(AddTagDialogTags.CONFIRM),
            ) {
                Text(stringResource(R.string.tag_dialog_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(AddTagDialogTags.CANCEL)) {
                Text(stringResource(R.string.tag_dialog_cancel))
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun Suggestions(suggestions: List<Tag>, onPick: (Tag) -> Unit) {
    SectionTitle(stringResource(R.string.tag_dialog_reuse))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { tag ->
            val description = stringResource(R.string.tag_dialog_reuse_description, tag.text)
            AssistChip(
                onClick = { onPick(tag) },
                label = { Text(tag.text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(painterResource(TagIcons.of(tag.icon).drawable), contentDescription = null, Modifier.size(18.dp)) },
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .semantics { contentDescription = description }
                    .testTag(AddTagDialogTags.SUGGESTION),
            )
        }
    }
}

@Composable
private fun TextInput(text: String, problem: TagProblem?, onTextChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text(stringResource(R.string.tag_dialog_text)) },
        singleLine = true,
        isError = problem != null,
        supportingText = {
            Text(
                text = problem?.let { problemText(it) }
                    ?: stringResource(R.string.tag_dialog_text_counter, text.codePointCount(0, text.length), TagRules.MAX_TEXT_LENGTH),
                modifier = Modifier.testTag(AddTagDialogTags.PROBLEM),
            )
        },
        modifier = Modifier.fillMaxWidth().testTag(AddTagDialogTags.TEXT_FIELD),
    )
}

@Composable
private fun problemText(problem: TagProblem): String = when (problem) {
    TagProblem.LIMIT_REACHED -> stringResource(R.string.tags_limit_reached, TagRules.MAX_TAGS)
    TagProblem.ICON_MISSING -> stringResource(R.string.tag_problem_icon_missing)
    TagProblem.TEXT_BLANK -> stringResource(R.string.tag_problem_text_blank)
    TagProblem.TEXT_TOO_LONG -> stringResource(R.string.tag_problem_text_too_long, TagRules.MAX_TEXT_LENGTH)
    TagProblem.DUPLICATE -> stringResource(R.string.tag_problem_duplicate)
}

@Composable
private fun IconGrid(icons: List<TagIcon>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        icons.forEach { icon ->
            val isSelected = icon.id == selected
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(icon.id) })
                    .testTag(AddTagDialogTags.icon(icon.id)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon.drawable),
                    contentDescription = stringResource(icon.label),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AddTagDialogPreview() {
    SixteenTheme(dynamicColor = false) {
        AddTagDialog(
            tags = emptyList(),
            suggestions = listOf(Tag("chess-king", "Dividend king")),
            isSaving = false,
            error = null,
            onAdd = { _, _ -> },
            onDismiss = {},
        )
    }
}
