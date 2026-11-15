package com.sixteenscreener.adapter.outbound.firestore

import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.sixteenscreener.domain.model.CacheEntries
import com.sixteenscreener.domain.model.CacheEntry
import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.PreviousPrice
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.QuoteRecord
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.domain.service.FreshnessPolicy
import com.sixteenscreener.port.outbound.CompanyCache
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FirestoreCompanyCache(private val firestore: Firestore) : CompanyCache {
    private val sourceData = firestore.collection(FirestoreCollections.SOURCE_DATA)
    private val quoteHistory = firestore.collection(FirestoreCollections.QUOTE_HISTORY)

    override suspend fun <T : Any> get(key: CacheKey<T>): CacheEntry<T>? =
        reference(key).get().await().toEntryOrNull(key.type)

    override suspend fun getAll(keys: Collection<CacheKey<*>>): CacheEntries {
        val distinctKeys = keys.distinct()
        if (distinctKeys.isEmpty()) return CacheEntries(emptyMap())
        val snapshots = firestore.getAll(*distinctKeys.map(::reference).toTypedArray()).await().associateBy { it.id }
        val entries = distinctKeys.mapNotNull { key ->
            snapshots[documentId(key)]?.toEntryOrNull(key.type)?.let { key to it }
        }
        return CacheEntries(entries.toMap())
    }

    override suspend fun <T : Any> put(key: CacheKey<T>, entry: CacheEntry<T>) {
        require(key.type != DataType.QUOTE) { "Quotes must be written with putQuote" }
        reference(key).set(
            mapOf(
                PAYLOAD to CachePayloadCodec.encode(key.type, entry.data),
                FETCHED_AT to entry.fetchedAt.toTimestamp(),
                EXPIRE_AT to expireAt(key.type, entry.fetchedAt),
            ),
        ).await()
    }

    override suspend fun putQuote(
        source: SourceId,
        symbol: Symbol,
        quote: Quote,
        fetchedAt: Instant,
        exchangeZone: ZoneId,
    ): CacheEntry<QuoteRecord> {
        val marketDate = LocalDate.ofInstant(fetchedAt, exchangeZone)
        val latestRef = reference(CacheKey(source, symbol, DataType.QUOTE))
        val historyRef = quoteHistory.document(historyDocumentId(source, symbol, marketDate))
        return firestore.transaction { tx ->
            val current = tx.get(latestRef).get().toEntryOrNull(DataType.QUOTE)?.data
            val record = QuoteRecord.next(current, quote, marketDate)
            tx.set(latestRef, record.toDocument(fetchedAt))
            tx.set(
                historyRef,
                mapOf(
                    SOURCE to source.value,
                    SYMBOL to symbol.value,
                    MARKET_DATE to marketDate.toString(),
                    PRICE to quote.price,
                    CURRENCY to quote.currency,
                    FETCHED_AT to fetchedAt.toTimestamp(),
                    EXPIRE_AT to expireAt(DataType.QUOTE, fetchedAt),
                ),
            )
            CacheEntry(record, fetchedAt)
        }
    }

    private fun reference(key: CacheKey<*>): DocumentReference = sourceData.document(documentId(key))

    private fun QuoteRecord.toDocument(fetchedAt: Instant): FirestoreMap = mapOf(
        PAYLOAD to CachePayloadCodec.encodeQuote(quote),
        FETCHED_AT to fetchedAt.toTimestamp(),
        EXPIRE_AT to expireAt(DataType.QUOTE, fetchedAt),
        MARKET_DATE to marketDate.toString(),
        PREVIOUS_PRICE to previousPrice?.price,
        PREVIOUS_PRICE_DATE to previousPrice?.marketDate?.toString(),
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> DocumentSnapshot.toEntryOrNull(type: DataType<T>): CacheEntry<T>? {
        if (!exists()) return null
        return try {
            val payload = get(PAYLOAD).asFirestoreMap()
            val data = if (type == DataType.QUOTE) toQuoteRecord(payload) as T else CachePayloadCodec.decode(type, payload)
            CacheEntry(data, requiredInstant(FETCHED_AT))
        } catch (e: RuntimeException) {
            logger.warn("Ignoring unreadable cache document {}", reference.path, e)
            null
        }
    }

    private fun DocumentSnapshot.toQuoteRecord(payload: FirestoreMap): QuoteRecord {
        val previousPrice = getDouble(PREVIOUS_PRICE)
        val previousPriceDate = getString(PREVIOUS_PRICE_DATE)?.let(LocalDate::parse)
        return QuoteRecord(
            quote = CachePayloadCodec.decodeQuote(payload),
            marketDate = LocalDate.parse(requiredString(MARKET_DATE)),
            previousPrice = if (previousPrice != null && previousPriceDate != null) PreviousPrice(previousPrice, previousPriceDate) else null,
        )
    }

    private fun expireAt(type: DataType<*>, fetchedAt: Instant) =
        fetchedAt.plus(FreshnessPolicy.cacheTtlOf(type)).toTimestamp()

    companion object {
        private val logger = LoggerFactory.getLogger(FirestoreCompanyCache::class.java)

        private const val PAYLOAD = "payload"
        private const val FETCHED_AT = "fetchedAt"
        private const val EXPIRE_AT = "expireAt"
        private const val MARKET_DATE = "marketDate"
        private const val PREVIOUS_PRICE = "previousPrice"
        private const val PREVIOUS_PRICE_DATE = "previousPriceDate"
        private const val SOURCE = "source"
        private const val SYMBOL = "symbol"
        private const val PRICE = "price"
        private const val CURRENCY = "currency"

        fun documentId(key: CacheKey<*>): String = "${key.source}__${key.symbol}__${key.type.name}"

        fun historyDocumentId(source: SourceId, symbol: Symbol, marketDate: LocalDate): String = "${source}__${symbol}__$marketDate"
    }
}
