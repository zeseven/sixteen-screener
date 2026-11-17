package com.sixteenscreener.app.ui.component

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.sixteenscreener.app.R

/** Row action removing the company from the watchlist. */
@Composable
fun RemoveFromWatchlistButton(companyName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_delete),
            contentDescription = stringResource(R.string.watchlist_remove_description, companyName),
        )
    }
}

/** Row action adding the company to the watchlist; shows a check mark when it is already there. */
@Composable
fun AddToWatchlistButton(
    companyName: String,
    inWatchlist: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, enabled = !inWatchlist, modifier = modifier) {
        if (inWatchlist) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = stringResource(R.string.watchlist_in_watchlist_description, companyName),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.watchlist_add_description, companyName),
            )
        }
    }
}
