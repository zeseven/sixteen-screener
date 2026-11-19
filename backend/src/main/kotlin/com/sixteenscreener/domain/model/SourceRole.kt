package com.sixteenscreener.domain.model

/**
 * What a source is used for (ADR-083). The role of a company is taken from its exchange.
 */
enum class SourceRole {
    /** Company search. */
    SEARCH,

    /** Data of companies listed on US exchanges (symbols without an exchange suffix). */
    US,

    /** Data of companies listed on any other exchange, EU or not. */
    INTERNATIONAL,
    ;

    companion object {
        /** Role serving the data of the company [symbol]: [US] for US exchanges, [INTERNATIONAL] otherwise. */
        fun of(symbol: Symbol): SourceRole = if (symbol.exchangeCode == ExchangeCode.US) US else INTERNATIONAL
    }
}

/**
 * Default source of each role (ADR-083).
 *
 * @property search default source of company search
 * @property us default source of companies of US exchanges
 * @property international default source of companies of other exchanges
 */
data class DefaultSources(
    val search: SourceId,
    val us: SourceId,
    val international: SourceId,
) {
    /** Default source of [role]. */
    fun of(role: SourceRole): SourceId = when (role) {
        SourceRole.SEARCH -> search
        SourceRole.US -> us
        SourceRole.INTERNATIONAL -> international
    }

    /** Roles [source] is the default source of, in role order. */
    fun rolesOf(source: SourceId): List<SourceRole> = SourceRole.entries.filter { of(it) == source }
}
