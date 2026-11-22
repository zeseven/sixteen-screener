package com.sixteenscreener.app.ui.tag

import com.sixteenscreener.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

class TagIconsTest {

    @Test
    fun `icon set has the 32 icons in picker order and groups`() {
        assertEquals(
            listOf(
                listOf("chess-king", "chess-queen", "poker-chip", "gem", "trophy", "star"),
                listOf("coin", "cash-stack", "cash", "piggy-bank", "safe", "percent"),
                listOf("graph-up-arrow", "graph-down-arrow", "rocket-takeoff", "bullseye", "hourglass-split", "exclamation-triangle", "eye"),
                listOf(
                    "bank", "building-factory-2", "pc-display", "cpu", "capsule", "lightning-charge", "fuel-pump", "cart", "house", "truck",
                    "broadcast", "shield-check", "tree",
                ),
            ),
            TagIcons.groups.map { group -> group.icons.map { it.id } },
        )
        assertEquals(32, TagIcons.all.map { it.id }.toSet().size)
    }

    @Test
    fun `icon IDs are the names of the source SVG files`() {
        val sources = File("../tag-icons").listFiles { file -> file.extension == "svg" }!!.map { it.nameWithoutExtension }.toSet()

        assertEquals(sources, TagIcons.all.map { it.id }.toSet())
    }

    @Test
    fun `every icon has its own drawable and label`() {
        assertEquals(32, TagIcons.all.map { it.drawable }.toSet().size)
        assertEquals(32, TagIcons.all.map { it.label }.toSet().size)
    }

    @Test
    fun `known ID maps to its drawable`() {
        assertEquals(R.drawable.ic_tag_chess_king, TagIcons.of("chess-king").drawable)
        assertEquals(R.drawable.ic_tag_building_factory_2, TagIcons.of("building-factory-2").drawable)
        assertEquals(R.string.tag_icon_piggy_bank, TagIcons.of("piggy-bank").label)
    }

    @Test
    fun `unknown, empty or differently cased ID maps to the fallback icon`() {
        assertSame(TagIcons.FALLBACK, TagIcons.of("crown"))
        assertSame(TagIcons.FALLBACK, TagIcons.of(""))
        assertSame(TagIcons.FALLBACK, TagIcons.of("Chess-King"))
        assertEquals(R.drawable.ic_tag_fallback, TagIcons.FALLBACK.drawable)
    }
}
