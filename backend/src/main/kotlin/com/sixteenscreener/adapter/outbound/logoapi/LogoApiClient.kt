package com.sixteenscreener.adapter.outbound.logoapi

import com.sixteenscreener.adapter.outbound.http.ErrorStatusMapping
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.LogoProvider
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

class LogoApiClient(
    override val id: SourceId,
    baseUrl: String,
    private val call: SourceHttpCall,
    private val maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES,
) : LogoProvider {

    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun logo(symbol: Symbol): SourceResult<ByteArray> =
        call.execute(
            request = {
                url("$baseUrl/ticker/${tickerOf(symbol)}.svg")
                parameter(FALLBACK_PARAMETER, FALLBACK_NOT_FOUND)
            },
            errorStatus = errorStatus,
        ) { response -> svgOrNull(response) }

    private suspend fun svgOrNull(response: HttpResponse): ByteArray? {
        if (isFaviconFallback(response)) return null
        if ((response.contentLength() ?: 0) > maxSizeBytes) return null
        val content = response.bodyAsChannel().readRemaining(maxSizeBytes + 1).readByteArray()
        return content.takeIf { it.size <= maxSizeBytes && SvgContent.isSvg(it) }
    }

    private fun isFaviconFallback(response: HttpResponse): Boolean =
        response.headers[FALLBACK_HEADER]?.trim().equals(FAVICON_FALLBACK, ignoreCase = true)

    companion object {
        const val DEFAULT_MAX_SIZE_BYTES: Long = 512L * 1024
        const val FALLBACK_HEADER = "X-Logo-Fallback"
        const val FAVICON_FALLBACK = "favicon"

        private const val FALLBACK_PARAMETER = "fallback"
        private const val FALLBACK_NOT_FOUND = "404"
        private const val NOT_FOUND = 404

        private val errorStatus = ErrorStatusMapping.overriding(
            NOT_FOUND to { outcome -> SourceResult.NoData(outcome) },
        )

        fun tickerOf(symbol: Symbol): String =
            if (symbol.exchangeCode == ExchangeCode.US) symbol.ticker else symbol.value
    }
}

object SvgContent {
    private const val BYTE_ORDER_MARK = '﻿'
    private val LEADING_CHARACTERS = charArrayOf(BYTE_ORDER_MARK, ' ', '\t', '\r', '\n')

    fun isSvg(content: ByteArray): Boolean {
        val text = String(content, Charsets.UTF_8).trimStart(*LEADING_CHARACTERS)
        return text.startsWith("<svg") || (text.startsWith("<?xml") && text.contains("<svg"))
    }
}
