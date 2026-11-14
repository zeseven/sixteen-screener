package com.sixteenscreener.adapter.inbound.http

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level

fun Application.configureCallLogging() {
    install(CallLogging) {
        level = Level.INFO
        disableDefaultColors()
        mdc("httpMethod") { it.request.httpMethod.value }
        mdc("path") { it.request.path() }
    }
}
