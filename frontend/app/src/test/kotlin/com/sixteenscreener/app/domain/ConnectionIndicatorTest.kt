package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.CallResult
import com.sixteenscreener.app.domain.model.SourceCallOutcome
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ConnectionIndicatorTest {

    private fun outcome(result: CallResult, httpStatus: Int? = null) =
        SourceCallOutcome(result = result, httpStatus = httpStatus, at = Instant.parse("2026-11-14T10:00:00Z"))

    @Test
    fun `nothing for a source never called`() {
        assertEquals("", ConnectionIndicator.of(null))
    }

    @Test
    fun `nothing for a successful call`() {
        assertEquals("", ConnectionIndicator.of(outcome(CallResult.SUCCESS)))
    }

    @Test
    fun `status code for an HTTP error`() {
        assertEquals("429", ConnectionIndicator.of(outcome(CallResult.HTTP_ERROR, 429)))
        assertEquals("500", ConnectionIndicator.of(outcome(CallResult.HTTP_ERROR, 500)))
    }

    @Test
    fun `nothing for an HTTP error without a status code`() {
        assertEquals("", ConnectionIndicator.of(outcome(CallResult.HTTP_ERROR)))
    }

    @Test
    fun `T for a timeout`() {
        assertEquals("T", ConnectionIndicator.of(outcome(CallResult.TIMEOUT)))
    }

    @Test
    fun `NPE for an application error`() {
        assertEquals("NPE", ConnectionIndicator.of(outcome(CallResult.APP_ERROR)))
    }

    @Test
    fun `nothing for an unknown result`() {
        assertEquals("", ConnectionIndicator.of(outcome(CallResult.UNKNOWN)))
    }
}
