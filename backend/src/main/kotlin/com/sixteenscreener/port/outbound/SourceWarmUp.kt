package com.sixteenscreener.port.outbound

/**
 * Warms up a source that starts slowly (the Yahoo proxy scales to zero, ADR-088), so that a later manual request of its
 * data doesn't wait for a cold start.
 */
fun interface SourceWarmUp {
    /**
     * Requests a warm-up and returns at once: the work runs in the background, is not a counted call, doesn't change the
     * last call outcome and never fails the caller. Implementations may skip requests that come too often.
     */
    fun warmUp()
}
