package com.sixteenscreener.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.model.CompanyIdentity
import com.sixteenscreener.app.domain.model.Symbol
import kotlinx.serialization.Serializable

/** First start profile prompt, shown while no profile is stored. */
@Serializable
data object ProfileRoute : NavKey

/** Main screen with the [MainTab] tabs. */
@Serializable
data object MainRoute : NavKey

/**
 * Detailed screen of [symbol]; [initialCompany] fills the header until the company profile arrives, [initiallyInWatchlist] tells
 * whether the company was in the watchlist when the screen was opened.
 */
@Serializable
data class DetailRoute(
    val symbol: Symbol,
    val initialCompany: CompanyIdentity? = null,
    val initiallyInWatchlist: Boolean = false,
) : NavKey

/** Settings screen. */
@Serializable
data object SettingsRoute : NavKey

/** Tabs of the main screen, in bottom bar order. */
enum class MainTab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    WATCHLIST(R.string.tab_watchlist, R.drawable.ic_watchlist),
    SEARCH(R.string.tab_search, R.drawable.ic_search),
    EXCHANGES(R.string.tab_exchanges, R.drawable.ic_exchanges),
    SOURCES(R.string.tab_sources, R.drawable.ic_sources),
}
