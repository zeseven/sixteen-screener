package com.sixteenscreener.app.data.remote.dto

import com.sixteenscreener.app.domain.model.Exchange
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
    val eodhdCode: String? = null,
    val country: String = "",
    val mainVenue: Boolean = false,
    val currency: String,
    val timeZone: String,
    val market: String? = null,
)

fun ExchangesDto.toDomain(): List<Exchange> = items.map { it.toDomain() }

fun ExchangeDto.toDomain() = Exchange(
    suffix = suffix,
    shortName = shortName,
    fullName = fullName,
    mic = mic,
    eodhdCode = eodhdCode,
    country = country,
    mainVenue = mainVenue,
    currency = currency,
    timeZone = timeZone,
    market = market.toMarket(),
)
