package com.sixteenscreener.adapter.outbound.eodhd

import com.sixteenscreener.domain.model.Dividend
import com.sixteenscreener.domain.model.Dividends
import com.sixteenscreener.domain.model.Quote
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate

internal object EodhdMapper {
    private const val ISO_DATE_LENGTH = 10

    fun quote(response: JsonElement, currency: String, priceDivisor: Double): Quote? {
        val quote = when (response) {
            is JsonObject -> response
            is JsonArray -> response.firstOrNull() as? JsonObject
            else -> null
        } ?: return null
        val price = quote.number("close")?.takeIf { it > 0 } ?: return null
        quote.number("timestamp")?.takeIf { it > 0 } ?: return null
        return Quote(price = price / priceDivisor, currency = currency)
    }

    fun dividends(response: List<DividendDto>): Dividends =
        Dividends(response.mapNotNull(::dividend).sortedByDescending { it.exDate })

    private fun dividend(dto: DividendDto): Dividend? {
        val exDate = dto.date.toLocalDateOrNull() ?: return null
        val amount = dto.value?.numberOrNull() ?: return null
        val currency = dto.currency.nullIfBlank() ?: return null
        return Dividend(
            exDate = exDate,
            paymentDate = dto.paymentDate.toLocalDateOrNull(),
            recordDate = dto.recordDate.toLocalDateOrNull(),
            declarationDate = dto.declarationDate.toLocalDateOrNull(),
            amount = amount,
            currency = currency,
        )
    }

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.numberOrNull()

    private fun JsonPrimitive.numberOrNull(): Double? = takeUnless { it.isString }?.doubleOrNull?.takeIf { it.isFinite() }

    private fun String?.nullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String?.toLocalDateOrNull(): LocalDate? =
        nullIfBlank()?.take(ISO_DATE_LENGTH)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
