package com.sixteenscreener.adapter.outbound.eodhd

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal data class DividendDto(
    val date: String? = null,
    val declarationDate: String? = null,
    val recordDate: String? = null,
    val paymentDate: String? = null,
    val value: JsonPrimitive? = null,
    val currency: String? = null,
)
