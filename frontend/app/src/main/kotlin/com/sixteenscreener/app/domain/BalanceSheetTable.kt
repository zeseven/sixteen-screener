package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.BalanceSheetItem
import com.sixteenscreener.app.domain.model.BalanceSheetPeriod
import com.sixteenscreener.app.domain.model.FiscalPeriod

/**
 * Column of the balance sheet table: one reported period.
 */
data class BalanceSheetColumn(
    /** Fiscal year of the period. */
    val fiscalYear: Int,
    /** Fiscal period within the year; [FiscalPeriod.FY] for annual reports. */
    val fiscalPeriod: FiscalPeriod,
)

/**
 * Line item aligned across periods.
 */
data class BalanceSheetRow(
    /** Concept of the line item, or its label when the source reports no concept. */
    val key: String,
    /** Label from the newest period reporting the line item. */
    val label: String,
    /** Unit from the newest period reporting the line item; shown per row only when the table has no [BalanceSheetTable.unit]. */
    val unit: String?,
    /** Value per column of [BalanceSheetTable.columns], `null` where the period doesn't report the line item. */
    val values: List<Double?>,
)

/**
 * Balance sheet as a table: one column per period, newest first, and one row per line item matched by concept (fallback label).
 * Rows follow the order of the newest period; line items no longer reported are appended in the order of the newest period
 * reporting them.
 */
data class BalanceSheetTable(
    val columns: List<BalanceSheetColumn>,
    val rows: List<BalanceSheetRow>,
    /** Unit shared by every line item, `null` when units differ or are missing. */
    val unit: String?,
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    companion object {
        fun of(periods: List<BalanceSheetPeriod>): BalanceSheetTable {
            val newestFirst = periods.sortedByDescending { it.periodEnd }
            val itemsByPeriod = newestFirst.map { period -> period.items.distinctBy { it.key }.associateBy { it.key } }
            val rows = newestFirst
                .flatMap { period -> period.items.map { it.key } }
                .distinct()
                .map { key -> rowOf(key, itemsByPeriod) }
            val units = newestFirst.flatMap { period -> period.items.map { it.unit } }.distinct()
            return BalanceSheetTable(
                columns = newestFirst.map { BalanceSheetColumn(it.fiscalYear, it.fiscalPeriod) },
                rows = rows,
                unit = units.singleOrNull()?.takeIf { it.isNotBlank() },
            )
        }

        private fun rowOf(key: String, itemsByPeriod: List<Map<String, BalanceSheetItem>>): BalanceSheetRow {
            val newestItem = itemsByPeriod.firstNotNullOf { it[key] }
            return BalanceSheetRow(
                key = key,
                label = newestItem.label,
                unit = newestItem.unit,
                values = itemsByPeriod.map { it[key]?.value },
            )
        }

        private const val LABEL_KEY_PREFIX = "label:"

        private val BalanceSheetItem.key: String get() = concept.ifBlank { "$LABEL_KEY_PREFIX$label" }
    }
}
