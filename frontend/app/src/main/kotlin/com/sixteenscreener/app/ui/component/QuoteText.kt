package com.sixteenscreener.app.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.CacheIndicator
import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.Trend
import com.sixteenscreener.app.domain.NumberFormats
import com.sixteenscreener.app.ui.theme.SixteenTheme

/** Price with currency symbol in the trend color; the content description keeps the currency code and adds the trend. */
@Composable
fun QuotePrice(
    quote: Quote,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val price = NumberFormats.price(quote.price, quote.currency)
    val description = stringResource(
        R.string.company_price_description,
        NumberFormats.priceDescription(quote.price, quote.currency),
        stringResource(trendDescription(quote.trend)),
    )
    Text(
        text = price,
        style = style,
        color = priceColor(quote.trend),
        maxLines = 1,
        modifier = modifier.semantics { contentDescription = description },
    )
}

/** Cache indicator of [quote]; nothing for fresh data. */
@Composable
fun QuoteCacheIndicator(quote: Quote, modifier: Modifier = Modifier) {
    val clock = LocalClock.current
    val days = remember(quote.fetchedAt, clock) { CacheIndicator.days(quote.fetchedAt, clock) } ?: return
    val description = if (days == 1) {
        stringResource(R.string.cache_indicator_today)
    } else {
        pluralStringResource(R.plurals.cache_indicator_days_ago, days - 1, days - 1)
    }
    SecondaryText(
        text = CacheIndicator.label(days),
        modifier = modifier.semantics { contentDescription = description },
    )
}

/** Small text in the weaker color, like the exchange name and the cache indicator. */
@Composable
fun SecondaryText(text: String, modifier: Modifier = Modifier) {
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

internal fun trendDescription(trend: Trend): Int = when (trend) {
    Trend.UP -> R.string.trend_up
    Trend.DOWN -> R.string.trend_down
    Trend.UNCHANGED -> R.string.trend_unchanged
}
