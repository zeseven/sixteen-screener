package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.port.inbound.SearchUseCase
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.util.getOrFail

fun Route.searchRoutes(search: SearchUseCase) {
    get("/profiles/{profileId}/search") {
        val profileId = call.profileIdParameter()
        val query = call.request.queryParameters.getOrFail("query")
        call.respond(search.search(profileId, query).toDto())
    }
}
