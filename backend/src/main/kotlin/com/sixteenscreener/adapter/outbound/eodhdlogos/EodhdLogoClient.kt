package com.sixteenscreener.adapter.outbound.eodhdlogos

import com.sixteenscreener.adapter.outbound.eodhd.EodhdClient
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.logo.PngLogoDownload
import com.sixteenscreener.domain.model.ImageFormat
import com.sixteenscreener.domain.model.KnownExchangeCodes
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.LogoProvider
import io.ktor.http.encodeURLPathPart

class EodhdLogoClient(
    override val id: SourceId,
    baseUrl: String,
    call: SourceHttpCall,
    maxSizeBytes: Long = PngLogoDownload.DEFAULT_MAX_SIZE_BYTES,
) : LogoProvider {

    override val format: ImageFormat = ImageFormat.PNG

    private val baseUrl = baseUrl.trimEnd('/')
    private val pngDownload = PngLogoDownload(call, maxSizeBytes)

    override suspend fun logo(symbol: Symbol): SourceResult<ByteArray> =
        logoUrl(symbol)?.let { pngDownload.download(it) } ?: NO_EODHD_EXCHANGE

    private fun logoUrl(symbol: Symbol): String? =
        KnownExchangeCodes.eodhdCodeOf(symbol.exchangeCode)?.let { exchange ->
            "$baseUrl/${exchange.encodeURLPathPart()}/${EodhdClient.eodhdTickerOf(symbol).encodeURLPathPart()}.png"
        }

    companion object {
        private val NO_EODHD_EXCHANGE = SourceResult.NoData(outcome = null)
    }
}
