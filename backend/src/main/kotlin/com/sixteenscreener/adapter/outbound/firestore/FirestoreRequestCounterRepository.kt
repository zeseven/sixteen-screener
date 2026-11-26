package com.sixteenscreener.adapter.outbound.firestore

import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.google.cloud.firestore.Transaction
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.RequestUsage
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.port.outbound.RequestCounterRepository
import java.time.Instant

class FirestoreRequestCounterRepository(private val firestore: Firestore) : RequestCounterRepository {
    private val counters = firestore.collection(FirestoreCollections.REQUEST_COUNTERS)

    private data class Window(val limit: RequestLimit, val reference: DocumentReference, val start: Instant, val end: Instant)

    override suspend fun tryAcquire(source: SourceId, limits: List<RequestLimit>, requested: Int, now: Instant): Int {
        require(requested >= 0) { "Requested calls must not be negative" }
        if (limits.isEmpty()) return requested
        val windows = windows(source, limits, now)
        return firestore.transaction { tx ->
            val counts = tx.readCounts(windows)
            val remaining = windows.minOf { it.limit.remaining(counts.getValue(it.reference.id)) }
            val granted = minOf(requested.toLong(), remaining)
            if (granted > 0) {
                windows.distinctBy { it.reference.id }
                    .forEach { tx.writeCount(it, counts.getValue(it.reference.id) + granted) }
            }
            granted.toInt()
        }
    }

    override suspend fun saturate(source: SourceId, limits: List<RequestLimit>, now: Instant) {
        val limited = limits.filterNot { it.isUnlimited }
        if (limited.isEmpty()) return
        val windows = windows(source, limited, now)
        firestore.transaction { tx ->
            val counts = tx.readCounts(windows)
            windows.groupBy { it.reference.id }.values.forEach { sameWindow ->
                val window = sameWindow.first()
                val target = maxOf(counts.getValue(window.reference.id), sameWindow.maxOf { checkNotNull(it.limit.limit).toLong() })
                if (target != counts.getValue(window.reference.id)) tx.writeCount(window, target)
            }
        }
    }

    override suspend fun usage(source: SourceId, limits: List<RequestLimit>, now: Instant): List<RequestUsage> {
        if (limits.isEmpty()) return emptyList()
        val windows = windows(source, limits, now)
        val counts = firestore.getAll(*distinctReferences(windows)).await()
            .associate { it.id to (it.getLong(COUNT) ?: 0L) }
        return windows.map { RequestUsage(it.limit, counts.getOrDefault(it.reference.id, 0L).toInt(), it.end) }
    }

    override suspend fun incrementTotal(source: SourceId, calls: Int) {
        require(calls >= 0) { "Calls must not be negative" }
        if (calls == 0) return
        counters.document(totalDocumentId(source))
            .set(mapOf(SOURCE to source.value, COUNT to FieldValue.increment(calls.toLong())), SetOptions.merge())
            .await()
    }

    override suspend fun totals(sources: Collection<SourceId>): Map<SourceId, Long> {
        val distinctSources = sources.distinct()
        if (distinctSources.isEmpty()) return emptyMap()
        val counts = firestore.getAll(*distinctSources.map { counters.document(totalDocumentId(it)) }.toTypedArray()).await()
            .associate { it.id to (it.getLong(COUNT) ?: 0L) }
        return distinctSources.associateWith { counts.getOrDefault(totalDocumentId(it), 0L) }
    }

    private fun windows(source: SourceId, limits: List<RequestLimit>, now: Instant): List<Window> =
        limits.map { limit ->
            val start = limit.interval.windowStart(now)
            Window(limit, counters.document(documentId(source, limit.interval, start)), start, limit.interval.windowEnd(now))
        }

    private fun distinctReferences(windows: List<Window>): Array<DocumentReference> =
        windows.map { it.reference }.distinctBy { it.id }.toTypedArray()

    private fun Transaction.readCounts(windows: List<Window>): Map<String, Long> =
        getAll(*distinctReferences(windows)).get().associate { it.id to (it.getLong(COUNT) ?: 0L) }

    private fun Transaction.writeCount(window: Window, count: Long) {
        set(
            window.reference,
            mapOf(COUNT to count, WINDOW_START to window.start.toTimestamp(), EXPIRE_AT to window.end.toTimestamp()),
        )
    }

    companion object {
        private const val COUNT = "count"
        private const val SOURCE = "source"
        private const val TOTAL = "TOTAL"
        private const val WINDOW_START = "windowStart"
        private const val EXPIRE_AT = "expireAt"

        fun documentId(source: SourceId, interval: LimitInterval, windowStart: Instant): String =
            "${source}__${interval}__$windowStart"

        fun totalDocumentId(source: SourceId): String = "${source}__$TOTAL"
    }
}
