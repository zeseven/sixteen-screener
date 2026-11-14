package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.port.inbound.ProfileUseCase
import com.sixteenscreener.port.inbound.WatchlistUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.watchlistRoutes(watchlist: WatchlistUseCase, profiles: ProfileUseCase) {
    route("/profiles/{profileId}/watchlist") {
        get {
            val items = watchlist.watchlist(call.profileIdParameter()).map { it.toDto(inWatchlist = true) }
            call.respond(WatchlistDto(items))
        }
        put("/{symbol}") {
            profiles.addToWatchlist(call.profileIdParameter(), call.symbolParameter())
            call.respond(HttpStatusCode.NoContent)
        }
        delete("/{symbol}") {
            profiles.removeFromWatchlist(call.profileIdParameter(), call.symbolParameter())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
