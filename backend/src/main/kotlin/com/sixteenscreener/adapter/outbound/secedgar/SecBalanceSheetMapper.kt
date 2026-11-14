package com.sixteenscreener.adapter.outbound.secedgar

import com.sixteenscreener.domain.model.BalanceSheet
import com.sixteenscreener.domain.model.BalanceSheetItem
import com.sixteenscreener.domain.model.BalanceSheetPeriod
import com.sixteenscreener.domain.model.FiscalPeriod
import java.time.LocalDate

object SecBalanceSheetConcepts {
    val ALL: List<String> = listOf(
        "Assets",
        "AssetsCurrent",
        "CashAndCashEquivalentsAtCarryingValue",
        "ShortTermInvestments",
        "MarketableSecuritiesCurrent",
        "AccountsReceivableNetCurrent",
        "InventoryNet",
        "AssetsNoncurrent",
        "PropertyPlantAndEquipmentNet",
        "OperatingLeaseRightOfUseAsset",
        "Goodwill",
        "IntangibleAssetsNetExcludingGoodwill",
        "Liabilities",
        "LiabilitiesCurrent",
        "AccountsPayableCurrent",
        "LongTermDebtCurrent",
        "LiabilitiesNoncurrent",
        "LongTermDebtNoncurrent",
        "OperatingLeaseLiabilityNoncurrent",
        "StockholdersEquity",
        "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest",
        "RetainedEarningsAccumulatedDeficit",
        "AccumulatedOtherComprehensiveIncomeLossNetOfTax",
        "LiabilitiesAndStockholdersEquity",
        "CommonStockSharesIssued",
        "CommonStockSharesOutstanding",
    )
}

object SecBalanceSheetMapper {
    private val ANNUAL_FORMS = setOf("10-K", "10-K/A")
    private const val AMENDMENT_SUFFIX = "/A"
    private const val FISCAL_YEAR = "FY"

    private data class Fact(
        val concept: String,
        val label: String,
        val unit: String,
        val end: LocalDate,
        val value: Double,
        val accn: String,
    )

    private data class Filing(
        val accn: String,
        val fiscalYear: Int,
        val filed: LocalDate,
        val form: String,
        val balanceSheetDate: LocalDate,
    ) {
        val isAmendment: Boolean get() = form.endsWith(AMENDMENT_SUFFIX)

        fun fiscalYearOf(periodEnd: LocalDate): Int = fiscalYear - (balanceSheetDate.year - periodEnd.year)
    }

    fun balanceSheet(concepts: Map<String, SecConceptDto>): BalanceSheet? {
        val annual = SecBalanceSheetConcepts.ALL.flatMap { concept ->
            concepts[concept]?.let { annualFacts(concept, it) }.orEmpty()
        }
        if (annual.isEmpty()) return null
        val filings = filingsOf(annual)
        val facts = annual.map { it.fact }
        val periods = facts.groupBy { it.end }
            .map { (end, periodFacts) -> period(end, periodFacts, filings) }
            .sortedByDescending { it.periodEnd }
        return BalanceSheet(periods)
    }

    private class AnnualFact(val fact: Fact, val fiscalYear: Int, val form: String, val filed: LocalDate)

    private fun annualFacts(concept: String, dto: SecConceptDto): List<AnnualFact> =
        dto.units.flatMap { (unit, facts) ->
            facts.filter { it.start == null && it.form in ANNUAL_FORMS && it.fp == FISCAL_YEAR }
                .mapNotNull { fact ->
                    val fiscalYear = fact.fy ?: return@mapNotNull null
                    AnnualFact(
                        fact = Fact(concept, dto.label ?: concept, unit, LocalDate.parse(fact.end), fact.value, fact.accn),
                        fiscalYear = fiscalYear,
                        form = fact.form,
                        filed = LocalDate.parse(fact.filed),
                    )
                }
        }

    private fun filingsOf(facts: List<AnnualFact>): Map<String, Filing> =
        facts.groupBy { it.fact.accn }.mapValues { (accn, filingFacts) ->
            val first = filingFacts.first()
            Filing(
                accn = accn,
                fiscalYear = first.fiscalYear,
                filed = filingFacts.maxOf { it.filed },
                form = first.form,
                balanceSheetDate = filingFacts.maxOf { it.fact.end },
            )
        }

    private fun period(end: LocalDate, facts: List<Fact>, filings: Map<String, Filing>): BalanceSheetPeriod {
        val ranking = compareByDescending<Filing> { it.balanceSheetDate == end }
            .thenByDescending { it.filed }
            .thenByDescending { it.isAmendment }
            .thenByDescending { it.accn }
        val ranked = facts.map { filings.getValue(it.accn) }.distinct().sortedWith(ranking)
        val rankOf = ranked.withIndex().associate { (index, filing) -> filing.accn to index }
        val best = ranked.first()
        val items = facts.groupBy { it.concept to it.unit }
            .values
            .map { candidates -> candidates.minBy { rankOf.getValue(it.accn) } }
            .sortedWith(compareBy({ SecBalanceSheetConcepts.ALL.indexOf(it.concept) }, { it.unit }))
            .map { BalanceSheetItem(it.concept, it.label, it.value, it.unit) }
        return BalanceSheetPeriod(
            periodEnd = end,
            fiscalYear = best.fiscalYearOf(end),
            fiscalPeriod = FiscalPeriod.FY,
            filedAt = best.filed,
            form = best.form,
            items = items,
        )
    }
}
