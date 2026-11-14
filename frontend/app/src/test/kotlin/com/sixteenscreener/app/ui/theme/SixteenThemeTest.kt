package com.sixteenscreener.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class SixteenThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var colorScheme: ColorScheme
    private lateinit var priceColors: PriceColors

    private fun captureTheme(darkTheme: Boolean, dynamicColor: Boolean) {
        composeRule.setContent {
            SixteenTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                colorScheme = MaterialTheme.colorScheme
                priceColors = SixteenTheme.priceColors
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

        assertEquals(PriceColors(up = PriceUpLight, down = PriceDownLight, unchanged = LightColorScheme.onSurface), priceColors)
        assertEquals(Color.Black, priceColors.unchanged)
    }

    @Test
    fun `dark theme price colors use dark variants and white for unchanged`() {
        captureTheme(darkTheme = true, dynamicColor = false)

        assertEquals(PriceColors(up = PriceUpDark, down = PriceDownDark, unchanged = DarkColorScheme.onSurface), priceColors)
        assertEquals(Color.White, priceColors.unchanged)
    }
}
