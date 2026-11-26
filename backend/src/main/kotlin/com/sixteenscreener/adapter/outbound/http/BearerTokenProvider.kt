package com.sixteenscreener.adapter.outbound.http

/**
 * Provides the token a source call sends in the `Authorization: Bearer` header.
 */
fun interface BearerTokenProvider {
    /**
     * Token for the next request. Throws when no token can be obtained; [SourceHttpCall] classifies the failure like a
     * failed request (no response is a timeout, anything else an application error).
     */
    suspend fun token(): String
}
