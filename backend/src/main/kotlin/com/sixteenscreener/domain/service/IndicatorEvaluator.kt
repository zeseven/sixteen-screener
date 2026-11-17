package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.Indicator
import com.sixteenscreener.domain.model.IndicatorLevel
import com.sixteenscreener.domain.model.IndicatorSetting
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.Ratios

/**
 * Calculates indicators from ratios (ADR-071, ADR-073, ADR-074).
 */
object IndicatorEvaluator {
    /** Number of indicators shown on the company row. */
    const val SHOWN_INDICATORS = 3

    private val DISPLAYED_LEVELS = listOf(IndicatorLevel.GOOD, IndicatorLevel.WARNING, IndicatorLevel.BAD)

    /**
     * Indicators of [ratios] for [settings] (in settings order), in display order. [price] is the price of a quote of
     * the same source as [ratios], used to calculate a missing P/E; null when there is none.
     */
    fun indicators(settings: List<IndicatorSetting>, ratios: Ratios?, price: Double?): List<Indicator> {
        if (ratios == null) return emptyList()
        val values = valuesForIndicators(ratios, price)
        val inSettingsOrder = settings
            .filter { it.enabled }
            .mapNotNull { setting -> values[setting.ratioCode]?.let { Indicator(setting.ratioCode, setting.levelOf(it)) } }
            .filter { it.level in DISPLAYED_LEVELS }
        return displayOrder(inSettingsOrder)
    }

    /**
     * Ratio values used for indicators: the values of [ratios], with a P/E calculated as [price] / EPS when the source
     * returned no P/E, the EPS is negative and a [price] is available.
     */
    fun valuesForIndicators(ratios: Ratios, price: Double?): Map<RatioCode, Double?> {
        val values = ratios.items.associate { it.code to it.value }
        val eps = values[RatioCode.EPS]
        val calculatedPe = if (values[RatioCode.PE] == null && eps != null && eps < 0 && price != null) price / eps else null
        return calculatedPe?.let { values + (RatioCode.PE to it) } ?: values
    }

    /**
     * Orders indicators given in settings order for display (ADR-073): the first of each level in the order
     * GOOD, WARNING, BAD; free places of the first [SHOWN_INDICATORS] go to the biggest level group, on a tie to the
     * group whose next indicator comes first in settings order; the chosen ones sorted by level, then the rest by level,
     * keeping settings order within a level. NEUTRAL indicators are dropped.
     */
    fun displayOrder(inSettingsOrder: List<Indicator>): List<Indicator> {
        val indexed = inSettingsOrder.withIndex().filter { it.value.level in DISPLAYED_LEVELS }
        val groups = DISPLAYED_LEVELS.map { level -> indexed.filter { it.value.level == level } }.filter { it.isNotEmpty() }
        val shown = fillPlaces(groups, taken = groups.map { 1 })
        val byLevelThenSettings = compareBy<IndexedValue<Indicator>>({ DISPLAYED_LEVELS.indexOf(it.value.level) }, { it.index })
        val shownSorted = shown.sortedWith(byLevelThenSettings)
        val rest = (indexed - shown.toSet()).sortedWith(byLevelThenSettings)
        return (shownSorted + rest).map { it.value }
    }

    private tailrec fun fillPlaces(
        groups: List<List<IndexedValue<Indicator>>>,
        taken: List<Int>,
    ): List<IndexedValue<Indicator>> {
        val candidates = groups.indices.filter { taken[it] < groups[it].size }
        if (taken.sum() >= SHOWN_INDICATORS || candidates.isEmpty()) {
            return groups.flatMapIndexed { index, group -> group.take(taken[index]) }
        }
        val next = candidates.minWith(compareByDescending<Int> { groups[it].size }.thenBy { groups[it][taken[it]].index })
        return fillPlaces(groups, taken.mapIndexed { index, count -> if (index == next) count + 1 else count })
    }
}
