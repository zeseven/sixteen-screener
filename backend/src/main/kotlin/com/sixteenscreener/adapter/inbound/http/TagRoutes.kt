package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.port.inbound.TagsUseCase
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.tagRoutes(tags: TagsUseCase) {
    route("/profiles/{profileId}") {
        put("/companies/{symbol}/tags") {
            val profileId = call.profileIdParameter()
            val symbol = call.symbolParameter()
            val request = call.receive<TagListDto>()
            call.respond(tags.setCompanyTags(profileId, symbol, request.tags.map { it.toDomain() }).toTagListDto())
        }
        get("/tags") {
            call.respond(tags.profileTags(call.profileIdParameter()).toTagListDto())
        }
    }
}
