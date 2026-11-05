package com.mpx.player.ui.screens.settings

import com.mpx.player.R
import com.mpx.player.domain.settings.DEFAULT_PLAYER_MENU_LAYOUT

/**
 * Player Menu layout (#158, mpvEx parity): the basic Player Layout editor
 * controls which rows appear in the Player Menu dialog and in what order.
 * This is MPx's own, simpler take on mpvEx's drag-to-arrange button editor:
 * visibility toggles + up/down reorder, persisted as two comma-separated
 * strings. The full drag-to-arrange chrome editor is a follow-up.
 *
 * Two strings are stored (#201):
 * - visible row ids in display order (playerMenuLayout);
 * - ids the user explicitly hid (hiddenPlayerMenuIds).
 *
 * The split exists because a single visible-only list cannot tell a
 * deliberately hidden id from an id that simply was not stored yet.
 * Hiding used to be a no-op: parse appended every missing known id back
 * (migration safety for stale stored values), so a hidden id reappeared
 * on the next recomposition. Now the append-back runs only for ids in
 * NEITHER list — a known id missing from both is new since the layout
 * was saved (e.g. added in a later version) and stays visible by
 * default, while an id in the hidden set stays hidden.
 *
 * One honest edge: hide never worked before this fix, so a stored layout
 * from the old code can be a subset only because the user tapped hide
 * and the row instantly reappeared — that tap never took visible
 * effect, so the missing id is treated as never-hidden and appended
 * visible on the next parse, exactly as the user always saw it.
 */
/** All Player Menu row ids, in default display order. */
val PLAYER_MENU_IDS: List<String> = DEFAULT_PLAYER_MENU_LAYOUT.split(",")

/**
 * Parses the stored hidden-ids string into the explicitly hidden row ids.
 * Unknown ids are dropped; blank input means nothing is hidden.
 */
fun parseHiddenPlayerMenuIds(hiddenRaw: String): List<String> =
    hiddenRaw.split(",").map { it.trim() }
        .filter { it.isNotEmpty() }.distinct()
        .filter { it in PLAYER_MENU_IDS }

/**
 * Parses a stored layout string into the visible row ids in display order.
 * Blank or garbage input yields the default order, minus any hidden ids.
 * Known ids missing from BOTH the stored visible list and the hidden set
 * are appended at the end (migration: an id added in a later version
 * stays visible by default); an id in the hidden set stays hidden.
 */
fun parsePlayerMenuLayout(raw: String, hiddenRaw: String = ""): List<String> {
    val hidden = parseHiddenPlayerMenuIds(hiddenRaw)
    val wanted = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val visible = if (wanted.isEmpty()) {
        PLAYER_MENU_IDS.toMutableList()
    } else {
        wanted.filter { it in PLAYER_MENU_IDS }.toMutableList()
    }
    // Migration append-back, but only for ids never stored at all: an id
    // in the hidden set was deliberately hidden — never resurrect it.
    PLAYER_MENU_IDS.forEach { id ->
        if (id !in visible && id !in hidden) visible.add(id)
    }
    // Hidden wins on inconsistent state (an id present in both lists).
    return visible.filter { it !in hidden }
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
