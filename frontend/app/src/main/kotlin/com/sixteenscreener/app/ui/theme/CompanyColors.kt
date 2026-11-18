package com.sixteenscreener.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colors of company rows. They don't follow the dynamic color scheme.
 */
@Immutable
data class CompanyColors(
    /** Color of the name of a security that is not a stock, e.g. an ADR or a preference share (pale lavender). */
    val notStock: Color,
)

internal fun companyColors(darkTheme: Boolean) = CompanyColors(notStock = if (darkTheme) NotStockNameDark else NotStockNameLight)

internal val LocalCompanyColors = staticCompositionLocalOf { CompanyColors(notStock = Color.Unspecified) }
