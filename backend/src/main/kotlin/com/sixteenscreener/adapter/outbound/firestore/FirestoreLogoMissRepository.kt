package com.sixteenscreener.adapter.outbound.firestore

import com.google.cloud.firestore.Firestore
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.LogoMissRepository
import java.time.Duration
import java.time.Instant

class FirestoreLogoMissRepository(firestore: Firestore) : LogoMissRepository {
    private val logoMisses = firestore.collection(FirestoreCollections.LOGO_MISSES)

    override suspend fun lastMiss(source: SourceId, symbol: Symbol): Instant? =
        logoMisses.document(documentId(source, symbol)).get().await()
            .takeIf { it.exists() }
            ?.getTimestamp(CHECKED_AT)
            ?.toInstant()

    override suspend fun recordMiss(source: SourceId, symbol: Symbol, checkedAt: Instant) {
        logoMisses.document(documentId(source, symbol)).set(
            mapOf(
                SOURCE to source.value,
                SYMBOL to symbol.value,
                CHECKED_AT to checkedAt.toTimestamp(),
                EXPIRE_AT to checkedAt.plus(MISS_TTL).toTimestamp(),
            ),
        ).await()
    }

    companion object {
        val MISS_TTL: Duration = Duration.ofDays(14)

        private const val SOURCE = "source"
        private const val SYMBOL = "symbol"
        private const val CHECKED_AT = "checkedAt"
        private const val EXPIRE_AT = "expireAt"

        fun documentId(source: SourceId, symbol: Symbol): String = "${source}__$symbol"
    }
}
