package com.sixteenscreener.app.domain.model

/**
 * User tag of a watchlist company (ADR-086).
 */
data class Tag(
    /** ID of an icon of the app's predefined icon set; an unknown ID is shown with a fallback icon. */
    val icon: String,
    /** Free text of 1 to 100 characters, trimmed. */
    val text: String,
) {
    /** Whether [other] is the same tag: equal icons and texts equal ignoring case. */
    fun isSameAs(other: Tag): Boolean = icon == other.icon && text.equals(other.text, ignoreCase = true)
}
