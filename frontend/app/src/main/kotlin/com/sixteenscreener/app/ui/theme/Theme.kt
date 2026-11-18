package com.sixteenscreener.app.ui.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

@Composable
fun SixteenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = colorSchemeFor(darkTheme, dynamicColor)
    CompositionLocalProvider(
        LocalPriceColors provides priceColors(darkTheme, colorScheme.onSurface),
        LocalIndicatorColors provides indicatorColors(darkTheme),
        LocalCompanyColors provides companyColors(darkTheme),
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

object SixteenTheme {
    val priceColors: PriceColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPriceColors.current

    val indicatorColors: IndicatorColors
        @Composable
        @ReadOnlyComposable
        get() = LocalIndicatorColors.current

    val companyColors: CompanyColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCompanyColors.current
}

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
internal fun supportsDynamicColor() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
private fun colorSchemeFor(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme = when {
    dynamicColor && supportsDynamicColor() && darkTheme -> dynamicDarkColorScheme(LocalContext.current)
    dynamicColor && supportsDynamicColor() -> dynamicLightColorScheme(LocalContext.current)
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
}
