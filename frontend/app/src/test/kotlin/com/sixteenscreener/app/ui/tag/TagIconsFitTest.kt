package com.sixteenscreener.app.ui.tag

import org.junit.Assert.assertEquals
import org.junit.Test

class TagIconsFitTest {

    private fun fit(count: Int, available: Int) = TagIconsFit.of(count, available, iconWidth = 14, spacing = 4, ellipsisWidth = 10)

    @Test
    fun `no icons show nothing`() {
        assertEquals(TagIconsFit(0, showsEllipsis = false), fit(0, 100))
    }

    @Test
    fun `all icons are shown when they fit exactly`() {
        assertEquals(TagIconsFit(3, showsEllipsis = false), fit(3, 3 * 14 + 2 * 4))
    }

    @Test
    fun `icons that don't fit are replaced by the ellipsis`() {
        assertEquals(TagIconsFit(2, showsEllipsis = true), fit(3, 3 * 14 + 2 * 4 - 1))
    }

    @Test
    fun `as many icons as fit before the ellipsis are shown`() {
        assertEquals(TagIconsFit(4, showsEllipsis = true), fit(20, 4 * 18 + 10))
        assertEquals(TagIconsFit(3, showsEllipsis = true), fit(20, 4 * 18 + 9))
    }

    @Test
    fun `only the ellipsis is shown when no icon fits beside it`() {
        assertEquals(TagIconsFit(0, showsEllipsis = true), fit(2, 20))
    }

    @Test
    fun `nothing is shown when not even the ellipsis fits`() {
        assertEquals(TagIconsFit(0, showsEllipsis = false), fit(2, 9))
        assertEquals(TagIconsFit(0, showsEllipsis = false), fit(1, 0))
    }

    @Test
    fun `single icon is shown without ellipsis when it fits`() {
        assertEquals(TagIconsFit(1, showsEllipsis = false), fit(1, 14))
    }
}
