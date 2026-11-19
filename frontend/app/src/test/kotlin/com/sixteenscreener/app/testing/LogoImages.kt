package com.sixteenscreener.app.testing

import android.graphics.Bitmap
import mockwebserver3.MockResponse
import okio.Buffer
import java.io.ByteArrayOutputStream

object LogoImages {
    const val COLOR = 0xFFFF00FF.toInt()
    private const val SVG_COLOR = "#f0f"

    fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(COLOR) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    fun svg(width: Int, height: Int): ByteArray =
        """<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height"><rect width="$width" height="$height" fill="$SVG_COLOR"/></svg>"""
            .toByteArray()

    fun blackSquareSvg(): ByteArray =
        """<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 10 10"><rect x="3" y="3" width="4" height="4"/></svg>"""
            .toByteArray()

    fun pngResponse(width: Int, height: Int): MockResponse = response(png(width, height), "image/png")

    fun svgResponse(width: Int, height: Int): MockResponse = response(svg(width, height), "image/svg+xml")

    fun blackSquareSvgResponse(): MockResponse = response(blackSquareSvg(), "image/svg+xml")

    private fun response(body: ByteArray, contentType: String): MockResponse =
        MockResponse.Builder().body(Buffer().write(body)).setHeader("Content-Type", contentType).build()
}
