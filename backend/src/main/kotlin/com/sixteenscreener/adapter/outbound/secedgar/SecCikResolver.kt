package com.sixteenscreener.adapter.outbound.secedgar

import com.sixteenscreener.adapter.outbound.http.SourceHttpCall
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.SecCikRepository
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

fun interface CallBudget {
    suspend fun tryAcquire(): Boolean
}

sealed interface CikResolution {
    data class Known(val cik: Long) : CikResolution

    data class Unresolved(val result: SourceResult<Nothing>) : CikResolution
}

class SecCikResolver(
    filesBaseUrl: String,
    private val userAgent: String,
    private val call: SourceHttpCall,
    private val repository: SecCikRepository,
    private val budget: CallBudget,
    private val clock: Clock,
    private val downloadTimeout: Duration = DEFAULT_DOWNLOAD_TIMEOUT,
    private val tableMaxAge: Duration = DEFAULT_TABLE_MAX_AGE,
) {
    private class Download(val at: Instant, val outcome: SourceCallOutcome?, val failure: SourceResult<Nothing>?)

    private val tickersUrl = filesBaseUrl.trimEnd('/') + TICKERS_PATH
    private val ciks = ConcurrentHashMap<String, Long>()
    private val persisted: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val mutex = Mutex()

    @Volatile
    private var lastDownload: Download? = null

    suspend fun resolve(ticker: String): CikResolution {
        known(ticker)?.let { return it }
        stored(ticker)?.let { cik ->
            persisted += ticker
            ciks[ticker] = cik
            return CikResolution.Known(cik)
        }
        val seen = lastDownload
        return mutex.withLock { known(ticker) ?: resolveFromTable(ticker, seen) }
    }

    private suspend fun known(ticker: String): CikResolution.Known? {
        val cik = ciks[ticker] ?: return null
        if (persisted.add(ticker)) save(ticker, cik)
        return CikResolution.Known(cik)
    }

    private suspend fun resolveFromTable(ticker: String, seen: Download?): CikResolution {
        val current = lastDownload
        if (current != null && (current !== seen || current.isRecentSuccess())) {
            return CikResolution.Unresolved(current.failure ?: SourceResult.NoData(outcome = null))
        }
        val download = download().also { lastDownload = it }
        download.failure?.let { return CikResolution.Unresolved(it) }
        return known(ticker) ?: CikResolution.Unresolved(SourceResult.NoData(download.outcome))
    }

    private fun Download.isRecentSuccess(): Boolean =
        failure == null && clock.now() < at.plus(tableMaxAge.toJavaDuration())

    private suspend fun download(): Download {
        if (!budget.tryAcquire()) return Download(clock.now(), outcome = null, SourceResult.LimitReached(outcome = null))
        val result = call.execute(
            request = {
                url(tickersUrl)
                header(HttpHeaders.UserAgent, userAgent)
            },
            requestTimeout = downloadTimeout,
        ) { response ->
            response.body<Map<String, SecTickerDto>>().values
                .associate { it.ticker.uppercase() to it.cik }
                .takeIf { it.isNotEmpty() }
        }
        return when (result) {
            is SourceResult.Success -> {
                ciks.putAll(result.data)
                Download(clock.now(), result.outcome, failure = null)
            }
            is SourceResult.NoData -> Download(clock.now(), result.outcome, result)
            is SourceResult.NotSupported -> Download(clock.now(), result.outcome, result)
            is SourceResult.LimitReached -> Download(clock.now(), result.outcome, result)
            is SourceResult.SourceError -> Download(clock.now(), result.outcome, result)
        }
    }

    private suspend fun stored(ticker: String): Long? =
        guarded("read the stored CIK of '$ticker'") { repository.find(ticker, clock.now()) }

    private suspend fun save(ticker: String, cik: Long) {
        guarded("store the CIK of '$ticker'") { repository.save(ticker, cik, clock.now()) }
    }

    private suspend fun <T> guarded(action: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to {}", action, e)
            null
        }

    companion object {
        const val TICKERS_PATH = "/files/company_tickers.json"

        val DEFAULT_DOWNLOAD_TIMEOUT: Duration = 30.seconds
        val DEFAULT_TABLE_MAX_AGE: Duration = 1.days

        private val logger = LoggerFactory.getLogger(SecCikResolver::class.java)
    }
}
