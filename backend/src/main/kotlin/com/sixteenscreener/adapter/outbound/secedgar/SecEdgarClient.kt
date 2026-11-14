package com.sixteenscreener.adapter.outbound.secedgar

import com.sixteenscreener.adapter.outbound.http.ErrorStatusMapping
import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.adapter.outbound.http.SourceHttpClient
import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.domain.model.Symbol
import com.sixteenscreener.port.outbound.BalanceSheetSource
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream

class SecEdgarClient(
    override val id: SourceId,
    baseUrl: String,
    private val userAgent: String,
    private val call: SourceHttpCall,
    private val ciks: SecCikResolver,
) : BalanceSheetSource {

    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun balanceSheet(symbol: Symbol): SourceResult<BalanceSheet> {
        if (symbol.exchangeCode != ExchangeCode.US) return SourceResult.NotSupported(outcome = null)
        return when (val resolution = ciks.resolve(secTickerOf(symbol))) {
            is CikResolution.Unresolved -> resolution.result
            is CikResolution.Known -> companyFacts(resolution.cik)
        }
    }

    private suspend fun companyFacts(cik: Long): SourceResult<BalanceSheet> =
        call.execute(
            request = {
                url("$baseUrl$COMPANY_FACTS_PATH/CIK${cik.toString().padStart(CIK_DIGITS, '0')}.json")
                header(HttpHeaders.UserAgent, userAgent)
            },
            errorStatus = errorStatus,
        ) { response -> SecBalanceSheetMapper.balanceSheet(decodeConcepts(response)) }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun decodeConcepts(response: HttpResponse): Map<String, SecConceptDto> {
        val channel = response.bodyAsChannel()
        return withContext(Dispatchers.IO) {
            channel.toInputStream().use { input ->
                SourceHttpClient.json.decodeFromStream(SecCompanyFactsDto.serializer(), input).facts.usGaap
            }
        }
    }

    companion object {
        const val COMPANY_FACTS_PATH = "/api/xbrl/companyfacts"

        private const val CIK_DIGITS = 10
        private const val NOT_FOUND = 404

        private val errorStatus = ErrorStatusMapping.overriding(
            NOT_FOUND to { outcome -> SourceResult.NoData(outcome) },
        )

        fun secTickerOf(symbol: Symbol): String = symbol.ticker.uppercase().replace('.', '-')
    }
}
