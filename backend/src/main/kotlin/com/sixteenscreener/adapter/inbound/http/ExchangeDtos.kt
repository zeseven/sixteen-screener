package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.domain.model.ExchangeEntry
import kotlinx.serialization.Serializable

@Serializable
data class ExchangesDto(
    val items: List<ExchangeDto>,
)

@Serializable
data class ExchangeDto(
    val suffix: String,
    val shortName: String,
    val fullName: String,
    val mic: String,
    val currency: String,
    val timeZone: String,
    val market: String?,
)

fun List<ExchangeEntry>.toDto() = ExchangesDto(map { it.toDto() })

fun ExchangeEntry.toDto() = ExchangeDto(
    suffix = suffix,
    shortName = shortName,
    fullName = fullName,
    mic = mic,
    currency = currency,
    timeZone = timeZone.id,
    market = market?.name,
)
