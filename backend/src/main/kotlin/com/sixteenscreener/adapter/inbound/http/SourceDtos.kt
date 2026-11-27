package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.domain.model.CompanySources
import com.sixteenscreener.domain.model.RequestUsage
import com.sixteenscreener.domain.model.SourceCallOutcome
import com.sixteenscreener.domain.model.SourceOption
import com.sixteenscreener.domain.model.SourceOverview
import kotlinx.serialization.Serializable

@Serializable
data class SourcesDto(
    val items: List<SourceDto>,
)

@Serializable
data class SourceDto(
    val id: String,
    val name: String,
    val capabilities: List<String>,
    val roles: List<String>,
    val defaultFor: List<String>,
    val isDefault: Boolean,
    val automaticRequests: Boolean,
    val limits: List<RequestLimitDto>,
    val lastCall: SourceCallOutcomeDto?,
    val pausedUntil: String?,
    val totalCalls: Long?,
)

@Serializable
data class RequestLimitDto(
    val interval: String,
    val unit: String,
    val limit: Int?,
    val used: Int,
    val resetsAt: String?,
)

@Serializable
data class SourceCallOutcomeDto(
    val result: String,
    val httpStatus: Int?,
    val at: String,
)

@Serializable
data class CompanySourcesDto(
    val priceRatios: List<SourceOptionDto>,
    val dividends: List<SourceOptionDto>,
    val balanceSheet: List<SourceOptionDto>,
    val profile: List<SourceOptionDto>,
)

@Serializable
data class SourceOptionDto(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    val automaticRequests: Boolean,
)

fun CompanySources.toDto() = CompanySourcesDto(
    priceRatios = priceRatios.map { it.toDto() },
    dividends = dividends.map { it.toDto() },
    balanceSheet = balanceSheet.map { it.toDto() },
    profile = profile.map { it.toDto() },
)

fun SourceOption.toDto() = SourceOptionDto(
    id = source.id.value,
    name = source.name,
    isDefault = isDefault,
    automaticRequests = source.automaticRequests,
)

fun List<SourceOverview>.toDto() = SourcesDto(map { it.toDto() })

fun SourceOverview.toDto() = SourceDto(
    id = source.id.value,
    name = source.name,
    capabilities = source.capabilities.sortedBy { it.ordinal }.map { it.name },
    roles = source.roles.sortedBy { it.ordinal }.map { it.name },
    defaultFor = defaultFor.map { it.name },
    isDefault = isDefault,
    automaticRequests = source.automaticRequests,
    limits = limits.map { it.toDto() },
    lastCall = lastCall?.toDto(),
    pausedUntil = pausedUntil?.toString(),
    totalCalls = totalCalls,
)

fun RequestUsage.toDto() = RequestLimitDto(
    interval = limit.interval.name,
    unit = limit.unit.name,
    limit = limit.limit,
    used = used,
    resetsAt = resetsAt.toString(),
)

fun SourceCallOutcome.toDto() = SourceCallOutcomeDto(result = result.name, httpStatus = httpStatus, at = at.toString())
