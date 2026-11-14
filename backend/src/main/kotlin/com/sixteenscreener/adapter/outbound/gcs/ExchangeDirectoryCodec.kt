package com.sixteenscreener.adapter.outbound.gcs

import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ListedSymbol
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.ExchangeDirectory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

@OptIn(ExperimentalSerializationApi::class)
object ExchangeDirectoryCodec {
    const val FORMAT_VERSION = 1

    private const val SYMBOL = 0
    private const val NAME = 1
    private const val MIC = 2
    private const val CURRENCY = 3
    private const val TYPE = 4
    private const val ROW_SIZE = 5

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    @Serializable
    private data class StoredDirectory(
        val format: Int,
        val source: String,
        val exchangeCode: String,
        val downloadedAt: String,
        val symbols: List<List<String?>>,
    )

    fun encode(directory: ExchangeDirectory, output: OutputStream) {
        val stored = StoredDirectory(
            format = FORMAT_VERSION,
            source = directory.source.value,
            exchangeCode = directory.exchangeCode.value,
            downloadedAt = directory.downloadedAt.toString(),
            symbols = directory.symbols.map(::row),
        )
        json.encodeToStream(stored, output)
    }

    fun decode(input: InputStream): ExchangeDirectory {
        val stored = json.decodeFromStream<StoredDirectory>(input)
        require(stored.format == FORMAT_VERSION) { "Unsupported exchange directory format ${stored.format}" }
        return ExchangeDirectory(
            source = SourceId.of(stored.source),
            exchangeCode = ExchangeCode(stored.exchangeCode),
            symbols = stored.symbols.mapNotNull(::listedSymbol),
            downloadedAt = Instant.parse(stored.downloadedAt),
        )
    }

    private fun row(listing: ListedSymbol): List<String?> =
        listOf(listing.symbol.value, listing.name, listing.mic, listing.currency, listing.type)

    private fun listedSymbol(row: List<String?>): ListedSymbol? {
        if (row.size != ROW_SIZE) return null
        return ListedSymbol(
            symbol = row[SYMBOL]?.let(Symbol::parseOrNull) ?: return null,
            name = row[NAME] ?: return null,
            mic = row[MIC],
            currency = row[CURRENCY] ?: return null,
            type = row[TYPE],
        )
    }
}
