package com.sixteenscreener.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.IndicatorsDisplay
import com.sixteenscreener.app.domain.MarketDataDisplay
import com.sixteenscreener.app.domain.MarketValueColor
import com.sixteenscreener.app.domain.NumberFormats
import com.sixteenscreener.app.domain.RowMarketDisplay
import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.RowMarketData
import com.sixteenscreener.app.ui.theme.SixteenTheme

/**
 * Price over cache indicator, then the indicators column to the right of the price, of a watchlist row, drawn by the
 * [MarketDataDisplay] rules.
 */
@Composable
fun RowScope.WatchlistMarketData(data: RowMarketData?) {
    val display = remember(data) { MarketDataDisplay.of(data) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.End) {
            MarketPriceText(display)
            display.quote?.let { QuoteCacheIndicator(it, modifier = Modifier.testTag(CompanyRowTags.CACHE_INDICATOR)) }
        }
        IndicatorsText(display.indicators)
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
private fun IndicatorsText(indicators: IndicatorsDisplay) {
    val description = indicatorsDescription(indicators).withLoadFailure(indicators.isLoadFailed)
    val text = indicatorLines(indicators)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (indicators.isLoadFailed) SixteenTheme.priceColors.loadFailed else SixteenTheme.priceColors.unchanged,
        maxLines = MarketDataDisplay.MAX_SHOWN_INDICATORS,
        softWrap = false,
        modifier = Modifier
            .semantics { contentDescription = description }
            .testTag(CompanyRowTags.INDICATORS),
    )
}

@Composable
private fun indicatorLines(indicators: IndicatorsDisplay): AnnotatedString {
    if (indicators.shown.isEmpty()) return AnnotatedString(NumberFormats.MISSING)
    val levelColors = indicators.shown.map { indicatorTextColor(it.level) }
    val lastLine = indicators.shown.last().name
        .let { if (indicators.hasMore) stringResource(R.string.company_indicators_more, it) else it }
    val lines = indicators.shown.dropLast(1).map { it.name } + lastLine
    return buildAnnotatedString {
        lines.forEachIndexed { index, line ->
            if (index > 0) append('\n')
            if (indicators.isLoadFailed) append(line) else withStyle(SpanStyle(color = levelColors[index])) { append(line) }
        }
    }
}

@Composable
private fun indicatorsDescription(indicators: IndicatorsDisplay): String {
    if (indicators.shown.isEmpty()) return stringResource(R.string.company_no_indicators)
    val named = indicators.shown.map { indicator ->
        stringResource(R.string.company_indicator_description, indicator.name, stringResource(indicatorLevelLabel(indicator.level)))
    }
    val listed = stringResource(R.string.company_indicators_description, named.joinToString(", "))
    return if (indicators.hasMore) stringResource(R.string.company_indicators_more_description, listed) else listed
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
