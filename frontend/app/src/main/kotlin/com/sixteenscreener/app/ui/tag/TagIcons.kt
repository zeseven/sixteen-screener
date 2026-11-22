package com.sixteenscreener.app.ui.tag

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sixteenscreener.app.R

/** Icon of the tag icon set: [id] is stored in tags, [label] names it for screen readers. */
data class TagIcon(
    val id: String,
    @param:DrawableRes val drawable: Int,
    @param:StringRes val label: Int,
)

/** Icons of the picker shown under one [title]. */
data class TagIconGroup(
    @param:StringRes val title: Int,
    val icons: List<TagIcon>,
)

/**
 * Predefined icon set of user tags (ADR-086): Bootstrap Icons with four Tabler Icons, converted from `frontend/tag-icons`.
 * The ID of an icon is its name in its library; a tag with an ID missing from the set is shown with [FALLBACK].
 */
object TagIcons {
    val groups: List<TagIconGroup> = listOf(
        TagIconGroup(
            title = R.string.tag_icon_group_rank,
            icons = listOf(
                TagIcon("chess-king", R.drawable.ic_tag_chess_king, R.string.tag_icon_chess_king),
                TagIcon("chess-queen", R.drawable.ic_tag_chess_queen, R.string.tag_icon_chess_queen),
                TagIcon("poker-chip", R.drawable.ic_tag_poker_chip, R.string.tag_icon_poker_chip),
                TagIcon("gem", R.drawable.ic_tag_gem, R.string.tag_icon_gem),
                TagIcon("trophy", R.drawable.ic_tag_trophy, R.string.tag_icon_trophy),
                TagIcon("star", R.drawable.ic_tag_star, R.string.tag_icon_star),
            ),
        ),
        TagIconGroup(
            title = R.string.tag_icon_group_money,
            icons = listOf(
                TagIcon("coin", R.drawable.ic_tag_coin, R.string.tag_icon_coin),
                TagIcon("cash-stack", R.drawable.ic_tag_cash_stack, R.string.tag_icon_cash_stack),
                TagIcon("cash", R.drawable.ic_tag_cash, R.string.tag_icon_cash),
                TagIcon("piggy-bank", R.drawable.ic_tag_piggy_bank, R.string.tag_icon_piggy_bank),
                TagIcon("safe", R.drawable.ic_tag_safe, R.string.tag_icon_safe),
                TagIcon("percent", R.drawable.ic_tag_percent, R.string.tag_icon_percent),
            ),
        ),
        TagIconGroup(
            title = R.string.tag_icon_group_trend,
            icons = listOf(
                TagIcon("graph-up-arrow", R.drawable.ic_tag_graph_up_arrow, R.string.tag_icon_graph_up_arrow),
                TagIcon("graph-down-arrow", R.drawable.ic_tag_graph_down_arrow, R.string.tag_icon_graph_down_arrow),
                TagIcon("rocket-takeoff", R.drawable.ic_tag_rocket_takeoff, R.string.tag_icon_rocket_takeoff),
                TagIcon("bullseye", R.drawable.ic_tag_bullseye, R.string.tag_icon_bullseye),
                TagIcon("hourglass-split", R.drawable.ic_tag_hourglass_split, R.string.tag_icon_hourglass_split),
                TagIcon("exclamation-triangle", R.drawable.ic_tag_exclamation_triangle, R.string.tag_icon_exclamation_triangle),
                TagIcon("eye", R.drawable.ic_tag_eye, R.string.tag_icon_eye),
            ),
        ),
        TagIconGroup(
            title = R.string.tag_icon_group_sectors,
            icons = listOf(
                TagIcon("bank", R.drawable.ic_tag_bank, R.string.tag_icon_bank),
                TagIcon("building-factory-2", R.drawable.ic_tag_building_factory_2, R.string.tag_icon_building_factory_2),
                TagIcon("pc-display", R.drawable.ic_tag_pc_display, R.string.tag_icon_pc_display),
                TagIcon("cpu", R.drawable.ic_tag_cpu, R.string.tag_icon_cpu),
                TagIcon("capsule", R.drawable.ic_tag_capsule, R.string.tag_icon_capsule),
                TagIcon("lightning-charge", R.drawable.ic_tag_lightning_charge, R.string.tag_icon_lightning_charge),
                TagIcon("fuel-pump", R.drawable.ic_tag_fuel_pump, R.string.tag_icon_fuel_pump),
                TagIcon("cart", R.drawable.ic_tag_cart, R.string.tag_icon_cart),
                TagIcon("house", R.drawable.ic_tag_house, R.string.tag_icon_house),
                TagIcon("truck", R.drawable.ic_tag_truck, R.string.tag_icon_truck),
                TagIcon("broadcast", R.drawable.ic_tag_broadcast, R.string.tag_icon_broadcast),
                TagIcon("shield-check", R.drawable.ic_tag_shield_check, R.string.tag_icon_shield_check),
                TagIcon("tree", R.drawable.ic_tag_tree, R.string.tag_icon_tree),
            ),
        ),
    )

    val all: List<TagIcon> = groups.flatMap { it.icons }

    val FALLBACK = TagIcon(id = "", drawable = R.drawable.ic_tag_fallback, label = R.string.tag_icon_unknown)

    private val byId: Map<String, TagIcon> = all.associateBy { it.id }

    /** Icon with [id], [FALLBACK] for an ID missing from the set. */
    fun of(id: String): TagIcon = byId[id] ?: FALLBACK
}
