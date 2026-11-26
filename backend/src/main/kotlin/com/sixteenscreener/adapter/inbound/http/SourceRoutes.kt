package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.port.inbound.SourcesUseCase
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.sourceRoutes(sources: SourcesUseCase) {
    get("/profiles/{profileId}/sources") {
        call.respond(sources.sources(call.profileIdParameter()).toDto())
    }
    get("/profiles/{profileId}/companies/{symbol}/sources") {
        call.respond(sources.companySources(call.profileIdParameter(), call.symbolParameter()).toDto())
    }
}
