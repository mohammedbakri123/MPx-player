package com.mpx.player.ui.screens.settings

import com.mpx.player.R
import com.mpx.player.domain.settings.DEFAULT_PLAYER_MENU_LAYOUT

/**
 * Player Menu layout (#158, mpvEx parity): the basic Player Layout editor
 * controls which rows appear in the Player Menu dialog and in what order.
 * This is MPx's own, simpler take on mpvEx's drag-to-arrange button editor:
 * visibility toggles + up/down reorder, persisted as one comma-separated
 * string. The full drag-to-arrange chrome editor is a follow-up.
 *
 * Stored as visible row ids in display order. Unknown ids are dropped and
 * any known id missing from the stored value is appended at the end, so a
 * stale or partial stored value can never lose a row.
 */
/** All Player Menu row ids, in default display order. */
val PLAYER_MENU_IDS: List<String> = DEFAULT_PLAYER_MENU_LAYOUT.split(",")

/**
 * Parses a stored layout string into the visible row ids in display order.
 * Blank or garbage input yields the default order.
 */
fun parsePlayerMenuLayout(raw: String): List<String> {
    val wanted = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (wanted.isEmpty()) return PLAYER_MENU_IDS
    val visible = wanted.filter { it in PLAYER_MENU_IDS }.toMutableList()
    // Never lose a row to a stale stored value: append missing known ids.
    PLAYER_MENU_IDS.forEach { id -> if (id !in visible) visible.add(id) }
    return visible
}

/** Label string for one Player Menu row id. */
fun playerMenuRowLabelRes(id: String): Int = when (id) {
    "decoder" -> R.string.player_layout_row_decoder
    "audio" -> R.string.player_layout_row_audio
    "subtitles" -> R.string.player_layout_row_subtitles
    "favourite" -> R.string.player_layout_row_favourite
    "shuffle" -> R.string.player_layout_row_shuffle
    "repeat" -> R.string.player_layout_row_repeat
    "pip" -> R.string.player_layout_row_pip
    "background" -> R.string.player_layout_row_background
    "speed" -> R.string.player_layout_row_speed
    "aspect" -> R.string.player_layout_row_aspect
    "rotation" -> R.string.player_layout_row_rotation
    "sleep" -> R.string.player_layout_row_sleep
    "playlist" -> R.string.player_layout_row_playlist
    else -> R.string.player_layout_row_decoder
}
