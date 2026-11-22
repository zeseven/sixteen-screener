package com.sixteenscreener.app.ui.tag

/** Number of tag icons shown in a row of limited width and whether `…` replaces the icons that don't fit. */
data class TagIconsFit(
    val visibleIcons: Int,
    val showsEllipsis: Boolean,
) {
    companion object {
        private val NOTHING = TagIconsFit(visibleIcons = 0, showsEllipsis = false)

        /**
         * Fit of [iconCount] icons of [iconWidth] separated by [spacing] into [availableWidth]: all icons when they fit, otherwise as
         * many as fit followed by the ellipsis of [ellipsisWidth]; nothing when not even the ellipsis fits. Widths are in pixels.
         */
        fun of(iconCount: Int, availableWidth: Int, iconWidth: Int, spacing: Int, ellipsisWidth: Int): TagIconsFit {
            if (iconCount <= 0) return NOTHING
            val allIconsWidth = iconCount * iconWidth + (iconCount - 1) * spacing
            return when {
                allIconsWidth <= availableWidth -> TagIconsFit(iconCount, showsEllipsis = false)
                ellipsisWidth > availableWidth -> NOTHING
                else -> TagIconsFit(
                    visibleIcons = ((availableWidth - ellipsisWidth) / (iconWidth + spacing)).coerceIn(0, iconCount - 1),
                    showsEllipsis = true,
                )
            }
        }
    }
}
