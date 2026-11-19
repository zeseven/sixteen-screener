package com.sixteenscreener.app.testing

import com.sixteenscreener.app.domain.model.CallResult
import com.sixteenscreener.app.domain.model.Capability
import com.sixteenscreener.app.domain.model.LimitInterval
import com.sixteenscreener.app.domain.model.LimitUnit
import com.sixteenscreener.app.domain.model.RequestLimit
import com.sixteenscreener.app.domain.model.Source
import com.sixteenscreener.app.domain.model.SourceCallOutcome
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.SourceRole
import java.time.Instant

object Sources {
    fun limit(
        interval: LimitInterval = LimitInterval.MINUTE,
        limit: Int = 60,
        used: Int = 12,
        unit: LimitUnit = LimitUnit.REQUESTS,
        resetsAt: Instant? = null,
    ) = RequestLimit(interval = interval, unit = unit, limit = limit, used = used, resetsAt = resetsAt)

    fun outcome(result: CallResult, httpStatus: Int? = null) =
        SourceCallOutcome(result = result, httpStatus = httpStatus, at = Instant.parse("2026-11-14T10:00:00Z"))

    fun source(
        id: String = "finnhub",
        name: String = "Finnhub",
        capabilities: List<Capability> = listOf(Capability.SEARCH, Capability.QUOTE, Capability.BALANCE_SHEET),
        isDefault: Boolean = false,
        roles: List<SourceRole> = emptyList(),
        defaultFor: List<SourceRole> = emptyList(),
        automaticRequests: Boolean = true,
        limits: List<RequestLimit> = listOf(limit()),
        lastCall: SourceCallOutcome? = null,
        totalCalls: Long? = null,
    ) = Source(
        id = SourceId(id),
        name = name,
        capabilities = capabilities,
        roles = roles,
        defaultFor = defaultFor,
        isDefault = isDefault,
        automaticRequests = automaticRequests,
        limits = limits,
        lastCall = lastCall,
        totalCalls = totalCalls,
    )
}
