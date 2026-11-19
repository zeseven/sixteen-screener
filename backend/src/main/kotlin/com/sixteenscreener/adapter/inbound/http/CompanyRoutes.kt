package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.RequestTrigger
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.inbound.CompanyDataUseCase
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

private data class CompanyDataRequest(
    val profileId: ProfileId,
    val symbol: Symbol,
    val source: SourceId?,
    val trigger: RequestTrigger,
)

private fun ApplicationCall.companyDataRequest() = CompanyDataRequest(
    profileId = profileIdParameter(),
    symbol = symbolParameter(),
    source = sourceParameter(),
    trigger = triggerParameter(),
)

fun Route.companyRoutes(companyData: CompanyDataUseCase) {
    route("/profiles/{profileId}/companies/{symbol}") {
        get("/price-ratios") {
            val (profileId, symbol, source, trigger) = call.companyDataRequest()
            call.respond(companyData.priceAndRatios(profileId, symbol, source, trigger).toDto())
        }
        get("/dividends") {
            val (profileId, symbol, source, trigger) = call.companyDataRequest()
            call.respond(companyData.dividends(profileId, symbol, source, trigger).toDto())
        }
        get("/balance-sheet") {
            val (profileId, symbol, source, trigger) = call.companyDataRequest()
            call.respond(companyData.balanceSheet(profileId, symbol, source, trigger).toDto())
        }
        get("/profile") {
            val (profileId, symbol, source, trigger) = call.companyDataRequest()
            call.respond(companyData.companyProfile(profileId, symbol, source, trigger).toDto())
        }
    }
}
