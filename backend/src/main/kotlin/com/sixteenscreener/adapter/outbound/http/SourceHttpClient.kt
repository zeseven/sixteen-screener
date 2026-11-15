package com.sixteenscreener.adapter.outbound.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object SourceHttpClient {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = true
    }

    fun create(engine: HttpClientEngine = CIO.create()): HttpClient =
        HttpClient(engine) {
            expectSuccess = false
            followRedirects = true
            install(HttpTimeout)
            install(ContentNegotiation) { json(json) }
        }
}
