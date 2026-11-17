package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.AppServices
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun Application.configureRouting(services: AppServices) {
    routing {
        profileRoutes(services.profileUseCase)
        watchlistRoutes(services.watchlistUseCase, services.profileUseCase)
        searchRoutes(services.searchUseCase)
        companyRoutes(services.companyDataUseCase)
        sourceRoutes(services.sourcesUseCase)
        logoRoutes(services.logoUseCase)
        exchangeRoutes(services.exchangesUseCase)
        indicatorRoutes(services.indicatorSettingsUseCase)
    }
}
