package com.sixteenscreener.app.ui.detail.balancesheet

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.BalanceSheetColumn
import com.sixteenscreener.app.domain.BalanceSheetRow
import com.sixteenscreener.app.domain.BalanceSheetTable
import com.sixteenscreener.app.domain.NumberFormats
import com.sixteenscreener.app.domain.model.FiscalPeriod
import com.sixteenscreener.app.domain.model.Source
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.ui.component.FilterDropdown
import com.sixteenscreener.app.ui.theme.SixteenTheme

object BalanceSheetTags {
    const val SOURCE_DROPDOWN = "balance_sheet_source_dropdown"
    const val TABLE = "balance_sheet_table"
    const val YEAR_COLUMNS = "balance_sheet_year_columns"
    const val LABEL = "balance_sheet_label"
    const val YEAR_HEADER = "balance_sheet_year_header"
    const val VALUE = "balance_sheet_value"
    const val EMPTY = "balance_sheet_empty"
}

private val LabelColumnWidth = 152.dp
private val YearColumnWidth = 88.dp
private val RowHeight = 48.dp

/** Source dropdown of the balance sheet tab; every balance sheet source is offered for every company (ADR-047). */
@Composable
fun BalanceSheetSourceDropdown(
    sources: List<Source>,
    selectedSource: SourceId?,
    onSourceSelected: (SourceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = sources.firstOrNull { it.id == selectedSource } ?: return
    FilterDropdown(
        label = stringResource(R.string.detail_balance_sheet_source),
        selected = selected,
        options = sources,
        optionLabel = { it.name },
        onSelect = { onSourceSelected(it.id) },
        isActive = false,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .testTag(BalanceSheetTags.SOURCE_DROPDOWN),
    )
}

/**
 * Balance sheet table (ADR-053): fixed label column, one column per period (newest first) scrolling horizontally,
 * values abbreviated, `-` where a period doesn't report a line item.
 */
@Composable
fun BalanceSheetGrid(table: BalanceSheetTable, modifier: Modifier = Modifier) {
    if (table.isEmpty) {
        Text(
            text = NumberFormats.MISSING,
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier
                .padding(16.dp)
                .testTag(BalanceSheetTags.EMPTY),
        )
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .testTag(BalanceSheetTags.TABLE),
    ) {
        Column(modifier = Modifier.width(LabelColumnWidth)) {
            HeaderCell(table.unit?.let { stringResource(R.string.detail_balance_sheet_unit, it) }.orEmpty(), TextAlign.Start)
            table.rows.forEach { row -> LabelCell(row, showUnit = table.unit == null) }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .testTag(BalanceSheetTags.YEAR_COLUMNS),
        ) {
            table.columns.forEachIndexed { index, column ->
                val title = columnTitle(column)
                Column(modifier = Modifier.width(YearColumnWidth)) {
                    HeaderCell(title, TextAlign.End, Modifier.testTag(BalanceSheetTags.YEAR_HEADER))
                    table.rows.forEach { row ->
                        val value = NumberFormats.abbreviated(row.values[index])
                        ValueCell(value, stringResource(R.string.detail_balance_sheet_cell_description, row.label, title, value))
                    }
                }
            }
        }
    }
}

@Composable
private fun columnTitle(column: BalanceSheetColumn): String =
    if (column.fiscalPeriod == FiscalPeriod.FY || column.fiscalPeriod == FiscalPeriod.UNKNOWN) {
        column.fiscalYear.toString()
    } else {
        stringResource(R.string.detail_balance_sheet_quarter, column.fiscalPeriod.name, column.fiscalYear)
    }

@Composable
private fun HeaderCell(text: String, textAlign: TextAlign, modifier: Modifier = Modifier) {
    Cell {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = textAlign,
            maxLines = 1,
            modifier = modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
    }
}

@Composable
private fun LabelCell(row: BalanceSheetRow, showUnit: Boolean) {
    val label = row.unit?.takeIf { showUnit && it.isNotBlank() }
        ?.let { stringResource(R.string.detail_balance_sheet_label_with_unit, row.label, it) }
        ?: row.label
    Cell {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(BalanceSheetTags.LABEL),
        )
    }
}

@Composable
private fun ValueCell(text: String, description: String) {
    Cell {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description }
                .testTag(BalanceSheetTags.VALUE),
        )
    }
}

@Composable
private fun Cell(content: @Composable () -> Unit) {
    Column(modifier = Modifier.height(RowHeight)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) { content() }
        HorizontalDivider()
    }
}

@Preview(showBackground = true)
@Composable
private fun BalanceSheetGridPreview() {
    SixteenTheme(dynamicColor = false) {
        BalanceSheetGrid(
            BalanceSheetTable(
                columns = (2026 downTo 2021).map { BalanceSheetColumn(it, FiscalPeriod.FY) },
                rows = listOf(
                    BalanceSheetRow("Assets", "Total assets", "USD", List(6) { 352.6e9 }),
                    BalanceSheetRow("Goodwill", "Goodwill", "USD", listOf(null, 1.2e9, null, 1e9, 9e8, 8e8)),
                ),
                unit = "USD",
            ),
        )
    }
}
