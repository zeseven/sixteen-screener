package com.sixteenscreener.domain.model

import java.time.LocalDate

/**
 * Fiscal period of a balance sheet.
 */
enum class FiscalPeriod {
    FY,
    Q1,
    Q2,
    Q3,
    Q4,
}

/**
 * Balance sheet line item as provided by the source.
 *
 * @property concept line item identifier, e.g. a US GAAP concept name
 * @property label display label
 * @property value reported value
 * @property unit unit of [value], e.g. `USD`
 */
data class BalanceSheetItem(
    val concept: String,
    val label: String,
    val value: Double,
    val unit: String?,
)

/**
 * Balance sheet of one reporting period.
 *
 * @property periodEnd end of the period
 * @property fiscalYear fiscal year
 * @property fiscalPeriod fiscal period
 * @property filedAt filing date
 * @property form filing form, e.g. `10-K`
 * @property items line items
 */
data class BalanceSheetPeriod(
    val periodEnd: LocalDate,
    val fiscalYear: Int,
    val fiscalPeriod: FiscalPeriod,
    val filedAt: LocalDate?,
    val form: String?,
    val items: List<BalanceSheetItem>,
)

/**
 * Annual balance sheets of a company from one source.
 *
 * @property periods periods sorted by period end, newest first
 */
data class BalanceSheet(
    val periods: List<BalanceSheetPeriod>,
)
