package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.port.inbound.ProfileUseCase
import com.sixteenscreener.port.inbound.WatchlistUseCase
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.watchlistRoutes(watchlist: WatchlistUseCase, profiles: ProfileUseCase) {
    route("/profiles/{profileId}/watchlist") {
        get {
            val items = watchlist.watchlist(call.profileIdParameter()).map { it.toDto() }
            call.respond(WatchlistDto(items))
        }
        get("/market-data") {
            val rows = watchlist.marketData(call.profileIdParameter(), call.symbolsParameter())
            call.respond(WatchlistMarketDataDto(rows.map { it.toDto() }))
        }
        put("/{symbol}") {
            val request = call.receiveOptional<WatchlistAddDto>()
            call.respond(profiles.addToWatchlist(call.profileIdParameter(), call.symbolParameter(), request?.name).toDto())
        }
        delete("/{symbol}") {
            profiles.removeFromWatchlist(call.profileIdParameter(), call.symbolParameter())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private suspend inline fun <reified T : Any> ApplicationCall.receiveOptional(): T? =
    if (request.hasBody()) receiveNullable<T>() else null

private fun ApplicationRequest.hasBody(): Boolean =
    contentLength()?.let { it > 0 } ?: (headers[HttpHeaders.TransferEncoding] != null)
