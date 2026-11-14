package com.sixteenscreener

import com.sixteenscreener.adapter.inbound.http.configureCallLogging
import com.sixteenscreener.adapter.inbound.http.configureRouting
import com.sixteenscreener.adapter.inbound.http.configureSerialization
import com.sixteenscreener.adapter.inbound.http.configureStatusPages
import com.sixteenscreener.adapter.outbound.firestore.FirestoreClientFactory
import com.sixteenscreener.adapter.outbound.firestore.FirestoreCompanyCache
import com.sixteenscreener.adapter.outbound.firestore.FirestoreLogoMissRepository
import com.sixteenscreener.adapter.outbound.firestore.FirestoreProfileRepository
import com.sixteenscreener.adapter.outbound.firestore.FirestoreRequestCounterRepository
import com.sixteenscreener.adapter.outbound.firestore.FirestoreSourceCallOutcomeRepository
import com.sixteenscreener.adapter.outbound.time.SystemClock
import com.sixteenscreener.config.AppConfig
import com.sixteenscreener.config.AppConfigLoader
import com.sixteenscreener.config.InvalidConfigurationException
import com.sixteenscreener.domain.service.RateLimiter
import com.sixteenscreener.domain.service.SourceCallGuard
import com.sixteenscreener.domain.service.SourceCallOutcomes
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.CompanyCache
import com.sixteenscreener.port.outbound.LogoMissRepository
import com.sixteenscreener.port.outbound.ProfileRepository
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.cio.CIO
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.embeddedServer
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

class AppServices(
    val config: AppConfig,
    val clock: Clock,
    val profiles: ProfileRepository,
    val companyCache: CompanyCache,
    val logoMisses: LogoMissRepository,
    val rateLimiter: RateLimiter,
    val sourceCallOutcomes: SourceCallOutcomes,
    val sourceCallGuard: SourceCallGuard,
    private val resources: List<AutoCloseable> = emptyList(),
) : AutoCloseable {
    override fun close() = resources.forEach(AutoCloseable::close)
}

fun main() {
    val config = loadConfigOrExit()
    val services = wireServices(config)
    embeddedServer(CIO, port = config.port, host = "0.0.0.0") {
        module(services)
        monitor.subscribe(ApplicationStopped) { services.close() }
    }.start(wait = true)
}

fun wireServices(config: AppConfig): AppServices {
    val clock = SystemClock
    val firestore = FirestoreClientFactory.create(config.gcpProjectId)
    val rateLimiter = RateLimiter(
        limitsBySource = config.sources.associate { it.id to it.limits },
        counters = FirestoreRequestCounterRepository(firestore),
        clock = clock,
    )
    return AppServices(
        config = config,
        clock = clock,
        profiles = FirestoreProfileRepository(firestore),
        companyCache = FirestoreCompanyCache(firestore),
        logoMisses = FirestoreLogoMissRepository(firestore),
        rateLimiter = rateLimiter,
        sourceCallOutcomes = SourceCallOutcomes(FirestoreSourceCallOutcomeRepository(firestore)),
        sourceCallGuard = SourceCallGuard(rateLimiter),
        resources = listOf(firestore),
    )
}

fun Application.module(services: AppServices) {
    configureCallLogging()
    configureSerialization()
    configureStatusPages()
    configureRouting(services)
}

private fun loadConfigOrExit(): AppConfig =
    try {
        AppConfigLoader.load(ApplicationConfig("application.conf"), System.getenv())
    } catch (e: InvalidConfigurationException) {
        LoggerFactory.getLogger("com.sixteenscreener.Application").error(e.message)
        exitProcess(1)
    }
