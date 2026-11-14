package com.sixteenscreener.adapter.outbound.gcs

import com.google.cloud.NoCredentials
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper
import com.sixteenscreener.contract.exchangeDirectoryStoreContract
import com.sixteenscreener.contract.logoStorageContract
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.ExchangeDirectory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant

private const val BUCKET = "test-bucket"

class GcsExchangeDirectoryStoreTest : FunSpec({
    include(exchangeDirectoryStoreContract { GcsExchangeDirectoryStore(LocalStorageHelper.getOptions().service, BUCKET) })

    test("directory is stored as JSON under directory/{source}/{exchangeCode}.json") {
        val storage = LocalStorageHelper.getOptions().service
        val directory = ExchangeDirectory(
            SourceId.of("finnhub"),
            ExchangeCode("DE"),
            listOf(ListedSymbol(Symbol.of("SAP.DE"), "SAP SE", "XETR", "EUR", "Common Stock")),
            Instant.parse("2026-11-14T12:00:00Z"),
        )

        GcsExchangeDirectoryStore(storage, BUCKET).save(directory)

        val blob = storage.get(BlobId.of(BUCKET, "directory/finnhub/DE.json"))
        blob shouldNotBe null
        blob.contentType shouldBe "application/json"
    }
})

class GcsLogoStorageTest : FunSpec({
    include(logoStorageContract { GcsLogoStorage(LocalStorageHelper.getOptions().service, BUCKET) })

    test("logo is stored as SVG under logos/{symbol}.svg") {
        val storage = LocalStorageHelper.getOptions().service

        GcsLogoStorage(storage, BUCKET).save(Symbol.of("SAP.DE"), "<svg/>".toByteArray())

        val blob = storage.get(BlobId.of(BUCKET, "logos/SAP.DE.svg"))
        blob.contentType shouldBe "image/svg+xml"
        blob.getContent().decodeToString() shouldBe "<svg/>"
    }
})

class GcsClientFactoryTest : FunSpec({
    test("emulator host replaces the API host and disables credentials") {
        GcsClientFactory.create("test-project", "http://localhost:4443").use { storage ->
            storage.options.host shouldBe "http://localhost:4443"
            storage.options.projectId shouldBe "test-project"
            storage.options.credentials shouldBe NoCredentials.getInstance()
        }
    }
})
