package com.sixteenscreener.adapter.outbound.http

import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceResult
import com.sixteenscreener.port.outbound.Clock
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.channels.UnresolvedAddressException
import kotlin.time.Duration

/**
 * Maps a response status outside 2XX to a source result; the default treats `429` as [SourceResult.LimitReached]
 * and every other status as [SourceResult.SourceError].
 */
fun interface ErrorStatusMapping {
    fun resultOf(status: Int, outcome: SourceCallOutcome): SourceResult<Nothing>

    companion object {
        private const val TOO_MANY_REQUESTS = 429

        val DEFAULT = ErrorStatusMapping { status, outcome ->
            if (status == TOO_MANY_REQUESTS) SourceResult.LimitReached(outcome) else SourceResult.SourceError(outcome)
        }

        fun overriding(vararg results: Pair<Int, (SourceCallOutcome) -> SourceResult<Nothing>>): ErrorStatusMapping {
            val byStatus = results.toMap()
            return ErrorStatusMapping { status, outcome ->
                byStatus[status]?.invoke(outcome) ?: DEFAULT.resultOf(status, outcome)
            }
        }
    }
}

/**
 * One call to a source over HTTP: runs the request within [timeout] and classifies the outcome (ADR-028).
 * Every source client sends its requests through this class, one instance per source, which sends at most
 * [maxParallelRequests] requests at a time; time spent waiting for a free slot does not count toward the timeout (ADR-081).
 * With a [pacer], a request that holds a slot also waits for its turn (ADR-089); that wait doesn't count toward the timeout
 * either. With a [bearerToken] provider, every request carries its token in the `Authorization: Bearer` header; a failure to
 * get the token is the outcome of the call.
 */
class SourceHttpCall(
    private val client: HttpClient,
    private val timeout: Duration,
    private val clock: Clock,
    maxParallelRequests: Int = DEFAULT_MAX_PARALLEL_REQUESTS,
    private val pacer: RequestPacer? = null,
    private val bearerToken: BearerTokenProvider? = null,
) {
    private val logger = LoggerFactory.getLogger(SourceHttpCall::class.java)
    private val parallelRequests = Semaphore(maxParallelRequests)

    suspend fun <T> execute(
        request: HttpRequestBuilder.() -> Unit,
        errorStatus: ErrorStatusMapping = ErrorStatusMapping.DEFAULT,
        requestTimeout: Duration? = null,
        mapResponse: suspend (HttpResponse) -> T?,
    ): SourceResult<T> {
        val response = try {
            parallelRequests.withPermit {
                pacer?.awaitTurn()
                val token = bearerToken?.token()
                client.request {
                    request()
                    token?.let(::bearerAuth)
                    timeout { requestTimeoutMillis = (requestTimeout ?: timeout).inWholeMilliseconds }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failure(e, noResponse = true)
        }

        if (!response.status.isSuccess()) {
            return errorStatus.resultOf(response.status.value, SourceCallOutcome.httpError(response.status.value, clock.now()))
        }

        return try {
            val data = mapResponse(response)
            val outcome = SourceCallOutcome.success(clock.now())
            if (data == null) SourceResult.NoData(outcome) else SourceResult.Success(data, outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure(e, noResponse = false)
        }
    }

    private fun failure(error: Exception, noResponse: Boolean): SourceResult<Nothing> {
        val outcome = if (isTimeout(error) || (noResponse && isConnectionFailure(error))) {
            logger.warn("Source call received no response: {}", error.toString())
            SourceCallOutcome.timeout(clock.now())
        } else {
            logger.error("Source call failed with an application error", error)
            SourceCallOutcome.appError(clock.now())
        }
        return SourceResult.SourceError(outcome)
    }

    private fun isTimeout(error: Exception): Boolean =
        error is HttpRequestTimeoutException || error is ConnectTimeoutException || error is SocketTimeoutException

    private fun isConnectionFailure(error: Exception): Boolean =
        error is IOException || error is UnresolvedAddressException

    companion object {
        const val DEFAULT_MAX_PARALLEL_REQUESTS = 10
    }
}
