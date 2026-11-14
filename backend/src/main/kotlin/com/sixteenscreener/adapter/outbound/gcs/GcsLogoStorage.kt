package com.sixteenscreener.adapter.outbound.gcs

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.LogoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GcsLogoStorage(
    private val storage: Storage,
    private val bucket: String,
) : LogoStorage {

    override suspend fun find(symbol: Symbol): ByteArray? =
        withContext(Dispatchers.IO) { storage.get(blobId(symbol))?.getContent() }

    override suspend fun save(symbol: Symbol, svg: ByteArray) {
        withContext(Dispatchers.IO) {
            storage.create(BlobInfo.newBuilder(blobId(symbol)).setContentType(CONTENT_TYPE).build(), svg)
        }
    }

    private fun blobId(symbol: Symbol): BlobId = BlobId.of(bucket, "${GcsPaths.LOGOS}/$symbol.svg")

    companion object {
        const val CONTENT_TYPE = "image/svg+xml"
    }
}
