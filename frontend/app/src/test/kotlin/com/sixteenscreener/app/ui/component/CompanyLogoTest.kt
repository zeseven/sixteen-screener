package com.sixteenscreener.app.ui.component

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.sixteenscreener.app.di.ImageModule
import com.sixteenscreener.app.testing.LogoImages
import com.sixteenscreener.app.ui.theme.SixteenTheme
import dagger.Lazy
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class CompanyLogoTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val server = MockWebServer()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var imageLoader: ImageLoader

    @Before
    fun setUp() {
        server.start()
        imageLoader = ImageModule.provideImageLoader(
            context = context,
            httpClient = Lazy { OkHttpClient() },
            backendUrl = server.url("/").toString().trimEnd('/'),
        )
        SingletonImageLoader.setUnsafe(imageLoader)
    }

    @After
    fun tearDown() {
        SingletonImageLoader.reset()
        imageLoader.shutdown()
        imageLoader.diskCache?.clear()
        server.close()
    }

    private data class Bounds(val left: Int, val top: Int, val width: Int, val height: Int)

    private fun showLogo(response: MockResponse, darkTheme: Boolean = false) {
        server.enqueue(response)
        composeRule.setContent {
            SixteenTheme(darkTheme = darkTheme, dynamicColor = false) {
                CompanyLogo(logoUrl = "/logos/TEST", contentDescription = null, modifier = Modifier.testTag(TAG))
            }
        }
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun drawnLogoBounds(response: MockResponse): Bounds {
        showLogo(response)
        composeRule.waitUntil(timeoutMillis = 5_000) { capture().opaqueLogoPixels() > 0 }
        settle()
        return capture().logoBounds()
    }

    private fun drawnBlackLogo(darkTheme: Boolean): ImageBitmap {
        showLogo(LogoImages.blackSquareSvgResponse(), darkTheme)
        composeRule.waitUntil(timeoutMillis = 5_000) { capture().pixel(SLOT_PX / 2, SLOT_PX / 2) == BLACK }
        settle()
        return capture()
    }

    private fun ImageBitmap.pixel(x: Int, y: Int): Int = toPixelMap()[x, y].toArgb()

    private fun capture(): ImageBitmap = composeRule.onNodeWithTag(TAG).captureToImage()

    private fun ImageBitmap.opaqueLogoPixels(): Int {
        val pixels = toPixelMap()
        return (0 until width).sumOf { x -> (0 until height).count { y -> pixels[x, y].toArgb() == LogoImages.COLOR } }
    }

    private fun ImageBitmap.logoBounds(): Bounds {
        val pixels = toPixelMap()
        val logoPixels = (0 until width).flatMap { x -> (0 until height).map { y -> x to y } }
            .filter { (x, y) -> pixels[x, y].toArgb() == LogoImages.COLOR }
        val left = logoPixels.minOf { it.first }
        val top = logoPixels.minOf { it.second }
        return Bounds(left, top, logoPixels.maxOf { it.first } - left + 1, logoPixels.maxOf { it.second } - top + 1)
    }

    @Test
    fun `slot is 40 dp in device pixels`() {
        drawnLogoBounds(LogoImages.svgResponse(10, 10))

        val slot = capture()
        assertEquals(SLOT_PX to SLOT_PX, slot.width to slot.height)
    }

    @Test
    fun `SVG logo fills the slot`() {
        val bounds = drawnLogoBounds(LogoImages.svgResponse(10, 10))

        assertEquals(SLOT_PX, bounds.width)
        assertEquals(SLOT_PX, bounds.height)
    }

    @Test
    fun `wide SVG wordmark fits the slot width and is centered vertically`() {
        val bounds = drawnLogoBounds(LogoImages.svgResponse(100, 25))

        assertEquals(Bounds(left = 0, top = (SLOT_PX - SLOT_PX / 4) / 2, width = SLOT_PX, height = SLOT_PX / 4), bounds)
    }

    @Test
    fun `PNG logo larger than the slot is scaled down to fit`() {
        val bounds = drawnLogoBounds(LogoImages.pngResponse(480, 240))

        assertEquals(Bounds(left = 0, top = SLOT_PX / 4, width = SLOT_PX, height = SLOT_PX / 2), bounds)
    }

    @Test
    fun `PNG logo smaller than the slot keeps its pixel size and is centered`() {
        val bounds = drawnLogoBounds(LogoImages.pngResponse(60, 40))

        assertEquals(Bounds(left = 30, top = 40, width = 60, height = 40), bounds)
    }

    @Test
    fun `black logo is drawn on a white tile in dark theme`() {
        val slot = drawnBlackLogo(darkTheme = true)

        assertEquals(BLACK, slot.pixel(SLOT_PX / 2, SLOT_PX / 2))
        assertEquals(WHITE, slot.pixel(TILE_X, SLOT_PX / 2))
    }

    @Test
    fun `black logo is drawn on a white tile in light theme`() {
        val slot = drawnBlackLogo(darkTheme = false)

        assertEquals(BLACK, slot.pixel(SLOT_PX / 2, SLOT_PX / 2))
        assertEquals(WHITE, slot.pixel(TILE_X, SLOT_PX / 2))
    }

    @Test
    fun `default logo is drawn on a white tile`() {
        showLogo(MockResponse.Builder().code(500).build(), darkTheme = true)
        composeRule.waitUntil(timeoutMillis = 5_000) { server.requestCount > 0 }
        settle()

        val slot = capture()
        assertEquals(DEFAULT_LOGO_GRAY, slot.pixel(DEFAULT_LOGO_X, DEFAULT_LOGO_Y))
        assertEquals(WHITE, slot.pixel(TILE_X, SLOT_PX / 2))
    }

    private companion object {
        const val TAG = "logo"
        const val SLOT_PX = 120
        const val TILE_X = 5
        const val DEFAULT_LOGO_X = 35
        const val DEFAULT_LOGO_Y = 20
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val DEFAULT_LOGO_GRAY = 0xFF9E9E9E.toInt()
    }
}
