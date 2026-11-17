package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.port.inbound.IndicatorSettingsUseCase
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.indicatorRoutes(indicatorSettings: IndicatorSettingsUseCase) {
    route("/profiles/{profileId}/indicator-settings") {
        get {
            call.respond(indicatorSettings.settings(call.profileIdParameter()).toDto())
        }
        put {
            val profileId = call.profileIdParameter()
            val request = call.receive<IndicatorSettingsUpdateDto>()
            call.respond(indicatorSettings.replace(profileId, request.settings.map { it.toDomain() }).toDto())
        }
    }
}
