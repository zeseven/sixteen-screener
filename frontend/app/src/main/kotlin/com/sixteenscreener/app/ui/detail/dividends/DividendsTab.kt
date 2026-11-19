package com.sixteenscreener.app.ui.detail.dividends

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.NumberFormats
import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.Dividend
import com.sixteenscreener.app.domain.model.Dividends
import com.sixteenscreener.app.domain.model.RatioUnit
import com.sixteenscreener.app.domain.model.SourceDataMeta
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.ui.component.ratioDisplayName
import com.sixteenscreener.app.ui.theme.SixteenTheme
import java.time.Instant
import java.time.LocalDate

object DividendsTags {
    const val YIELD = "dividends_yield"
    const val YIELD_VALUE = "dividends_yield_value"
    const val SECTION_TITLE = "dividends_section_title"
    const val DIVIDEND = "dividends_dividend"
    const val EX_DATE = "dividends_ex_date"
    const val PAYMENT_DATE = "dividends_payment_date"
    const val AMOUNT = "dividends_amount"
    const val EMPTY = "dividends_empty"
}

/**
 * "Dividends" tab: trailing twelve months dividend yield, then the dividends newest first with their ex-date, payment date and
 * amount per share; `-` for a missing yield or when there are no dividends.
 */
@Composable
fun DividendsTab(data: Dividends, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        YieldRow(data.dividendYield)
        HorizontalDivider()
        SectionTitle(stringResource(R.string.detail_dividends))
        if (data.dividends.isEmpty()) {
            Text(
                text = NumberFormats.MISSING,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag(DividendsTags.EMPTY),
            )
        }
        data.dividends.forEach { dividend ->
            DividendRow(dividend)
            HorizontalDivider()
        }
    }
}

@Composable
private fun YieldRow(dividendYield: Double?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(DividendsTags.YIELD),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = ratioDisplayName(stringResource(R.string.detail_dividend_yield), RatioUnit.PERCENT),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = NumberFormats.decimal(dividendYield),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(DividendsTags.YIELD_VALUE),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() }
            .testTag(DividendsTags.SECTION_TITLE),
    )
}

@Composable
private fun DividendRow(dividend: Dividend) {
    val description = stringResource(
        R.string.detail_dividend_description,
        dividend.exDate.toString(),
        NumberFormats.priceDescription(dividend.amount, dividend.currency),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag(DividendsTags.DIVIDEND),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(R.string.detail_dividend_ex_date, dividend.exDate.toString()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(DividendsTags.EX_DATE),
            )
            dividend.paymentDate?.let { paymentDate ->
                Text(
                    text = stringResource(R.string.detail_dividend_payment_date, paymentDate.toString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(DividendsTags.PAYMENT_DATE),
                )
            }
        }
        Text(
            text = NumberFormats.price(dividend.amount, dividend.currency),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(DividendsTags.AMOUNT),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DividendsTabPreview() {
    SixteenTheme(dynamicColor = false) {
        DividendsTab(
            Dividends(
                meta = SourceDataMeta(SourceId.EODHD, DataStatus.CACHED, Instant.now()),
                dividendYield = 1.12,
                dividends = listOf(
                    Dividend(LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 14), null, null, 2.35, "EUR"),
                    Dividend(LocalDate.of(2025, 5, 12), null, null, null, 2.2, "EUR"),
                ),
            ),
        )
    }
}
