package com.sixteenscreener.adapter.outbound.firestore

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.sixteenscreener.domain.model.IndicatorDefault
import com.sixteenscreener.port.outbound.IndicatorDefaultsRepository
import org.slf4j.LoggerFactory

class FirestoreIndicatorDefaultsRepository(private val firestore: Firestore) : IndicatorDefaultsRepository {
    private val document = firestore.collection(FirestoreCollections.CONFIG).document(INDICATOR_DEFAULTS)

    override suspend fun getOrCreate(initial: List<IndicatorDefault>): List<IndicatorDefault> {
        val existing = document.get().await()
        if (existing.exists()) return existing.toDefaults()
        return firestore.transaction { tx ->
            val snapshot = tx.get(document).get()
            if (snapshot.exists()) {
                snapshot.toDefaults()
            } else {
                tx.create(document, mapOf(RATIOS to initial.map(IndicatorSettingCodec::encodeDefault)))
                initial
            }
        }
    }

    private fun DocumentSnapshot.toDefaults(): List<IndicatorDefault> {
        val stored = (get(RATIOS) as? List<*>).orEmpty()
        return stored.mapNotNull { entry ->
            IndicatorSettingCodec.decodeDefaultOrNull(entry).also {
                if (it == null) logger.warn("Skipping unreadable indicator default {}", entry)
            }
        }
    }

    companion object {
        const val INDICATOR_DEFAULTS = "indicatorDefaults"

        private const val RATIOS = "ratios"

        private val logger = LoggerFactory.getLogger(FirestoreIndicatorDefaultsRepository::class.java)
    }
}
