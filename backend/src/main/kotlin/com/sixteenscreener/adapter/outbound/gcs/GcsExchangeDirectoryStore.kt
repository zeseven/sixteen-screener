package com.sixteenscreener.adapter.outbound.gcs

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.port.outbound.ExchangeDirectory
import com.sixteenscreener.port.outbound.ExchangeDirectoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.channels.Channels

class GcsExchangeDirectoryStore(
    private val storage: Storage,
    private val bucket: String,
) : ExchangeDirectoryStore {

    override suspend fun load(source: SourceId, exchangeCode: ExchangeCode): ExchangeDirectory? =
        withContext(Dispatchers.IO) {
            val blob = storage.get(blobId(source, exchangeCode)) ?: return@withContext null
            Channels.newInputStream(blob.reader()).buffered().use(ExchangeDirectoryCodec::decode)
        }

    override suspend fun save(directory: ExchangeDirectory) {
        withContext(Dispatchers.IO) {
            val info = BlobInfo.newBuilder(blobId(directory.source, directory.exchangeCode))
                .setContentType(CONTENT_TYPE)
                .build()
            Channels.newOutputStream(storage.writer(info)).buffered().use { output ->
                ExchangeDirectoryCodec.encode(directory, output)
            }
        }
    }

    private fun blobId(source: SourceId, exchangeCode: ExchangeCode): BlobId =
        BlobId.of(bucket, "${GcsPaths.DIRECTORY}/$source/$exchangeCode.json")

    private companion object {
        private const val CONTENT_TYPE = "application/json"
    }
}
