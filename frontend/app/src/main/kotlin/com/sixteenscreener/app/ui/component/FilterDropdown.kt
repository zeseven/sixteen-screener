package com.sixteenscreener.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sixteenscreener.app.R

/**
 * Chip showing "[label]: selected option" that opens a dropdown menu of [options]; highlighted when [isActive].
 */
@Composable
fun <T> FilterDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        FilterChip(
            selected = isActive,
            onClick = { expanded = true },
            label = {
                Text(
                    text = stringResource(R.string.filter_value, label, optionLabel(selected)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                    } else {
                        null
                    },
                    modifier = Modifier.semantics { this.selected = isSelected },
                )
            }
        }
    }
}
