package com.sixteenscreener.adapter.outbound.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

class CloudLoggingJsonLayoutTest : FunSpec({
    val logger = (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger("test.logger")
    val layout = CloudLoggingJsonLayout()

    fun event(level: Level, message: String, error: Throwable? = null) =
        LoggingEvent("test", logger, level, message, error, null).apply { timeStamp = 0 }

    fun render(event: LoggingEvent): JsonObject = Json.parseToJsonElement(layout.doLayout(event)).jsonObject

    context("maps levels to Cloud Logging severities") {
        withData(
            nameFn = { (level, severity) -> "$level -> $severity" },
            Level.ERROR to "ERROR",
            Level.WARN to "WARNING",
            Level.INFO to "INFO",
            Level.DEBUG to "DEBUG",
            Level.TRACE to "DEBUG",
        ) { (level, severity) ->
            render(event(level, "x"))["severity"]?.jsonPrimitive?.content shouldBe severity
        }
    }

    test("writes one JSON line with message, timestamp and logger") {
        val line = layout.doLayout(event(Level.INFO, "hello \"world\"\nnext"))
        line shouldEndWith "\n"
        line.trimEnd().lines().size shouldBe 1

        val json = render(event(Level.INFO, "hello \"world\"\nnext"))
        json["message"]?.jsonPrimitive?.content shouldBe "hello \"world\"\nnext"
        json["timestamp"]?.jsonPrimitive?.content shouldBe "1970-01-01T00:00:00Z"
        json["logger"]?.jsonPrimitive?.content shouldBe "test.logger"
        json["stack_trace"].shouldBeNull()
    }

    test("adds the stack trace of an exception") {
        val json = render(event(Level.ERROR, "failed", IllegalStateException("boom")))
        json["stack_trace"]?.jsonPrimitive?.content shouldContain "java.lang.IllegalStateException: boom"
    }
})
