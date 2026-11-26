package com.sixteenscreener.port.inbound

import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.CompanyProfileData
import com.sixteenscreener.domain.model.DividendsData
import com.sixteenscreener.domain.model.PriceAndRatios
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.RequestTrigger
import com.sixteenscreener.domain.model.SourceData
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol

/**
 * Company data of the detailed screen, per source (ADR-026, ADR-027).
 *
 * Whether the source is called depends on its automatic requests setting and the request trigger (ADR-083):
 * - a source with automatic requests on is called for [RequestTrigger.AUTO] only when nothing is cached (cached data is
 *   returned as is), and always for [RequestTrigger.MANUAL];
 * - a source with automatic requests off is never called for [RequestTrigger.AUTO]: cached data is returned as is, and
 *   without cached data empty data with `NOT_REQUESTED`; for [RequestTrigger.MANUAL] it is called only when nothing is
 *   cached or the cached data is older than 1 hour.
 *
 * Calls are made within the source request limits. Source failures and reached limits never fail the operation: the
 * cached data, or empty data without a cache, is returned with the corresponding status. A source without the
 * capability, or not configured for the role of the company (`US` for US exchanges, `INTERNATIONAL` otherwise), gives
 * `NOT_SUPPORTED` without a call; SEC EDGAR is accepted for the balance sheet of a US company (ADR-083).
 *
 * An `AUTO` request that doesn't call a source with a warm-up (Yahoo Finance) starts the warm-up in the background (ADR-088).
 *
 * All operations throw [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown profile, an unknown
 * symbol (neither in the exchange directory nor on a not covered exchange of the static exchange table) or an unknown
 * source. When `source` is null, the profile's default source of the company's role is used.
 */
interface CompanyDataUseCase {
    /**
     * Latest quote and ratios of [symbol] with the indicators of the profile's settings; the combined status is
     * defined by [PriceAndRatios].
     */
    suspend fun priceAndRatios(profileId: ProfileId, symbol: Symbol, source: SourceId?, trigger: RequestTrigger): PriceAndRatios

    /** Dividends of [symbol] of the last year, newest first, with the trailing yield from the cached quote of the source. */
    suspend fun dividends(profileId: ProfileId, symbol: Symbol, source: SourceId?, trigger: RequestTrigger): DividendsData

    /** Annual balance sheet periods of [symbol], newest first. */
    suspend fun balanceSheet(profileId: ProfileId, symbol: Symbol, source: SourceId?, trigger: RequestTrigger): SourceData<BalanceSheet>

    /** Company profile of [symbol] with its identity (exchange, name, market) resolved by the backend. */
    suspend fun companyProfile(profileId: ProfileId, symbol: Symbol, source: SourceId?, trigger: RequestTrigger): CompanyProfileData
}
