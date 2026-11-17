package com.sixteenscreener.app.ui.detail.priceratios

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.NumberFormats
import com.sixteenscreener.app.domain.model.DataStatus
import com.sixteenscreener.app.domain.model.Indicator
import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.PriceAndRatios
import com.sixteenscreener.app.domain.model.Quote
import com.sixteenscreener.app.domain.model.Ratio
import com.sixteenscreener.app.domain.model.RatioUnit
import com.sixteenscreener.app.domain.model.SourceDataMeta
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Trend
import com.sixteenscreener.app.ui.component.QuoteCacheIndicator
import com.sixteenscreener.app.ui.component.QuotePrice
import com.sixteenscreener.app.ui.component.indicatorLevelLabel
import com.sixteenscreener.app.ui.component.indicatorTextColor
import com.sixteenscreener.app.ui.component.ratioDisplayName
import com.sixteenscreener.app.ui.theme.SixteenTheme
import java.time.Instant

object PriceAndRatiosTags {
    const val PRICE = "price_and_ratios_price"
    const val CACHE_INDICATOR = "price_and_ratios_cache_indicator"
    const val RATIO = "price_and_ratios_ratio"
    const val RATIO_NAME = "price_and_ratios_ratio_name"
    const val RATIO_VALUE = "price_and_ratios_ratio_value"
    const val SECTION_TITLE = "price_and_ratios_section_title"
}

/**
 * "Price and ratio" tab: price with currency, trend color and cache indicator, then the ratio list. A ratio with an indicator is
 * drawn in the color of the indicator's level, the others in the default text color.
 */
@Composable
fun PriceAndRatiosTab(data: PriceAndRatios, modifier: Modifier = Modifier) {
    val indicatorLevels = remember(data.indicators) { data.indicators.associate { it.ratioCode to it.level } }
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SectionTitle(stringResource(R.string.detail_price))
        PriceLine(data.quote)
        SectionTitle(stringResource(R.string.detail_ratios))
        data.ratios.forEach { ratio ->
            RatioRow(ratio, indicatorLevels[ratio.code])
            HorizontalDivider()
        }
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
            .testTag(PriceAndRatiosTags.SECTION_TITLE),
    )
}

@Composable
private fun PriceLine(quote: Quote?) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        if (quote == null) {
            val noPrice = stringResource(R.string.company_no_price)
            Text(
                text = NumberFormats.MISSING,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { contentDescription = noPrice }.testTag(PriceAndRatiosTags.PRICE),
            )
        } else {
            QuotePrice(quote, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag(PriceAndRatiosTags.PRICE))
            QuoteCacheIndicator(quote, modifier = Modifier.padding(bottom = 4.dp).testTag(PriceAndRatiosTags.CACHE_INDICATOR))
        }
    }
}

@Composable
private fun RatioRow(ratio: Ratio, indicatorLevel: IndicatorLevel?) {
    val color = indicatorTextColor(indicatorLevel)
    val levelDescription = indicatorLevel?.let { stringResource(indicatorLevelLabel(it)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { levelDescription?.let { stateDescription = it } }
            .testTag(PriceAndRatiosTags.RATIO),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = ratioDisplayName(ratio.name, ratio.unit),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.testTag(PriceAndRatiosTags.RATIO_NAME),
        )
        Text(
            text = NumberFormats.decimal(ratio.value),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.testTag(PriceAndRatiosTags.RATIO_VALUE),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PriceAndRatiosTabPreview() {
    SixteenTheme(dynamicColor = false) {
        PriceAndRatiosTab(
            PriceAndRatios(
                meta = SourceDataMeta(SourceId.FINNHUB, DataStatus.CACHED, Instant.now()),
                quote = Quote(189.84, "USD", Instant.now(), 187.5, Trend.UP, SourceId.FINNHUB),
                ratios = listOf(Ratio("PE", "P/E", RatioUnit.RATIO, 31.2), Ratio("ROE", "Return on equity", RatioUnit.PERCENT, 18.4)),
                indicators = listOf(Indicator("ROE", "ROE", IndicatorLevel.GOOD), Indicator("PE", "P/E", IndicatorLevel.WARNING)),
            ),
        )
    }
}
