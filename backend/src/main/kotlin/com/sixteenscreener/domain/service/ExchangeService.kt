package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.ExchangeEntry
import com.sixteenscreener.domain.model.KnownExchangeCodes
import com.sixteenscreener.port.inbound.ExchangesUseCase

class ExchangeService(private val marketResolver: MarketResolver) : ExchangesUseCase {

    private val entries: List<ExchangeEntry> =
        (suffixEntries() + usEntries()).sortedWith(compareBy(ExchangeEntry::suffix, ExchangeEntry::mic))

    override fun exchanges(): List<ExchangeEntry> = entries

    private fun suffixEntries(): List<ExchangeEntry> =
        KnownExchangeCodes.mics.mapNotNull { (code, mic) -> entryOf(code.value, mic) }

    private fun usEntries(): List<ExchangeEntry> = marketResolver.usMics.mapNotNull { mic -> entryOf(US_SUFFIX, mic) }

    private fun entryOf(suffix: String, mic: String): ExchangeEntry? {
        val info = marketResolver.find(mic) ?: return null
        val fullName = marketResolver.fullNameOf(mic) ?: return null
        return ExchangeEntry(
            suffix = suffix,
            shortName = info.name,
            fullName = fullName,
            mic = mic,
            currency = info.currency,
            timeZone = info.timeZone,
            market = info.market,
        )
    }

    private companion object {
        private const val US_SUFFIX = ""
    }
}
