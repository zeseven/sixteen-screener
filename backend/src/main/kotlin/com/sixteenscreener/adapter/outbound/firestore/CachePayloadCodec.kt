package com.sixteenscreener.adapter.outbound.firestore

import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.BalanceSheetItem
import com.sixteenscreener.domain.model.BalanceSheetPeriod
import com.sixteenscreener.domain.model.CompanyProfile
import com.sixteenscreener.domain.model.DataType
import com.sixteenscreener.domain.model.FiscalPeriod
import com.sixteenscreener.domain.model.Quote
import com.sixteenscreener.domain.model.Ratio
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.Ratios
import java.time.LocalDate

object CachePayloadCodec {

    fun <T : Any> encode(type: DataType<T>, data: T): FirestoreMap = when (type) {
        DataType.RATIOS -> encodeRatios(data as Ratios)
        DataType.BALANCE_SHEET -> encodeBalanceSheet(data as BalanceSheet)
        DataType.COMPANY_PROFILE -> encodeProfile(data as CompanyProfile)
        DataType.QUOTE -> throw IllegalArgumentException("Quotes are encoded with encodeQuote")
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> decode(type: DataType<T>, payload: FirestoreMap): T = when (type) {
        DataType.RATIOS -> decodeRatios(payload)
        DataType.BALANCE_SHEET -> decodeBalanceSheet(payload)
        DataType.COMPANY_PROFILE -> decodeProfile(payload)
        DataType.QUOTE -> throw IllegalArgumentException("Quotes are decoded with decodeQuote")
    } as T

    fun encodeQuote(quote: Quote): FirestoreMap = mapOf("price" to quote.price, "currency" to quote.currency)

    fun decodeQuote(payload: FirestoreMap): Quote = Quote(payload.double("price"), payload.string("currency"))

    private fun encodeRatios(ratios: Ratios): FirestoreMap =
        mapOf("items" to ratios.items.map { mapOf("code" to it.code.name, "value" to it.value) })

    private fun decodeRatios(payload: FirestoreMap): Ratios =
        Ratios(
            payload.maps("items").mapNotNull { item ->
                enumValueOrNull<RatioCode>(item.string("code"))?.let { Ratio(it, item.doubleOrNull("value")) }
            },
        )

    private fun encodeBalanceSheet(balanceSheet: BalanceSheet): FirestoreMap =
        mapOf(
            "periods" to balanceSheet.periods.map { period ->
                mapOf(
                    "periodEnd" to period.periodEnd.toString(),
                    "fiscalYear" to period.fiscalYear.toLong(),
                    "fiscalPeriod" to period.fiscalPeriod.name,
                    "filedAt" to period.filedAt?.toString(),
                    "form" to period.form,
                    "items" to period.items.map { item ->
                        mapOf("concept" to item.concept, "label" to item.label, "value" to item.value, "unit" to item.unit)
                    },
                )
            },
        )

    private fun decodeBalanceSheet(payload: FirestoreMap): BalanceSheet =
        BalanceSheet(
            payload.maps("periods").map { period ->
                BalanceSheetPeriod(
                    periodEnd = LocalDate.parse(period.string("periodEnd")),
                    fiscalYear = period.long("fiscalYear").toInt(),
                    fiscalPeriod = FiscalPeriod.valueOf(period.string("fiscalPeriod")),
                    filedAt = period.stringOrNull("filedAt")?.let(LocalDate::parse),
                    form = period.stringOrNull("form"),
                    items = period.maps("items").map { item ->
                        BalanceSheetItem(item.string("concept"), item.string("label"), item.double("value"), item.stringOrNull("unit"))
                    },
                )
            },
        )

    private fun encodeProfile(profile: CompanyProfile): FirestoreMap =
        mapOf(
            "name" to profile.name,
            "country" to profile.country,
            "currency" to profile.currency,
            "sector" to profile.sector,
            "industry" to profile.industry,
            "ipoDate" to profile.ipoDate?.toString(),
            "marketCapitalization" to profile.marketCapitalization,
            "sharesOutstanding" to profile.sharesOutstanding,
            "employees" to profile.employees?.toLong(),
            "website" to profile.website,
            "description" to profile.description,
            "logoUrl" to profile.logoUrl,
        )

    private fun decodeProfile(payload: FirestoreMap): CompanyProfile =
        CompanyProfile(
            name = payload.stringOrNull("name"),
            country = payload.stringOrNull("country"),
            currency = payload.stringOrNull("currency"),
            sector = payload.stringOrNull("sector"),
            industry = payload.stringOrNull("industry"),
            ipoDate = payload.stringOrNull("ipoDate")?.let(LocalDate::parse),
            marketCapitalization = payload.doubleOrNull("marketCapitalization"),
            sharesOutstanding = payload.doubleOrNull("sharesOutstanding"),
            employees = payload.longOrNull("employees")?.toInt(),
            website = payload.stringOrNull("website"),
            description = payload.stringOrNull("description"),
            logoUrl = payload.stringOrNull("logoUrl"),
        )

    private inline fun <reified E : Enum<E>> enumValueOrNull(raw: String): E? = enumValues<E>().firstOrNull { it.name == raw }
}
