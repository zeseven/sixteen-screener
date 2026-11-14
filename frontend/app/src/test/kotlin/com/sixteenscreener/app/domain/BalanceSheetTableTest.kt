package com.sixteenscreener.app.domain

import com.sixteenscreener.app.domain.model.FiscalPeriod
import com.sixteenscreener.app.testing.CompanyData.item
import com.sixteenscreener.app.testing.CompanyData.period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceSheetTableTest {

    private fun BalanceSheetTable.labels() = rows.map { it.label }

    private fun BalanceSheetTable.valuesOf(label: String) = rows.single { it.label == label }.values

    @Test
    fun `no periods make an empty table`() {
        val table = BalanceSheetTable.of(emptyList())

        assertTrue(table.isEmpty)
        assertEquals(emptyList<BalanceSheetColumn>(), table.columns)
        assertNull(table.unit)
    }

    @Test
    fun `periods without items make columns but no rows`() {
        val table = BalanceSheetTable.of(listOf(period(2026)))

        assertTrue(table.isEmpty)
        assertEquals(listOf(BalanceSheetColumn(2026, FiscalPeriod.FY)), table.columns)
    }

    @Test
    fun `one year keeps the source order of line items`() {
        val table = BalanceSheetTable.of(listOf(period(2026, item("Assets", 3.0), item("AssetsCurrent", 1.0), item("Liabilities", 2.0))))

        assertEquals(listOf("Assets", "AssetsCurrent", "Liabilities"), table.labels())
        assertEquals(listOf(3.0), table.valuesOf("Assets"))
    }

    @Test
    fun `columns are ordered newest first regardless of input order`() {
        val table = BalanceSheetTable.of(listOf(period(2023, item("A", 3.0)), period(2026, item("A", 6.0)), period(2024, item("A", 4.0))))

        assertEquals(listOf(2026, 2024, 2023), table.columns.map { it.fiscalYear })
        assertEquals(listOf(6.0, 4.0, 3.0), table.valuesOf("A"))
    }

    @Test
    fun `many years make one column each`() {
        val periods = (2010..2026).map { year -> period(year, item("Assets", year.toDouble())) }

        val table = BalanceSheetTable.of(periods)

        assertEquals((2026 downTo 2010).toList(), table.columns.map { it.fiscalYear })
        assertEquals((2026 downTo 2010).map { it.toDouble() }, table.valuesOf("Assets"))
    }

    @Test
    fun `rows are aligned by concept when the order differs across years`() {
        val table = BalanceSheetTable.of(
            listOf(
                period(2026, item("Assets", 26.0), item("Liabilities", 16.0)),
                period(2025, item("Liabilities", 15.0), item("Assets", 25.0)),
            ),
        )

        assertEquals(listOf("Assets", "Liabilities"), table.labels())
        assertEquals(listOf(26.0, 25.0), table.valuesOf("Assets"))
        assertEquals(listOf(16.0, 15.0), table.valuesOf("Liabilities"))
    }

    @Test
    fun `line items missing in some years have no value there`() {
        val table = BalanceSheetTable.of(
            listOf(
                period(2026, item("Assets", 26.0), item("Goodwill", 6.0)),
                period(2025, item("Assets", 25.0)),
                period(2024, item("Assets", 24.0), item("Goodwill", 4.0)),
            ),
        )

        assertEquals(listOf(6.0, null, 4.0), table.valuesOf("Goodwill"))
    }

    @Test
    fun `discontinued line items are appended after the newest period's items`() {
        val table = BalanceSheetTable.of(
            listOf(
                period(2026, item("Assets", 26.0), item("Liabilities", 16.0)),
                period(2025, item("OldItem", 5.0), item("Assets", 25.0)),
                period(2024, item("OlderItem", 4.0), item("OldItem", 4.5)),
            ),
        )

        assertEquals(listOf("Assets", "Liabilities", "OldItem", "OlderItem"), table.labels())
        assertEquals(listOf(null, 5.0, 4.5), table.valuesOf("OldItem"))
        assertEquals(listOf(null, null, 4.0), table.valuesOf("OlderItem"))
    }

    @Test
    fun `rows matched by concept show the newest label`() {
        val table = BalanceSheetTable.of(
            listOf(
                period(2026, item("Assets", 26.0, label = "Total assets")),
                period(2025, item("Assets", 25.0, label = "Assets, total")),
            ),
        )

        assertEquals(listOf("Total assets"), table.labels())
        assertEquals(listOf(26.0, 25.0), table.valuesOf("Total assets"))
    }

    @Test
    fun `items without a concept are matched by label`() {
        val table = BalanceSheetTable.of(
            listOf(
                period(2026, item("", 26.0, label = "Cash"), item("", 16.0, label = "Debt")),
                period(2025, item("", 15.0, label = "Debt"), item("", 25.0, label = "Cash")),
            ),
        )

        assertEquals(listOf("Cash", "Debt"), table.labels())
        assertEquals(listOf(26.0, 25.0), table.valuesOf("Cash"))
    }

    @Test
    fun `item without a concept is not matched with a concept equal to its label`() {
        val table = BalanceSheetTable.of(
            listOf(
                period(2026, item("Cash", 26.0, label = "Cash at hand")),
                period(2025, item("", 25.0, label = "Cash")),
            ),
        )

        assertEquals(listOf(listOf(26.0, null), listOf(null, 25.0)), table.rows.map { it.values })
    }

    @Test
    fun `duplicate concept within a period keeps the first value`() {
        val table = BalanceSheetTable.of(listOf(period(2026, item("Assets", 1.0), item("Assets", 2.0))))

        assertEquals(listOf(listOf(1.0)), table.rows.map { it.values })
    }

    @Test
    fun `common unit is set for the table`() {
        val table = BalanceSheetTable.of(listOf(period(2026, item("A", 1.0)), period(2025, item("A", 1.0), item("B", 1.0))))

        assertEquals("USD", table.unit)
    }

    @Test
    fun `different or missing units leave the unit to the rows`() {
        val mixed = BalanceSheetTable.of(listOf(period(2026, item("A", 1.0, unit = "USD"), item("Shares", 1.0, unit = "shares"))))
        val missing = BalanceSheetTable.of(listOf(period(2026, item("A", 1.0, unit = null))))

        assertNull(mixed.unit)
        assertEquals(listOf("USD", "shares"), mixed.rows.map { it.unit })
        assertNull(missing.unit)
    }

    @Test
    fun `quarterly periods keep their fiscal period`() {
        val table = BalanceSheetTable.of(listOf(period(2026, item("A", 1.0), fiscalPeriod = FiscalPeriod.Q1)))

        assertEquals(listOf(BalanceSheetColumn(2026, FiscalPeriod.Q1)), table.columns)
    }
}
