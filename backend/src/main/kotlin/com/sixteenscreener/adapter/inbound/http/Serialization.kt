package com.sixteenscreener.adapter.inbound.http

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

val ApiJson: Json = Json {
    explicitNulls = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(ApiJson)
    }
}
