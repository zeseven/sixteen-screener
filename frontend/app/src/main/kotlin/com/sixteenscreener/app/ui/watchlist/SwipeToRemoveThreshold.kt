package com.sixteenscreener.app.ui.watchlist

import kotlin.math.abs

/** How far a watchlist row has to be swiped to be removed, so that short flicks while scrolling snap back instead. */
object SwipeToRemoveThreshold {
    const val ROW_WIDTH_FRACTION = 0.5f

    /** Swipe distance in pixels needed to remove a row of [rowWidth] pixels. */
    fun distance(rowWidth: Float): Float = rowWidth * ROW_WIDTH_FRACTION

    /** Whether a row of [rowWidth] pixels released at [offset] pixels from its rest position is removed; never for an unknown (zero) width. */
    fun isReached(offset: Float, rowWidth: Float): Boolean = rowWidth > 0f && abs(offset) >= distance(rowWidth)
}
