package com.sixteenscreener.app.ui.detail.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.NumberFormats
import com.sixteenscreener.app.domain.model.CompanyProfile
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.ui.theme.SixteenTheme
import java.time.LocalDate

object CompanyProfileTags {
    const val FIELD = "company_profile_field"
    const val WEBSITE = "company_profile_website"
    const val DESCRIPTION = "company_profile_description"
    const val DESCRIPTION_TITLE = "company_profile_description_title"
}

/** "Company profile" tab: every field of the profile, `-` for missing values, the website as a link. */
@Composable
fun CompanyProfileTab(profile: CompanyProfile?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Field(R.string.profile_field_country, profile?.country)
        Field(R.string.profile_field_currency, profile?.currency)
        Field(R.string.profile_field_sector, profile?.sector)
        Field(R.string.profile_field_industry, profile?.industry)
        Field(R.string.profile_field_ipo_date, profile?.ipoDate?.toString())
        Field(R.string.profile_field_market_capitalization, profile?.marketCapitalization?.let(NumberFormats::abbreviated))
        Field(R.string.profile_field_shares_outstanding, profile?.sharesOutstanding?.let(NumberFormats::abbreviated))
        Field(R.string.profile_field_employees, profile?.employees?.let(NumberFormats::integer))
        FieldRow(R.string.profile_field_website) {
            Text(
                text = profile?.website?.takeIf { it.isNotBlank() }?.let { websiteLink(it) } ?: AnnotatedString(NumberFormats.MISSING),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.testTag(CompanyProfileTags.WEBSITE),
            )
        }
        Text(
            text = stringResource(R.string.profile_field_description),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .semantics { heading() }
                .testTag(CompanyProfileTags.DESCRIPTION_TITLE),
        )
        Text(
            text = profile?.description?.takeIf { it.isNotBlank() } ?: NumberFormats.MISSING,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(16.dp)
                .testTag(CompanyProfileTags.DESCRIPTION),
        )
    }
}

@Composable
private fun websiteLink(website: String): AnnotatedString {
    val url = if ("://" in website) website else "https://$website"
    val linkStyles = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        withLink(LinkAnnotation.Url(url, linkStyles)) { append(website) }
    }
}

@Composable
private fun Field(label: Int, value: String?) {
    FieldRow(label) {
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: NumberFormats.MISSING,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun FieldRow(label: Int, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(CompanyProfileTags.FIELD),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        value()
    }
    HorizontalDivider()
}

@Preview(showBackground = true)
@Composable
private fun CompanyProfileTabPreview() {
    SixteenTheme(dynamicColor = false) {
        CompanyProfileTab(
            CompanyProfile(
                symbol = Symbol("AAPL"),
                ticker = "AAPL",
                exchange = "NASDAQ",
                name = "Apple Inc.",
                market = Market.US,
                country = "US",
                currency = "USD",
                sector = "Technology",
                industry = "Consumer Electronics",
                ipoDate = LocalDate.of(1980, 12, 12),
                marketCapitalization = 3.5e12,
                sharesOutstanding = 1.52e10,
                employees = 164_000,
                website = "https://www.apple.com",
                description = "Designs smartphones.",
            ),
        )
    }
}
