package com.sixteenscreener.app.data.remote.logo

import coil3.map.Mapper
import coil3.request.Options
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Resolves backend-relative image URLs such as `logoUrl` (`/logos/AAPL`) against the backend URL; other data is left unchanged.
 */
class BackendRelativeUrlMapper(backendUrl: String) : Mapper<String, String> {

    private val baseUrl = backendUrl.toHttpUrl()

    override fun map(data: String, options: Options): String? = resolve(data)

    fun resolve(url: String): String? =
        if (url.startsWith("/") && !url.startsWith("//")) baseUrl.resolve(url)?.toString() else null
}
