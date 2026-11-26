package com.sixteenscreener.adapter.outbound.yahoo

import com.sixteenscreener.adapter.outbound.http.BearerTokenProvider
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.SourceWarmUp
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/**
 * Fire-and-forget `GET /health` to the Yahoo proxy, so that a cold proxy instance starts before the user refreshes (ADR-088).
 * Sent at most once per [minInterval] per backend instance, with the ID token of proxy calls; it is not counted, doesn't
 * change the last call outcome, and its failures are only logged. The requests run in a scope owned by this class, which
 * [close] cancels.
 */
class YahooProxyWarmUp(
    private val client: HttpClient,
    baseUrl: String,
    private val bearerToken: BearerTokenProvider?,
    private val clock: Clock,
    private val minInterval: Duration = DEFAULT_MIN_INTERVAL,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SourceWarmUp, AutoCloseable {
    private val healthUrl = "${baseUrl.trimEnd('/')}/health"
    private val lastStart = AtomicReference<Instant?>(null)

    override fun warmUp() {
        val now = clock.now()
        val previous = lastStart.get()
        if (previous != null && now < previous + minInterval) return
        if (!lastStart.compareAndSet(previous, now)) return
        scope.launch { sendHealthRequest() }
    }

    override fun close() = scope.cancel()

    private suspend fun sendHealthRequest() {
        try {
            val token = bearerToken?.token()
            val response = client.get(healthUrl) {
                token?.let(::bearerAuth)
                timeout { requestTimeoutMillis = REQUEST_TIMEOUT.inWholeMilliseconds }
            }
            logger.debug("Yahoo proxy warm-up answered {}", response.status.value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.info("Yahoo proxy warm-up failed: {}", e.toString())
        }
    }

    companion object {
        val DEFAULT_MIN_INTERVAL: Duration = Duration.ofMinutes(1)

        private val REQUEST_TIMEOUT = 40.seconds
        private val logger = LoggerFactory.getLogger(YahooProxyWarmUp::class.java)
    }
}
