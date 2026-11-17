package com.sixteenscreener.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.DisplaySymbols
import com.sixteenscreener.app.domain.model.Company
import com.sixteenscreener.app.domain.model.CompanyIdentity
import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.Indicator
import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.MarketDataLoad
import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.RowMarketData
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.Trend
import com.sixteenscreener.app.ui.theme.SixteenTheme
import java.time.Instant

object CompanyRowTags {
    const val ROW = "company_row"
    const val NAME = "company_row_name"
    const val FLAG = "company_row_flag"
    const val PRICE = "company_row_price"
    const val CACHE_INDICATOR = "company_row_cache_indicator"
    const val INDICATORS = "company_row_indicators"
}

/**
 * Company row: logo | ticker over exchange | name (at most half the row width) with the market flag | [marketData] | [action].
 * [marketData] shows the price and the indicators to its right, e.g. [WatchlistMarketData]; search rows have none.
 * The row opens the detailed screen with [onClick]; it is not clickable when [onClick] is `null`.
 */
@Composable
fun CompanyRow(
    company: Company,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    marketData: (@Composable RowScope.() -> Unit)? = null,
    action: @Composable RowScope.() -> Unit = {},
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) {
                    Modifier.semantics(mergeDescendants = true) {}
                } else {
                    Modifier.clickable(onClickLabel = stringResource(R.string.company_open_details), onClick = onClick)
                },
            )
            .testTag(CompanyRowTags.ROW)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val nameMaxWidth = maxWidth / 2
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompanyLogo(
                logoUrl = company.logoUrl,
                contentDescription = stringResource(R.string.company_logo_description, company.name),
            )
            TickerAndExchange(ticker = company.ticker, exchange = company.exchange)
            NameAndFlag(
                name = company.name,
                market = company.market,
                nameMaxWidth = nameMaxWidth,
                modifier = Modifier.weight(1f),
            )
            marketData?.invoke(this)
            action()
        }
    }
}

@Composable
private fun TickerAndExchange(ticker: String, exchange: String) {
    Column {
        Text(text = ticker, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(
            text = exchange,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun NameAndFlag(
    name: String,
    market: Market?,
    nameMaxWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = nameMaxWidth)
                .testTag(CompanyRowTags.NAME),
        )
        DisplaySymbols.flagOf(market)?.let { flag ->
            val description = marketDescription(market)
            Text(
                text = flag,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .semantics { contentDescription = description }
                    .testTag(CompanyRowTags.FLAG),
            )
        }
    }
}

/** Spoken description of the flag of [market], empty without a market. */
@Composable
fun marketDescription(market: Market?): String = when (market) {
    Market.US -> stringResource(R.string.market_us)
    Market.EU -> stringResource(R.string.market_eu)
    null -> ""
}

@Preview(showBackground = true)
@Composable
private fun CompanyRowPreview() {
    SixteenTheme(dynamicColor = false) {
        Surface {
            CompanyRow(
                company = CompanyIdentity(Symbol("AAPL"), "AAPL", "NASDAQ", "Apple Inc.", Market.US, "/logos/AAPL"),
                onClick = {},
                marketData = {
                    WatchlistMarketData(
                        RowMarketData(
                            quote = Quote(189.84, "USD", Instant.now().minusSeconds(90_000), 187.5, Trend.UP, null),
                            indicators = listOf(
                                Indicator("ROA", "ROA", IndicatorLevel.GOOD),
                                Indicator("PE", "P/E", IndicatorLevel.WARNING),
                                Indicator("ROE", "ROE", IndicatorLevel.BAD),
                                Indicator("PB", "P/B", IndicatorLevel.BAD),
                            ),
                            lastLoad = MarketDataLoad.Loaded(DataStatus.CACHED),
                        ),
                    )
                },
            )
        }
    }
}
