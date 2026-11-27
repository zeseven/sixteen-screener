package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.ExchangeEntry
import com.sixteenscreener.domain.model.KnownExchangeCodes
import com.sixteenscreener.port.inbound.ExchangesUseCase

class ExchangeService(private val marketResolver: MarketResolver) : ExchangesUseCase {

    private val entries: List<ExchangeEntry> =
        (suffixEntries() + usEntries()).sortedWith(compareBy(ExchangeEntry::suffix, ExchangeEntry::mic))

    override fun exchanges(): List<ExchangeEntry> = entries

    private fun suffixEntries(): List<ExchangeEntry> =
        KnownExchangeCodes.mics.mapNotNull { (code, mic) -> entryOf(code.value, mic, code) }

    private fun usEntries(): List<ExchangeEntry> =
        marketResolver.usMics.mapNotNull { mic -> entryOf(US_SUFFIX, mic, ExchangeCode.US) }

    private fun entryOf(suffix: String, mic: String, code: ExchangeCode): ExchangeEntry? {
        val info = marketResolver.find(mic) ?: return null
        val fullName = marketResolver.fullNameOf(mic) ?: return null
        return ExchangeEntry(
            suffix = suffix,
            shortName = info.name,
            fullName = fullName,
            mic = mic,
            eodhdCode = KnownExchangeCodes.eodhdCodeOf(code),
            yahooSuffix = KnownExchangeCodes.yahooSuffixOf(code),
            currency = info.currency,
            timeZone = info.timeZone,
            market = info.market,
            country = info.country,
            mainVenue = info.mainVenue,
        )
    }

    private companion object {
        private const val US_SUFFIX = ""
    }
}
