package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.port.inbound.ProfileUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.profileRoutes(profiles: ProfileUseCase) {
    route("/profiles") {
        post {
            val request = call.receive<ProfileCreateDto>()
            val registration = profiles.getOrCreate(request.name)
            val status = if (registration.created) HttpStatusCode.Created else HttpStatusCode.OK
            call.respond(status, registration.profile.toDto())
        }
        get("/{profileId}") {
            call.respond(profiles.profile(call.profileIdParameter()).toDto())
        }
    }
}
