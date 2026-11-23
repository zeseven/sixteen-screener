package com.sixteenscreener.adapter.outbound.finnhublogos

import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.logo.LogoTicker
import com.sixteenscreener.adapter.outbound.logo.PngLogoDownload
import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.ImageFormat
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.CompanyCache
import com.sixteenscreener.port.outbound.LogoProvider
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.net.URI

class FinnhubLogoClient(
    override val id: SourceId,
    baseUrl: String,
    call: SourceHttpCall,
    private val profiles: CompanyCache,
    private val profileSource: SourceId,
    maxSizeBytes: Long = PngLogoDownload.DEFAULT_MAX_SIZE_BYTES,
) : LogoProvider {

    override val format: ImageFormat = ImageFormat.PNG

    private val baseUrl = baseUrl.trimEnd('/')
    private val logoHost: String? = hostOf(this.baseUrl)
    private val pngDownload = PngLogoDownload(call, maxSizeBytes)

    override suspend fun logo(symbol: Symbol): SourceResult<ByteArray> = pngDownload.download(logoUrl(symbol))

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

    companion object {
        private const val HTTPS = "https"

        private val logger = LoggerFactory.getLogger(FinnhubLogoClient::class.java)

        private fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()
    }
}
