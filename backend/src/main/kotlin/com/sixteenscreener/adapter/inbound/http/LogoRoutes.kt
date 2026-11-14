package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.domain.model.Logo
import com.sixteenscreener.port.inbound.LogoUseCase
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

object LogoCaching {
    const val LONG_LIVED = "public, max-age=2592000"
    const val SHORT_LIVED = "public, max-age=3600"

    fun cacheControlOf(logo: Logo): String = if (logo.isTemporary) SHORT_LIVED else LONG_LIVED
}

private val SVG = ContentType("image", "svg+xml")

fun Route.logoRoutes(logos: LogoUseCase) {
    get("/logos/{symbol}") {
        val logo = logos.logo(call.symbolParameter())
        call.response.header(HttpHeaders.CacheControl, LogoCaching.cacheControlOf(logo))
        call.respondBytes(logo.svg, SVG)
    }
}
