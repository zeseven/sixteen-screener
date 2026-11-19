package com.sixteenscreener.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sixteenscreener.app.domain.model.IndicatorLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class SixteenThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var colorScheme: ColorScheme
    private lateinit var priceColors: PriceColors
    private lateinit var indicatorColors: IndicatorColors
    private lateinit var companyColors: CompanyColors

    private fun captureTheme(darkTheme: Boolean, dynamicColor: Boolean) {
        composeRule.setContent {
            SixteenTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                colorScheme = MaterialTheme.colorScheme
                priceColors = SixteenTheme.priceColors
                indicatorColors = SixteenTheme.indicatorColors
                companyColors = SixteenTheme.companyColors
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `light theme without dynamic colors uses static light scheme`() {
        captureTheme(darkTheme = false, dynamicColor = false)

        assertEquals(LightColorScheme, colorScheme)
    }

    @Test
    fun `dark theme without dynamic colors uses static dark scheme`() {
        captureTheme(darkTheme = true, dynamicColor = false)

        assertEquals(DarkColorScheme, colorScheme)
    }

    @Test
    @Config(sdk = [30])
    fun `dynamic colors fall back to static scheme before Android 12`() {
        captureTheme(darkTheme = true, dynamicColor = true)

        assertEquals(DarkColorScheme, colorScheme)
    }

    @Test
    @Config(sdk = [31])
    fun `dynamic colors replace static scheme on Android 12`() {
        captureTheme(darkTheme = false, dynamicColor = true)

        assertNotEquals(LightColorScheme, colorScheme)
    }

    @Test
    fun `light theme price colors use light variants and black for unchanged`() {
        captureTheme(darkTheme = false, dynamicColor = false)

        assertEquals(PriceColors(up = PriceUpLight, down = PriceDownLight, unchanged = LightColorScheme.onSurface, loadFailed = LoadFailedLight), priceColors)
        assertEquals(Color.Black, priceColors.unchanged)
    }

    @Test
    fun `dark theme price colors use dark variants and white for unchanged`() {
        captureTheme(darkTheme = true, dynamicColor = false)

        assertEquals(PriceColors(up = PriceUpDark, down = PriceDownDark, unchanged = DarkColorScheme.onSurface, loadFailed = LoadFailedDark), priceColors)
        assertEquals(Color.White, priceColors.unchanged)
    }

    @Test
    @Config(sdk = [31])
    fun `dynamic colors do not change the price and load failure colors`() {
        captureTheme(darkTheme = true, dynamicColor = true)

        assertEquals(PriceUpDark, priceColors.up)
        assertEquals(PriceDownDark, priceColors.down)
        assertEquals(LoadFailedDark, priceColors.loadFailed)
    }

    @Test
    fun `indicator colors reuse the price green and red and add a yellow per theme`() {
        captureTheme(darkTheme = false, dynamicColor = false)
        assertEquals(IndicatorColors(PriceUpLight, IndicatorWarningLight, PriceDownLight, IndicatorNeutralLight), indicatorColors)
    }

    @Test
    fun `dark theme indicator colors use the dark variants`() {
        captureTheme(darkTheme = true, dynamicColor = false)
        assertEquals(IndicatorColors(PriceUpDark, IndicatorWarningDark, PriceDownDark, IndicatorNeutralDark), indicatorColors)
    }

    @Test
    fun `indicator yellow is readable on the background and differs from the load failure orange`() {
        assertTrue(contrast(IndicatorWarningLight, LightColorScheme.surface) >= 4.5)
        assertTrue(contrast(IndicatorWarningDark, DarkColorScheme.surface) >= 4.5)
        assertTrue(hueDistance(IndicatorWarningLight, LoadFailedLight) >= 15f)
        assertTrue(hueDistance(IndicatorWarningDark, LoadFailedDark) >= 15f)
    }

    @Test
    fun `company colors use a lavender not stock name and a white logo tile`() {
        captureTheme(darkTheme = false, dynamicColor = false)
        assertEquals(CompanyColors(notStock = Color(0xFF7F5FA8), logoBackground = Color.White), companyColors)
    }

    @Test
    fun `dark theme company colors use a pale lavender not stock name and a white logo tile`() {
        captureTheme(darkTheme = true, dynamicColor = false)
        assertEquals(CompanyColors(notStock = Color(0xFFC9B3EE), logoBackground = Color.White), companyColors)
    }

    @Test
    fun `not stock name color is readable on the background`() {
        assertTrue(contrast(NotStockNameLight, LightColorScheme.surface) >= 4.5)
        assertTrue(contrast(NotStockNameDark, DarkColorScheme.surface) >= 4.5)
    }

    @Test
    fun `indicator text colors cover only the displayed levels`() {
        val colors = IndicatorColors(Color.Green, Color.Yellow, Color.Red, Color.Gray)

        assertEquals(Color.Green, colors.textColorOf(IndicatorLevel.GOOD))
        assertEquals(Color.Yellow, colors.textColorOf(IndicatorLevel.WARNING))
        assertEquals(Color.Red, colors.textColorOf(IndicatorLevel.BAD))
        assertNull(colors.textColorOf(IndicatorLevel.NEUTRAL))
        assertNull(colors.textColorOf(IndicatorLevel.UNKNOWN))
        assertEquals(Color.Gray, colors.fillOf(IndicatorLevel.NEUTRAL))
        assertEquals(Color.Gray, colors.fillOf(IndicatorLevel.UNKNOWN))
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun hueDistance(first: Color, second: Color): Float = abs(hue(first) - hue(second))

    private fun hue(color: Color): Float = FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }[0]

    @Test
    fun `load failure color differs from the price colors in both themes`() {
        listOf(LoadFailedLight to listOf(PriceUpLight, PriceDownLight), LoadFailedDark to listOf(PriceUpDark, PriceDownDark))
            .forEach { (loadFailed, priceColors) -> assertTrue(loadFailed !in priceColors) }
        assertNotEquals(LoadFailedLight, LoadFailedDark)
    }
}
