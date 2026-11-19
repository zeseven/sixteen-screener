package com.sixteenscreener.app.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** What the search screen should do for the current input. */
sealed interface SearchRequest {
    /** Input is empty after trimming: clear the results and cancel a running search without a request. */
    data object Clear : SearchRequest

    /** Search for the trimmed [query]. */
    data class Query(val query: String) : SearchRequest
}

/** Limits of the search input. */
object SearchDebounce {
    const val MIN_QUERY_LENGTH = 1
    const val MAX_INPUT_LENGTH = 50
    val PAUSE: Duration = 500.milliseconds
}

private sealed interface SearchInput {
    val query: String

    data class Typed(override val query: String) : SearchInput

    data class Submitted(override val query: String) : SearchInput
}

/**
 * Turns raw search input into [SearchRequest]s: inputs shorter than [minLength] after trimming (by default empty or whitespace-only
 * input) give [SearchRequest.Clear] at once, drop the pending query and are never sent as a query; longer ones give a
 * [SearchRequest.Query] once no input arrived for [pause] (debounce). A query from [submitted] (the keyboard's search action) is sent
 * at once and drops the pending one. A request equal to the previous one is not repeated, so a query is sent again only after the
 * input changed to another sent query or was cleared.
 * Collect it with `mapLatest` or `collectLatest`, so a newer request cancels the search of an older one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<String>.debounceSearch(
    submitted: Flow<String> = emptyFlow(),
    pause: Duration = SearchDebounce.PAUSE,
    minLength: Int = SearchDebounce.MIN_QUERY_LENGTH,
): Flow<SearchRequest> {
    require(minLength >= 1) { "minLength must be at least 1, an empty query is never sent" }
    val typed = map { it.trim() }.distinctUntilChanged().map(SearchInput::Typed)
    return merge(typed, submitted.map { SearchInput.Submitted(it.trim()) })
        .transformLatest { input ->
            when {
                input.query.length < minLength -> if (input is SearchInput.Typed) emit(SearchRequest.Clear)
                input is SearchInput.Typed -> {
                    delay(pause)
                    emit(SearchRequest.Query(input.query))
                }
                else -> emit(SearchRequest.Query(input.query))
            }
        }
        .distinctUntilChanged()
}
