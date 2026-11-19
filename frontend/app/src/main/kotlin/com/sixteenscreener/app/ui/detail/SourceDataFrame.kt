package com.sixteenscreener.app.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.DataAge
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.SourceDataMeta
import com.sixteenscreener.app.ui.component.CenteredMessage
import com.sixteenscreener.app.ui.component.LocalClock
import com.sixteenscreener.app.ui.component.apiErrorMessage
import java.time.Instant

object SourceDataFrameTags {
    const val PROGRESS = "detail_progress"
    const val SOURCE = "detail_source"
    const val STATUS_NOTICE = "detail_status_notice"
    const val ERROR = "detail_error"
}

/**
 * Frame of a detailed screen tab: progress while loading, source name with data age, status notice and request error,
 * then [content] for the loaded data. Without data, a failed request is shown in place of the content.
 */
@Composable
fun SourceDataFrame(
    meta: SourceDataMeta?,
    sourceName: String?,
    isLoading: Boolean,
    error: ApiError?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag(SourceDataFrameTags.PROGRESS))
        }
        if (meta != null && sourceName != null) {
            SourceLine(sourceName = sourceName, fetchedAt = meta.fetchedAt)
            statusNoticeText(meta.status)?.let { StatusNotice(it) }
        }
        when {
            error != null && meta == null && !isLoading -> CenteredMessage(apiErrorMessage(error), Modifier.testTag(SourceDataFrameTags.ERROR))
            else -> {
                error?.let { ErrorLine(apiErrorMessage(it)) }
                if (meta != null) content()
            }
        }
    }
}

@Composable
private fun SourceLine(sourceName: String, fetchedAt: Instant?) {
    val text = fetchedAt
        ?.let { stringResource(R.string.detail_source_with_age, sourceName, dataAgeText(it)) }
        ?: stringResource(R.string.detail_source, sourceName)
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(SourceDataFrameTags.SOURCE),
    )
}

@Composable
private fun dataAgeText(fetchedAt: Instant): String {
    val clock = LocalClock.current
    return when (val age = remember(fetchedAt, clock) { DataAge.of(fetchedAt, clock) }) {
        DataAge.JustNow -> stringResource(R.string.data_age_just_now)
        is DataAge.Minutes -> pluralStringResource(R.plurals.data_age_minutes, age.count.toQuantity(), age.count)
        is DataAge.Hours -> pluralStringResource(R.plurals.data_age_hours, age.count.toQuantity(), age.count)
        is DataAge.Days -> pluralStringResource(R.plurals.data_age_days, age.count.toQuantity(), age.count)
    }
}

private fun Long.toQuantity(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

@Composable
private fun statusNoticeText(status: DataStatus): String? = when (status) {
    DataStatus.FRESH, DataStatus.CACHED -> null
    DataStatus.LIMIT_REACHED -> stringResource(R.string.status_limit_reached)
    DataStatus.SOURCE_ERROR -> stringResource(R.string.status_source_error)
    DataStatus.NOT_SUPPORTED -> stringResource(R.string.status_not_supported)
    DataStatus.NO_DATA -> stringResource(R.string.status_no_data)
    DataStatus.NOT_REQUESTED -> stringResource(R.string.status_not_requested)
    DataStatus.UNKNOWN -> stringResource(R.string.status_unknown)
}

@Composable
private fun StatusNotice(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(SourceDataFrameTags.STATUS_NOTICE),
        )
    }
}

@Composable
private fun ErrorLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(SourceDataFrameTags.ERROR),
    )
}
