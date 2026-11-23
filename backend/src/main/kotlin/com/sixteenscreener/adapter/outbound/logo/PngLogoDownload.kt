package com.sixteenscreener.adapter.outbound.logo

import com.sixteenscreener.adapter.outbound.http.ErrorStatusMapping
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.domain.model.SourceResult
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray

class PngLogoDownload(
    private val call: SourceHttpCall,
    private val maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES,
) {

    suspend fun download(logoUrl: String): SourceResult<ByteArray> =
        call.execute(request = { url(logoUrl) }, errorStatus = errorStatus) { response -> pngOrNull(response) }

    private suspend fun pngOrNull(response: HttpResponse): ByteArray? {
        if ((response.contentLength() ?: 0) > maxSizeBytes) return null
        val content = response.bodyAsChannel().readBuffer(maxSizeBytes + 1).readByteArray()
        return content.takeIf { it.size <= maxSizeBytes && PngContent.isPng(it) }
    }

    companion object {
        const val DEFAULT_MAX_SIZE_BYTES: Long = 1024L * 1024

        private const val NOT_FOUND = 404

        private val errorStatus = ErrorStatusMapping.overriding(
            NOT_FOUND to { outcome -> SourceResult.NoData(outcome) },
        )
    }
}

object PngContent {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun isPng(content: ByteArray): Boolean =
        content.size > SIGNATURE.size && content.copyOfRange(0, SIGNATURE.size).contentEquals(SIGNATURE)
}
