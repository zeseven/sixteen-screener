package com.sixteenscreener.app.data.remote.logo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.sixteenscreener.app.di.ImageModule
import dagger.Lazy
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LogoImageLoaderTest {

    private val server = MockWebServer()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var imageLoader: ImageLoader

    private val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><rect width="10" height="10" fill="#00f"/></svg>"""

    @Before
    fun setUp() {
        server.start()
        val authenticatedClient = OkHttpClient.Builder()
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer test-token").build()) }
            .build()
        imageLoader = ImageModule.provideImageLoader(
            context = context,
            httpClient = Lazy { authenticatedClient },
            backendUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    @After
    fun tearDown() {
        imageLoader.shutdown()
        imageLoader.diskCache?.clear()
        server.close()
    }

    private fun request(data: String) = ImageRequest.Builder(context).data(data).build()

    @Test
    fun `relative logo URL is loaded from the backend with the authenticated client`() = runTest {
        server.enqueue(MockResponse.Builder().body(svg).setHeader("Content-Type", "image/svg+xml").build())

        val result = imageLoader.execute(request("/logos/AAPL"))

        val recorded = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/logos/AAPL", recorded.target)
        assertEquals("Bearer test-token", recorded.headers["Authorization"])
        assertTrue("Expected success but was $result", result is SuccessResult)
    }

    @Test
    fun `undecodable logo is an error so the default logo is shown`() = runTest {
        server.enqueue(MockResponse.Builder().body("not an image").setHeader("Content-Type", "image/svg+xml").build())

        assertTrue(imageLoader.execute(request("/logos/BAD")) is ErrorResult)
    }

    @Test
    fun `missing logo is an error so the default logo is shown`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())

        assertTrue(imageLoader.execute(request("/logos/MISSING")) is ErrorResult)
    }
}
