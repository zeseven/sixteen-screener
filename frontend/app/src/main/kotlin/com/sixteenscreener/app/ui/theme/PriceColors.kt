package com.sixteenscreener.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colors of market data values: a price compared to the previous cached price, and values whose last load failed.
 * They don't follow the dynamic color scheme.
 */
@Immutable
data class PriceColors(
    /** Color of a price higher than the previous one. */
    val up: Color,
    /** Color of a price lower than the previous one. */
    val down: Color,
    /** Color of an unchanged price: the default text color of the theme. */
    val unchanged: Color,
    /** Color of a price or indicators whose last load failed (orange). */
    val loadFailed: Color,
)

internal fun priceColors(darkTheme: Boolean, defaultTextColor: Color) = if (darkTheme) {
    PriceColors(up = PriceUpDark, down = PriceDownDark, unchanged = defaultTextColor, loadFailed = LoadFailedDark)
} else {
    PriceColors(up = PriceUpLight, down = PriceDownLight, unchanged = defaultTextColor, loadFailed = LoadFailedLight)
}

internal val LocalPriceColors = staticCompositionLocalOf {
    PriceColors(up = Color.Unspecified, down = Color.Unspecified, unchanged = Color.Unspecified, loadFailed = Color.Unspecified)
}
