package com.sixteenscreener.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.MarketDataDisplay
import com.sixteenscreener.app.domain.MarketValueColor
import com.sixteenscreener.app.domain.NumberFormats
import com.sixteenscreener.app.domain.RowMarketDisplay
import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.RowMarketData
import com.sixteenscreener.app.ui.theme.SixteenTheme

/** Indicators and price over cache indicator of a watchlist row, drawn by the [MarketDataDisplay] rules. */
@Composable
fun RowScope.WatchlistMarketData(data: RowMarketData?) {
    val display = remember(data) { MarketDataDisplay.of(data) }
    IndicatorsText(display)
    Column(horizontalAlignment = Alignment.End) {
        MarketPriceText(display)
        display.quote?.let { QuoteCacheIndicator(it, modifier = Modifier.testTag(CompanyRowTags.CACHE_INDICATOR)) }
    }
}

/** Price over cache indicator of [quote] in the trend color; `-` without a quote. */
@Composable
fun QuotePriceAndCacheIndicator(quote: Quote?) {
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
        } else {
            QuotePrice(quote, modifier = Modifier.testTag(CompanyRowTags.PRICE))
            QuoteCacheIndicator(quote, modifier = Modifier.testTag(CompanyRowTags.CACHE_INDICATOR))
        }
    }
}

@Composable
private fun MarketPriceText(display: RowMarketDisplay) {
    val quote = display.quote
    val state = when {
        quote == null -> null
        display.isLastLoadFailed -> stringResource(R.string.market_data_load_failed)
        else -> stringResource(trendDescription(quote.trend))
    }
    val description = state?.let { stringResource(R.string.company_price_description, display.price.text, it) }
        ?: stringResource(R.string.company_no_price).withLoadFailure(display.isLastLoadFailed)
    Text(
        text = display.price.text,
        style = MaterialTheme.typography.bodyMedium,
        color = marketValueColor(display.price.color),
        maxLines = 1,
        modifier = Modifier
            .semantics { contentDescription = description }
            .testTag(CompanyRowTags.PRICE),
    )
}

@Composable
private fun IndicatorsText(display: RowMarketDisplay) {
    val indicators = display.indicators
    val description = if (indicators.text == NumberFormats.MISSING) {
        stringResource(R.string.company_no_indicators)
    } else {
        stringResource(R.string.company_indicators_description, indicators.text)
    }.withLoadFailure(display.isLastLoadFailed)
    Text(
        text = indicators.text,
        style = MaterialTheme.typography.bodySmall,
        color = marketValueColor(indicators.color),
        maxLines = 1,
        modifier = Modifier
            .semantics { contentDescription = description }
            .testTag(CompanyRowTags.INDICATORS),
    )
}

@Composable
private fun String.withLoadFailure(isFailed: Boolean): String {
    val failure = stringResource(R.string.market_data_load_failed)
    return if (isFailed) stringResource(R.string.company_value_load_failed_description, this, failure) else this
}

@Composable
private fun marketValueColor(color: MarketValueColor): Color = when (color) {
    MarketValueColor.DEFAULT -> SixteenTheme.priceColors.unchanged
    MarketValueColor.UP -> SixteenTheme.priceColors.up
    MarketValueColor.DOWN -> SixteenTheme.priceColors.down
    MarketValueColor.FAILED -> SixteenTheme.priceColors.loadFailed
}
