package com.sixteenscreener.adapter.outbound.finnhublogos

import com.sixteenscreener.adapter.outbound.http.ErrorStatusMapping
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.logo.LogoTicker
import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.ImageFormat
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.CompanyCache
import com.sixteenscreener.port.outbound.LogoProvider
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import org.slf4j.LoggerFactory
import java.net.URI

class FinnhubLogoClient(
    override val id: SourceId,
    baseUrl: String,
    private val call: SourceHttpCall,
    private val profiles: CompanyCache,
    private val profileSource: SourceId,
    private val maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES,
) : LogoProvider {

    override val format: ImageFormat = ImageFormat.PNG

    private val baseUrl = baseUrl.trimEnd('/')
    private val logoHost: String? = hostOf(this.baseUrl)

    override suspend fun logo(symbol: Symbol): SourceResult<ByteArray> {
        val logoUrl = logoUrl(symbol)
        return call.execute(request = { url(logoUrl) }, errorStatus = errorStatus) { response -> pngOrNull(response) }
    }

    private suspend fun logoUrl(symbol: Symbol): String =
        cachedProfileLogoUrl(symbol)?.takeIf(::isOnLogoHost) ?: "$baseUrl/${LogoTicker.of(symbol)}.png"

    private suspend fun cachedProfileLogoUrl(symbol: Symbol): String? =
        try {
            profiles.get(CacheKey(profileSource, symbol, DataType.COMPANY_PROFILE))?.data?.logoUrl
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to read the cached company profile of '{}'", symbol, e)
            null
        }

    private fun isOnLogoHost(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        return uri.scheme.equals(HTTPS, ignoreCase = true) && logoHost != null && uri.host.equals(logoHost, ignoreCase = true)
    }

    private suspend fun pngOrNull(response: HttpResponse): ByteArray? {
        if ((response.contentLength() ?: 0) > maxSizeBytes) return null
        val content = response.bodyAsChannel().readBuffer(maxSizeBytes + 1).readByteArray()
        return content.takeIf { it.size <= maxSizeBytes && PngContent.isPng(it) }
    }

    companion object {
        const val DEFAULT_MAX_SIZE_BYTES: Long = 1024L * 1024

        private const val HTTPS = "https"
        private const val NOT_FOUND = 404

        private val logger = LoggerFactory.getLogger(FinnhubLogoClient::class.java)

        private val errorStatus = ErrorStatusMapping.overriding(
            NOT_FOUND to { outcome -> SourceResult.NoData(outcome) },
        )

        private fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()
    }
}

object PngContent {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun isPng(content: ByteArray): Boolean =
        content.size > SIGNATURE.size && content.copyOfRange(0, SIGNATURE.size).contentEquals(SIGNATURE)
}
