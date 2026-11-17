package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.port.inbound.ExchangesUseCase
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.exchangeRoutes(exchanges: ExchangesUseCase) {
    get("/exchanges") {
        call.respond(exchanges.exchanges().toDto())
    }
}
