package com.sixteenscreener.app.domain

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** What the search screen should do for the current input. */
sealed interface SearchRequest {
    /** Input is empty after trimming: clear the results and cancel a running search without a request. */
    data object Clear : SearchRequest

    /** Search for the trimmed [query]. */
    data class Query(val query: String) : SearchRequest
}

/** Limits of the search input. */
object SearchThrottle {
    const val MIN_QUERY_LENGTH = 1
    const val MAX_INPUT_LENGTH = 50
    val INTERVAL: Duration = 1.seconds
}

/**
 * Turns raw search input into [SearchRequest]s: inputs shorter than [minLength] after trimming (by default empty or whitespace-only
 * input) give [SearchRequest.Clear] at once and are never sent as a query; longer ones give at most one [SearchRequest.Query] per
 * [interval], the first immediately and the latest pending one when the interval has passed (trailing throttle).
 * A [SearchRequest.Clear] drops the pending query and is not repeated.
 * Collect it with `mapLatest` or `collectLatest`, so a newer request cancels the search of an older one.
 */
fun Flow<String>.throttleSearch(
    interval: Duration = SearchThrottle.INTERVAL,
    minLength: Int = SearchThrottle.MIN_QUERY_LENGTH,
): Flow<SearchRequest> {
    require(minLength >= 1) { "minLength must be at least 1, an empty query is never sent" }
    return map { it.trim() }
        .distinctUntilChanged()
        .let { inputs ->
            channelFlow {
                val throttle = TrailingThrottle(this, interval)
                inputs.collect { query ->
                    if (query.length < minLength) throttle.clear() else throttle.offer(query)
                }
            }
        }
}

private class TrailingThrottle(
    private val producer: ProducerScope<SearchRequest>,
    private val interval: Duration,
) {
    private val mutex = Mutex()
    private var pending: String? = null
    private var window: Job? = null
    private var lastSent: SearchRequest? = null

    suspend fun clear() = mutex.withLock {
        pending = null
        if (lastSent != SearchRequest.Clear) send(SearchRequest.Clear)
    }

    suspend fun offer(query: String) = mutex.withLock {
        if (window == null) {
            send(SearchRequest.Query(query))
            window = openWindow()
        } else {
            pending = query
        }
    }

    private fun openWindow(): Job = producer.launch {
        do {
            delay(interval)
            val sent = mutex.withLock {
                val next = pending
                if (next == null) {
                    window = null
                } else {
                    pending = null
                    send(SearchRequest.Query(next))
                }
                next != null
            }
        } while (sent)
    }

    private suspend fun send(request: SearchRequest) {
        producer.send(request)
        lastSent = request
    }
}
