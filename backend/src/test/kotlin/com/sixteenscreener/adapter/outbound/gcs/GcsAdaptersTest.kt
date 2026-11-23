package com.sixteenscreener.adapter.outbound.gcs

import com.google.cloud.NoCredentials
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper
import com.sixteenscreener.contract.exchangeDirectoryStoreContract
import com.sixteenscreener.contract.logoStorageContract
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ImageFormat
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
    include(
        logoStorageContract {
            GcsLogoStorage(LocalStorageHelper.getOptions().service, BUCKET, GcsPaths.LOGOS, ImageFormat.SVG)
        },
    )

    test("SVG logo is stored under logos/{symbol}.svg") {
        val storage = LocalStorageHelper.getOptions().service

        GcsLogoStorage(storage, BUCKET, GcsPaths.LOGOS, ImageFormat.SVG).save(Symbol.of("SAP.DE"), "<svg/>".toByteArray())

        val blob = storage.get(BlobId.of(BUCKET, "logos/SAP.DE.svg"))
        blob.contentType shouldBe "image/svg+xml"
        blob.getContent().decodeToString() shouldBe "<svg/>"
    }

    test("PNG logo is stored under finnhub-logos/{symbol}.png") {
        val storage = LocalStorageHelper.getOptions().service
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)

        GcsLogoStorage(storage, BUCKET, GcsPaths.FINNHUB_LOGOS, ImageFormat.PNG).save(Symbol.of("BRK.B"), png)

        val blob = storage.get(BlobId.of(BUCKET, "finnhub-logos/BRK.B.png"))
        blob.contentType shouldBe "image/png"
        blob.getContent() shouldBe png
    }

    test("EODHD PNG logo is stored under eodhd-logos/{symbol}.png") {
        val storage = LocalStorageHelper.getOptions().service
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)

        GcsLogoStorage(storage, BUCKET, GcsPaths.EODHD_LOGOS, ImageFormat.PNG).save(Symbol.of("SAP.DE"), png)

        val blob = storage.get(BlobId.of(BUCKET, "eodhd-logos/SAP.DE.png"))
        blob.contentType shouldBe "image/png"
        blob.getContent() shouldBe png
    }

    test("EODHD and Finnhub PNG storages in the same bucket don't see each other's logos") {
        val storage = LocalStorageHelper.getOptions().service
        val eodhdLogos = GcsLogoStorage(storage, BUCKET, GcsPaths.EODHD_LOGOS, ImageFormat.PNG)
        val finnhubLogos = GcsLogoStorage(storage, BUCKET, GcsPaths.FINNHUB_LOGOS, ImageFormat.PNG)

        eodhdLogos.save(Symbol.of("RY.TO"), byteArrayOf(1, 2, 3))

        finnhubLogos.find(Symbol.of("RY.TO")) shouldBe null
        eodhdLogos.find(Symbol.of("RY.TO")) shouldBe byteArrayOf(1, 2, 3)
    }

    test("SVG and PNG storages in the same bucket don't see each other's logos") {
        val storage = LocalStorageHelper.getOptions().service
        val svgLogos = GcsLogoStorage(storage, BUCKET, GcsPaths.LOGOS, ImageFormat.SVG)
        val pngLogos = GcsLogoStorage(storage, BUCKET, GcsPaths.FINNHUB_LOGOS, ImageFormat.PNG)

        pngLogos.save(Symbol.of("RY.TO"), byteArrayOf(1, 2, 3))

        svgLogos.find(Symbol.of("RY.TO")) shouldBe null
        pngLogos.find(Symbol.of("RY.TO")) shouldBe byteArrayOf(1, 2, 3)
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
