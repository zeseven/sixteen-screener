package com.sixteenscreener.adapter.outbound.firestore

import com.google.cloud.firestore.Firestore
import com.sixteenscreener.port.outbound.SecCikRepository
import java.time.Duration
import java.time.Instant

class FirestoreSecCikRepository(firestore: Firestore) : SecCikRepository {
    private val secCik = firestore.collection(FirestoreCollections.SEC_CIK)

    override suspend fun find(ticker: String, now: Instant): Long? {
        val snapshot = secCik.document(ticker).get().await().takeIf { it.exists() } ?: return null
        val expireAt = snapshot.getTimestamp(EXPIRE_AT)?.toInstant() ?: return null
        return snapshot.getLong(CIK)?.takeIf { now < expireAt }
    }

    override suspend fun save(ticker: String, cik: Long, storedAt: Instant) {
        secCik.document(ticker).set(mapOf(CIK to cik, EXPIRE_AT to storedAt.plus(TTL).toTimestamp())).await()
    }

    companion object {
        val TTL: Duration = Duration.ofDays(14)

        private const val CIK = "cik"
        private const val EXPIRE_AT = "expireAt"
    }
}
