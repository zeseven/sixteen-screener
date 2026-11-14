package com.sixteenscreener.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colors of a price compared to the previous cached price.
 */
@Immutable
data class PriceColors(
    /** Color of a price higher than the previous one. */
    val up: Color,
    /** Color of a price lower than the previous one. */
    val down: Color,
    /** Color of an unchanged price: the default text color of the theme. */
    val unchanged: Color,
)

internal fun priceColors(darkTheme: Boolean, defaultTextColor: Color) = if (darkTheme) {
    PriceColors(up = PriceUpDark, down = PriceDownDark, unchanged = defaultTextColor)
} else {
    PriceColors(up = PriceUpLight, down = PriceDownLight, unchanged = defaultTextColor)
}

internal val LocalPriceColors = staticCompositionLocalOf {
    PriceColors(up = Color.Unspecified, down = Color.Unspecified, unchanged = Color.Unspecified)
}
