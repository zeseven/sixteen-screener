package com.sixteenscreener.adapter.outbound.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.CoreConstants
import ch.qos.logback.core.LayoutBase
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class CloudLoggingJsonLayout : LayoutBase<ILoggingEvent>() {

    override fun doLayout(event: ILoggingEvent): String =
        buildJsonObject {
            put("severity", severityOf(event.level))
            put("message", event.formattedMessage)
            put("timestamp", Instant.ofEpochMilli(event.timeStamp).toString())
            put("logger", event.loggerName)
            put("thread", event.threadName)
            event.throwableProxy?.let { put("stack_trace", ThrowableProxyUtil.asString(it)) }
            event.mdcPropertyMap.orEmpty().forEach { (key, value) -> put(key, JsonPrimitive(value)) }
        }.toString() + CoreConstants.LINE_SEPARATOR

    private fun severityOf(level: Level): String = when (level.toInt()) {
        Level.ERROR_INT -> "ERROR"
        Level.WARN_INT -> "WARNING"
        Level.INFO_INT -> "INFO"
        else -> "DEBUG"
    }
}
