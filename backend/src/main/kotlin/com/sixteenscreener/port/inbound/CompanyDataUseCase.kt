package com.sixteenscreener.port.inbound

import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.CompanyProfileData
import com.sixteenscreener.domain.model.PriceAndRatios
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.SourceData
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol

/**
 * Company data of the detailed screen, per source (ADR-026, ADR-027).
 *
 * Every operation returns cached data as is; the source is requested when nothing is cached or when [refresh] is true,
 * within the source request limits. Source failures and reached limits never fail the operation: the cached data,
 * or empty data without a cache, is returned with the corresponding status. A source without the capability gives
 * `NOT_SUPPORTED` without a call. Quotes, ratios and company profiles of a symbol whose exchange is not covered by the
 * source of the exchange directory are `NOT_SUPPORTED` from that source without a call (ADR-062).
 *
 * All operations throw [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown profile, an unknown
 * symbol (neither in the exchange directory nor on a not covered exchange of the static exchange table) or an unknown
 * source. When `source` is null, the profile's default source is used.
 */
interface CompanyDataUseCase {
    /**
     * Latest quote and ratios of [symbol] with the indicators of the profile's settings; the combined status is
     * defined by [PriceAndRatios].
     */
    suspend fun priceAndRatios(profileId: ProfileId, symbol: Symbol, source: SourceId?, refresh: Boolean): PriceAndRatios

    /** Annual balance sheet periods of [symbol], newest first. */
    suspend fun balanceSheet(profileId: ProfileId, symbol: Symbol, source: SourceId?, refresh: Boolean): SourceData<BalanceSheet>

    /** Company profile of [symbol] with its identity (exchange, name, market) resolved by the backend. */
    suspend fun companyProfile(profileId: ProfileId, symbol: Symbol, source: SourceId?, refresh: Boolean): CompanyProfileData
}
