package com.sixteenscreener.domain.model

/**
 * Canonical company identifier: the ticker for US companies, the ticker with an exchange suffix otherwise
 * (`AAPL`, `SAP.DE`, `SHEL.L`). Matches the `Symbol` pattern of the API spec.
 */
@JvmInline
value class Symbol private constructor(val value: String) {

    /** Exchange code taken from the suffix; [ExchangeCode.US] when the symbol has no known exchange suffix (ADR-034). */
    val exchangeCode: ExchangeCode
        get() = knownSuffix()?.let(::ExchangeCode) ?: ExchangeCode.US

    /** Ticker without the exchange suffix (`SAP` for `SAP.DE`, `BRK.B` for `BRK.B`). */
    val ticker: String
        get() = knownSuffix()?.let { value.removeSuffix(".$it") } ?: value

    private fun knownSuffix(): String? =
        value.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.isNotEmpty() && it.length < value.length - 1 && KnownExchangeCodes.isKnown(it) }

    override fun toString(): String = value

    companion object {
        private val PATTERN = Regex("^[A-Z0-9][A-Z0-9.\\-]{0,19}$")

        /** Parses [raw] as a symbol, throwing [InvalidInputException] when it doesn't match the spec pattern. */
        fun of(raw: String): Symbol =
            parseOrNull(raw) ?: throw InvalidInputException("Invalid symbol '$raw'")

        /** Parses [raw] as a symbol, returning null when it doesn't match the spec pattern. */
        fun parseOrNull(raw: String): Symbol? = raw.takeIf(PATTERN::matches)?.let(::Symbol)
    }
}
