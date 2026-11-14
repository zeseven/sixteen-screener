package com.sixteenscreener.adapter.outbound.firestore

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceCallResult
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.port.outbound.SourceCallOutcomeRepository
import org.slf4j.LoggerFactory

class FirestoreSourceCallOutcomeRepository(private val firestore: Firestore) : SourceCallOutcomeRepository {
    private val sourceCalls = firestore.collection(FirestoreCollections.SOURCE_CALLS)

    override suspend fun record(source: SourceId, outcome: SourceCallOutcome) {
        sourceCalls.document(source.value).set(
            mapOf(
                RESULT to outcome.result.name,
                HTTP_STATUS to outcome.httpStatus?.toLong(),
                AT to outcome.at.toTimestamp(),
            ),
        ).await()
    }

    override suspend fun findAll(sources: Collection<SourceId>): Map<SourceId, SourceCallOutcome> {
        val distinctSources = sources.distinct()
        if (distinctSources.isEmpty()) return emptyMap()
        val snapshots = firestore.getAll(*distinctSources.map { sourceCalls.document(it.value) }.toTypedArray()).await()
            .associateBy { it.id }
        return distinctSources.mapNotNull { source ->
            snapshots[source.value]?.toOutcomeOrNull()?.let { source to it }
        }.toMap()
    }

    private fun DocumentSnapshot.toOutcomeOrNull(): SourceCallOutcome? {
        if (!exists()) return null
        return try {
            SourceCallOutcome(
                result = SourceCallResult.valueOf(requiredString(RESULT)),
                httpStatus = getLong(HTTP_STATUS)?.toInt(),
                at = requiredInstant(AT),
            )
        } catch (e: RuntimeException) {
            logger.warn("Ignoring unreadable source call document {}", reference.path, e)
            null
        }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(FirestoreSourceCallOutcomeRepository::class.java)

        private const val RESULT = "result"
        private const val HTTP_STATUS = "httpStatus"
        private const val AT = "at"
    }
}
