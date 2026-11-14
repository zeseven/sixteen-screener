package com.sixteenscreener.app.data.remote.dto

import com.sixteenscreener.app.domain.model.CallResult
import com.sixteenscreener.app.domain.model.Capability
import com.sixteenscreener.app.domain.model.LimitInterval
import com.sixteenscreener.app.domain.model.LimitUnit
import com.sixteenscreener.app.domain.model.RequestLimit
import com.sixteenscreener.app.domain.model.Source
import com.sixteenscreener.app.domain.model.SourceCallOutcome
import com.sixteenscreener.app.domain.model.SourceId
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class SourcesDto(
    val items: List<SourceDto>,
)

@Serializable
data class SourceDto(
    val id: String,
    val name: String,
    val capabilities: List<String>,
    val selectableAsDefault: Boolean,
    val isDefault: Boolean,
    val limits: List<RequestLimitDto>,
    val lastCall: SourceCallOutcomeDto? = null,
)

@Serializable
data class RequestLimitDto(
    val interval: String,
    val unit: String,
    val limit: Int,
    val used: Int,
    val resetsAt: String? = null,
)

@Serializable
data class SourceCallOutcomeDto(
    val result: String,
    val httpStatus: Int? = null,
    val at: String,
)

fun SourcesDto.toDomain(): List<Source> = items.map { it.toDomain() }

fun SourceDto.toDomain() = Source(
    id = SourceId(id),
    name = name,
    capabilities = capabilities.map { it.toEnumOr(Capability.UNKNOWN) },
    selectableAsDefault = selectableAsDefault,
    isDefault = isDefault,
    limits = limits.map { limit ->
        RequestLimit(
            interval = limit.interval.toEnumOr(LimitInterval.UNKNOWN),
            unit = limit.unit.toEnumOr(LimitUnit.UNKNOWN),
            limit = limit.limit,
            used = limit.used,
            resetsAt = limit.resetsAt?.let(Instant::parse),
        )
    },
    lastCall = lastCall?.let { SourceCallOutcome(result = it.result.toEnumOr(CallResult.UNKNOWN), httpStatus = it.httpStatus, at = Instant.parse(it.at)) },
)
