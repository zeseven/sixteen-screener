package com.sixteenscreener.adapter.outbound.firestore

import com.sixteenscreener.domain.model.CacheKey
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import java.time.Instant
import java.time.LocalDate

class FirestoreDocumentIdsTest : FunSpec({
    val finnhub = SourceId.of("finnhub")

    test("profile name document ID is the SHA-256 hex of the normalized name") {
        val expected = "2bd806c97f0e00af1a1fc3328fa763a9269723c8db8fac4f93af71db186d6e90"

        FirestoreProfileRepository.nameDocumentId(ProfileName.of("alice")) shouldBe expected
        FirestoreProfileRepository.nameDocumentId(ProfileName.of("  ALICE ")) shouldBe expected
    }

    test("profile name document IDs are safe for names with slashes and Unicode") {
        FirestoreProfileRepository.nameDocumentId(ProfileName.of("a/b é")) shouldMatch Regex("^[0-9a-f]{64}$")
    }

    test("source data, history and counter document IDs") {
        FirestoreCompanyCache.documentId(CacheKey(finnhub, Symbol.of("SAP.DE"), DataType.COMPANY_PROFILE)) shouldBe
            "finnhub__SAP.DE__COMPANY_PROFILE"
        FirestoreCompanyCache.historyDocumentId(finnhub, Symbol.of("AAPL"), LocalDate.parse("2026-11-14")) shouldBe
            "finnhub__AAPL__2026-11-14"
        FirestoreRequestCounterRepository.documentId(finnhub, LimitInterval.MINUTE, Instant.parse("2026-11-14T12:01:00Z")) shouldBe
            "finnhub__MINUTE__2026-11-14T12:01:00Z"
    }
})
