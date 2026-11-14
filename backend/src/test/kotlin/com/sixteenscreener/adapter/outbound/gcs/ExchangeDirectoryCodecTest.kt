package com.sixteenscreener.adapter.outbound.gcs

import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.ExchangeDirectory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import java.io.ByteArrayOutputStream
import java.time.Instant

class ExchangeDirectoryCodecTest : FunSpec({
    val downloadedAt = Instant.parse("2026-11-14T12:00:00.123Z")
    val apple = ListedSymbol(Symbol.of("AAPL"), "APPLE INC", "XNAS", "USD", "Common Stock")
    val unit = ListedSymbol(Symbol.of("BRK.B"), "BERKSHIRE \"B\" É", null, "USD", null)

    fun encode(directory: ExchangeDirectory): String =
        ByteArrayOutputStream().also { ExchangeDirectoryCodec.encode(directory, it) }.toString(Charsets.UTF_8)

    fun decode(json: String): ExchangeDirectory = ExchangeDirectoryCodec.decode(json.byteInputStream())

    test("compact row format") {
        val directory = ExchangeDirectory(SourceId.of("finnhub"), ExchangeCode.US, listOf(apple, unit), downloadedAt)

        encode(directory) shouldBe
            """{"format":1,"source":"finnhub","exchangeCode":"US","downloadedAt":"2026-11-14T12:00:00.123Z",""" +
            """"symbols":[["AAPL","APPLE INC","XNAS","USD","Common Stock"],["BRK.B","BERKSHIRE \"B\" """ + "É" +
            """",null,"USD",null]]}"""
    }

    test("round trip keeps every field") {
        val directory = ExchangeDirectory(SourceId.of("finnhub"), ExchangeCode("DE"), listOf(apple, unit), downloadedAt)

        decode(encode(directory)) shouldBe directory
    }

    test("empty directory round trip") {
        val directory = ExchangeDirectory(SourceId.of("finnhub"), ExchangeCode("ME"), emptyList(), downloadedAt)

        decode(encode(directory)) shouldBe directory
    }

    test("rows with an invalid symbol, missing currency or wrong size are skipped") {
        val decoded = decode(
            """
            {"format":1,"source":"finnhub","exchangeCode":"US","downloadedAt":"2026-11-14T12:00:00Z","symbols":[
              ["AAPL","APPLE INC","XNAS","USD","Common Stock"],
              ["aapl","lower case","XNAS","USD",null],
              ["MSFT","MICROSOFT CORP","XNAS",null,null],
              ["IBM","INTL BUSINESS MACHINES"]
            ],"unknown":true}
            """.trimIndent(),
        )

        decoded.symbols shouldBe listOf(apple)
    }

    test("unsupported format version is rejected") {
        shouldThrow<IllegalArgumentException> {
            decode("""{"format":2,"source":"finnhub","exchangeCode":"US","downloadedAt":"2026-11-14T12:00:00Z","symbols":[]}""")
        }
    }

    test("malformed JSON is rejected") {
        shouldThrow<SerializationException> { decode("""{"format":1,"symbols":[""") }
    }

    test("large directory stays compact") {
        val listings = (1..30_000).map { index ->
            ListedSymbol(Symbol.of("T$index"), "COMPANY NUMBER $index INC", "XNYS", "USD", "Common Stock")
        }
        val directory = ExchangeDirectory(SourceId.of("finnhub"), ExchangeCode.US, listings, downloadedAt)

        val encoded = encode(directory)

        encoded.length shouldBeLessThan 2_000_000
        decode(encoded) shouldBe directory
    }
})
