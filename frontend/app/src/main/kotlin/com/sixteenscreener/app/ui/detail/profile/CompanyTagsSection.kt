package com.sixteenscreener.app.ui.detail.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.TagRules
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.ui.detail.TagsUiState
import com.sixteenscreener.app.ui.tag.TagIcons
import com.sixteenscreener.app.ui.theme.SixteenTheme

object CompanyTagsTags {
    const val TITLE = "company_tags_title"
    const val TAG = "company_tags_tag"
    const val REMOVE = "company_tags_remove"
    const val ADD = "company_tags_add"
    const val EMPTY = "company_tags_empty"
    const val HINT = "company_tags_hint"
    const val PROGRESS = "company_tags_progress"
}

/**
 * First section of the company profile tab: the company's tags with their icons and texts, a button removing each tag and a button
 * adding one. Read-only with an explanation for a company outside the watchlist or with the maximum number of tags.
 */
@Composable
fun CompanyTagsSection(
    state: TagsUiState,
    onAddTag: () -> Unit,
    onRemoveTag: (Tag) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.tags_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
                    .testTag(CompanyTagsTags.TITLE),
            )
            if (state.isEditable) {
                TextButton(onClick = onAddTag, enabled = state.canAdd, modifier = Modifier.testTag(CompanyTagsTags.ADD)) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.tags_add), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (state.isSaving) {
            val savingDescription = stringResource(R.string.tags_saving)
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .semantics { contentDescription = savingDescription }
                    .testTag(CompanyTagsTags.PROGRESS),
            )
        }
        hintText(state)?.let { Hint(it) }
        if (state.tags.isEmpty() && state.isEditable) {
            Hint(stringResource(R.string.tags_empty), Modifier.testTag(CompanyTagsTags.EMPTY))
        }
        state.tags.forEach { tag ->
            TagRow(tag = tag, isEditable = state.isEditable, canRemove = state.canRemove, onRemove = { onRemoveTag(tag) })
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun hintText(state: TagsUiState): String? = when {
    !state.isEditable -> stringResource(R.string.tags_not_in_watchlist)
    state.isLimitReached -> stringResource(R.string.tags_limit_reached, TagRules.MAX_TAGS)
    else -> null
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier.testTag(CompanyTagsTags.HINT)) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun TagRow(tag: Tag, isEditable: Boolean, canRemove: Boolean, onRemove: () -> Unit) {
    val icon = TagIcons.of(tag.icon)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
                .semantics(mergeDescendants = true) {}
                .testTag(CompanyTagsTags.TAG),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(icon.drawable),
                contentDescription = stringResource(icon.label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(text = tag.text, style = MaterialTheme.typography.bodyMedium)
        }
        if (isEditable) {
            IconButton(onClick = onRemove, enabled = canRemove, modifier = Modifier.testTag(CompanyTagsTags.REMOVE)) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.tags_remove_description, tag.text))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CompanyTagsSectionPreview() {
    SixteenTheme(dynamicColor = false) {
        CompanyTagsSection(
            state = TagsUiState(tags = listOf(Tag("chess-king", "Dividend king"), Tag("rocket-takeoff", "Growth")), isEditable = true),
            onAddTag = {},
            onRemoveTag = {},
        )
    }
}
