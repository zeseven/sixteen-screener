package com.sixteenscreener

import com.sixteenscreener.adapter.inbound.http.configureCallLogging
import com.sixteenscreener.adapter.inbound.http.configureRouting
import com.sixteenscreener.adapter.inbound.http.configureSerialization
import com.sixteenscreener.adapter.inbound.http.configureStatusPages
import com.sixteenscreener.adapter.outbound.time.SystemClock
import com.sixteenscreener.config.AppConfig
import com.sixteenscreener.config.AppConfigLoader
import com.sixteenscreener.config.InvalidConfigurationException
import com.sixteenscreener.port.outbound.Clock
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.embeddedServer
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

class AppServices(
    val config: AppConfig,
    val clock: Clock,
)

fun main() {
    val config = loadConfigOrExit()
    val services = wireServices(config)
    embeddedServer(CIO, port = config.port, host = "0.0.0.0") { module(services) }.start(wait = true)
}

fun wireServices(config: AppConfig): AppServices =
    AppServices(
        config = config,
        clock = SystemClock,
    )

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
