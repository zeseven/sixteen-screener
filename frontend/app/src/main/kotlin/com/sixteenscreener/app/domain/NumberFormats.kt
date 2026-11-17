package com.sixteenscreener.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * Number formatting for displayed values; missing values are shown as [MISSING].
 */
object NumberFormats {
    const val MISSING = "-"

    private const val THOUSAND = 1_000.0
    private const val MAX_THRESHOLD_FRACTION_DIGITS = 4
    private val SCALES = listOf("" to 1.0, "K" to 1e3, "M" to 1e6, "B" to 1e9, "T" to 1e12)

    /** Price with 2 decimals followed by [currency], e.g. `1,234.50 USD`. */
    fun price(value: Double?, currency: String?): String = value
        ?.let { pattern("#,##0.00").format(it) }
        ?.let { formatted -> currency?.takeIf(String::isNotBlank)?.let { "$formatted $it" } ?: formatted }
        ?: MISSING

    /** Value with up to [maxFractionDigits] decimals, e.g. a ratio. */
    fun decimal(value: Double?, maxFractionDigits: Int = 2): String =
        value?.let { pattern("#,##0." + "#".repeat(maxFractionDigits)).format(roundedSigned(it, maxFractionDigits)) } ?: MISSING

    /**
     * Indicator threshold with the decimals of its [step] (`12.5` with step `0.5`, `25.0` with step `0.5`, `25` with step `1`),
     * extended up to [MAX_THRESHOLD_FRACTION_DIGITS] decimals for a value that is not a multiple of the step.
     */
    fun threshold(value: Double, step: Double): String {
        val stepDigits = fractionDigits(step).coerceAtMost(MAX_THRESHOLD_FRACTION_DIGITS)
        val extraDigits = (MAX_THRESHOLD_FRACTION_DIGITS - stepDigits).coerceAtLeast(0)
        val fraction = "0".repeat(stepDigits) + "#".repeat(extraDigits)
        return pattern(if (fraction.isEmpty()) "0" else "0.$fraction").format(roundedSigned(value, MAX_THRESHOLD_FRACTION_DIGITS))
    }

    private fun fractionDigits(value: Double): Int =
        BigDecimal.valueOf(value).stripTrailingZeros().scale().coerceAtLeast(0)

    /** Whole number with grouping, e.g. a number of employees. */
    fun integer(value: Long?): String = value?.let { pattern("#,##0").format(it) } ?: MISSING

    /** Large value abbreviated to one decimal with `K`, `M`, `B` or `T`, e.g. `352.6 B`; values below 1,000 are not abbreviated. */
    fun abbreviated(value: Double?): String {
        value ?: return MISSING
        val magnitude = abs(value)
        var scaleIndex = SCALES.indexOfLast { (_, factor) -> magnitude >= factor }.coerceAtLeast(0)
        var scaled = roundToOneDecimal(magnitude / SCALES[scaleIndex].second)
        if (scaled >= BigDecimal.valueOf(THOUSAND) && scaleIndex < SCALES.lastIndex) {
            scaleIndex++
            scaled = roundToOneDecimal(magnitude / SCALES[scaleIndex].second)
        }
        val sign = if (value < 0 && scaled.signum() != 0) "-" else ""
        val suffix = SCALES[scaleIndex].first.let { if (it.isEmpty()) "" else " $it" }
        return sign + pattern("#,##0.#").format(scaled) + suffix
    }

    private fun roundToOneDecimal(value: Double): BigDecimal = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP)

    private fun roundedSigned(value: Double, fractionDigits: Int): BigDecimal =
        BigDecimal.valueOf(value).setScale(fractionDigits, RoundingMode.HALF_UP).let { if (it.signum() == 0) BigDecimal.ZERO else it }

    private fun pattern(pattern: String) = DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US)).apply {
        roundingMode = RoundingMode.HALF_UP
    }
}
