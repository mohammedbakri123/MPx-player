package com.mpx.player.util

import java.text.Normalizer
import java.util.Locale

/**
 * Hand-rolled fuzzy search for the Local search page (#142). No new
 * dependency. Matching is case- and diacritic-insensitive (é matches e,
 * ñ matches n). Results are ranked prefix first, then substring, then
 * subsequence (fuzzy), and alphabetically inside each rank so the order
 * is stable across keystrokes.
 */

/** Lowercase + strip diacritics, so "cafe" matches "Café". */
fun String.normalizedForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .lowercase(Locale.ROOT)

/**
 * Rank of [query] against a single [target] string, or null when it does
 * not match. 0 = target starts with query, 1 = target contains query,
 * 2 = query is a subsequence of target (fuzzy: every query char appears
 * in target, in order, possibly with gaps).
 */
fun fuzzyMatchRank(query: String, target: String): Int? {
    val q = query.normalizedForSearch()
    if (q.isEmpty()) return null
    val t = target.normalizedForSearch()
    if (t.startsWith(q)) return 0
    if (t.contains(q)) return 1
    var qi = 0
    for (c in t) {
        if (qi < q.length && c == q[qi]) qi++
    }
    return if (qi == q.length) 2 else null
}

/**
 * Best rank of [query] against a display [name], falling back to the
 * [path]. A path hit only ever counts as a substring hit (rank 1): typing
 * a parent folder name should still find the files inside it, but a
 * name prefix hit (rank 0) always outranks it.
 */
fun fuzzyMatchRank(query: String, name: String, path: String): Int? {
    val nameRank = fuzzyMatchRank(query, name)
    val q = query.normalizedForSearch()
    val pathRank =
        if (q.isNotEmpty() && path.normalizedForSearch().contains(q)) 1 else null
    return listOfNotNull(nameRank, pathRank).minOrNull()
}

/**
 * Filter [items] by [query] and return them ranked. An empty query
 * returns the list unchanged (the caller shows its hint state instead).
 * Runs over the already-loaded in-memory lists — no scan per keystroke.
 */
fun <T> fuzzySearch(
    query: String,
    items: List<T>,
    nameOf: (T) -> String,
    pathOf: (T) -> String,
): List<T> {
    val q = query.trim()
    if (q.isBlank()) return items
    return items.mapNotNull { item ->
        fuzzyMatchRank(q, nameOf(item), pathOf(item))?.let { rank ->
            rank to item
        }
    }.sortedWith(
        compareBy<Pair<Int, T>> { it.first }
            .thenBy { nameOf(it.second).lowercase(Locale.ROOT) },
    ).map { it.second }
}
