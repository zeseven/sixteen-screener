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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.CacheIndicator
import com.sixteenscreener.app.domain.DisplaySymbols
import com.sixteenscreener.app.domain.NumberFormats
import com.sixteenscreener.app.domain.model.CompanySummary
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.Quote
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
}

/**
 * Company row: logo | ticker over exchange | name (at most half the row width) with the market flag | price over cache indicator | [action].
 */
@Composable
fun CompanyRow(
    company: CompanySummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.company_open_details), onClick = onClick)
            .testTag(CompanyRowTags.ROW)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val nameMaxWidth = maxWidth / 2
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            PriceAndCacheIndicator(quote = company.quote)
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

@Composable
private fun marketDescription(market: Market?): String = when (market) {
    Market.US -> stringResource(R.string.market_us)
    Market.EU -> stringResource(R.string.market_eu)
    null -> ""
}

@Composable
private fun PriceAndCacheIndicator(quote: Quote?) {
    Column(horizontalAlignment = Alignment.End) {
        if (quote == null) {
            val noPrice = stringResource(R.string.company_no_price)
            Text(
                text = NumberFormats.MISSING,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .semantics { contentDescription = noPrice }
                    .testTag(CompanyRowTags.PRICE),
            )
            SecondaryText(text = NumberFormats.MISSING, modifier = Modifier.testTag(CompanyRowTags.CACHE_INDICATOR))
        } else {
            QuotePrice(quote)
            QuoteCacheIndicator(quote)
        }
    }
}

@Composable
private fun QuotePrice(quote: Quote) {
    val price = NumberFormats.price(quote.price, quote.currency)
    val description = stringResource(R.string.company_price_description, price, stringResource(trendDescription(quote.trend)))
    Text(
        text = price,
        style = MaterialTheme.typography.bodyMedium,
        color = priceColor(quote.trend),
        maxLines = 1,
        modifier = Modifier
            .semantics { contentDescription = description }
            .testTag(CompanyRowTags.PRICE),
    )
}

@Composable
private fun QuoteCacheIndicator(quote: Quote) {
    val clock = LocalClock.current
    val days = remember(quote.fetchedAt, clock) { CacheIndicator.days(quote.fetchedAt, clock) } ?: return
    val description = if (days == 1) {
        stringResource(R.string.cache_indicator_today)
    } else {
        pluralStringResource(R.plurals.cache_indicator_days_ago, days - 1, days - 1)
    }
    SecondaryText(
        text = CacheIndicator.label(days),
        modifier = Modifier
            .semantics { contentDescription = description }
            .testTag(CompanyRowTags.CACHE_INDICATOR),
    )
}

@Composable
private fun SecondaryText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun priceColor(trend: Trend): Color = when (trend) {
    Trend.UP -> SixteenTheme.priceColors.up
    Trend.DOWN -> SixteenTheme.priceColors.down
    Trend.UNCHANGED -> SixteenTheme.priceColors.unchanged
}

private fun trendDescription(trend: Trend): Int = when (trend) {
    Trend.UP -> R.string.trend_up
    Trend.DOWN -> R.string.trend_down
    Trend.UNCHANGED -> R.string.trend_unchanged
}

@Preview(showBackground = true)
@Composable
private fun CompanyRowPreview() {
    SixteenTheme(dynamicColor = false) {
        Surface {
            CompanyRow(
                company = CompanySummary(
                    symbol = Symbol("AAPL"),
                    ticker = "AAPL",
                    exchange = "NASDAQ",
                    name = "Apple Inc.",
                    market = Market.US,
                    sector = "Technology",
                    logoUrl = "/logos/AAPL",
                    quote = Quote(189.84, "USD", Instant.now().minusSeconds(90_000), 187.5, Trend.UP, null),
                    inWatchlist = true,
                ),
                onClick = {},
                action = { CompanyDetailsButton(companyName = "Apple Inc.", onClick = {}) },
            )
        }
    }
}
