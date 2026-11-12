package com.mpx.player.ui.screens.local

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.domain.model.MediaFolder
import com.mpx.player.ui.screens.local.components.formatSize
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxTheme
import java.util.Locale

/**
 * Tree view for Local (#164): the folder hierarchy, inline.
 *
 * The folder list MPx scans is flat (one MediaStore bucket per directory
 * that holds videos), so the tree is derived from paths: a folder's parent
 * is the folder in the list with the longest path that is a strict prefix
 * of its own path. Paths are canonicalized first (#196): primary-storage
 * aliases (/sdcard, /mnt/sdcard, /storage/emulated/legacy, ...) all
 * denote /storage/emulated/0, duplicate paths collapse to the first
 * entry, and a case-insensitive fallback runs when the exact prefix walk
 * finds nothing — otherwise a child whose path string mismatches its
 * parent strands as a root and the parent shows no children. A folder
 * whose parent directory holds no videos of its own never appears in
 * the scan, so its intermediate parents are SYNTHESIZED (#358): without
 * a node for e.g. /Movies, its subfolders strand as roots and the tree
 * has no second level. Synthesized nodes carry the sums of their
 * subtree (counts, size, duration) and open those videos when tapped
 * (their "tree:" id is resolved recursively in getVideosInFolder);
 * no recursive scan runs, and real folders keep their scanned values.
 *
 * Tree mode applies to the folders LIST layout only; the GRID layout
 * renders the flat grid as before, and sort keeps working (children keep
 * the list's sorted order). Videos stay in the folder view — tree rows
 * are folders only, never video leaves. Tapping a folder name still opens
 * it exactly as today; only the leading chevron expands/collapses.
 * The blacklist (#158) is respected because the tree is built from the
 * already-filtered folder list LocalScreen passes in.
 */

// One indent step per tree depth.
private val treeIndentStep = 20.dp
private val treeExpanderSize = 32.dp

data class FolderTreeNode(
    val folder: MediaFolder,
    val children: List<FolderTreeNode>,
)

/** One flattened row of the visible tree: the folder, its depth, and whether it can expand. */
data class VisibleTreeRow(
    val folder: MediaFolder,
    val depth: Int,
    val hasChildren: Boolean,
    val expanded: Boolean,
)

private fun treeKey(folder: MediaFolder): String = folder.id.ifBlank { folder.path }

internal fun normalizedTreePath(path: String): String {
    val trimmed = path.trimEnd('/')
    // Primary-storage aliases that all denote /storage/emulated/0 (#196).
    // MediaStore DATA has historically reported the same directory under
    // different prefixes on different devices/ROMs — and one scan can mix
    // them — so the tree canonicalizes them before any parent matching.
    // Without this, a child reported under an alias never prefix-matches
    // its parent, strands as a top-level root, and the parent shows no
    // children. Canonical paths pass through untouched.
    for ((alias, target) in storagePrefixAliases) {
        if (trimmed == alias) return target
        if (trimmed.startsWith("$alias/")) return target + trimmed.substring(alias.length)
    }
    return trimmed
}

private val storagePrefixAliases = listOf(
    "/sdcard" to "/storage/emulated/0",
    "/mnt/sdcard" to "/storage/emulated/0",
    "/storage/emulated/legacy" to "/storage/emulated/0",
    "/storage/self/primary" to "/storage/emulated/0",
    "/mnt/user/0/primary" to "/storage/emulated/0",
)

/**
 * Synthetic tree nodes (#358) carry ids of the form "tree:<path>".
 * getVideosInFolder resolves them recursively (all videos under that
 * path); real folders keep their bucket ids and direct-videos-only
 * behaviour.
 */
private const val SYNTHETIC_TREE_ID_PREFIX = "tree:"

/** One intermediate parent the scan never listed (it holds no videos directly). */
private fun syntheticTreeFolder(path: String): MediaFolder {
    val segments = normalizedTreePath(path).split('/').filter { it.isNotEmpty() }
    return MediaFolder(
        id = "$SYNTHETIC_TREE_ID_PREFIX$path",
        name = segments.lastOrNull().orEmpty(),
        path = path,
        videoCount = 0,
        totalSizeBytes = 0L,
        totalDurationMs = 0L,
        lastModified = 0L,
        isStorageRemovable = segments.size >= 2 && segments[0] == "storage" &&
            segments[1] != "emulated",
    )
}

/**
 * Any storage volume root (/storage/emulated/0, /storage/<uuid>): the
 * tree's implicit root, never a parent node (#173). A folder entry for
 * the storage root itself swallows the whole tree — every folder's
 * prefix walk reaches it, so the top level becomes a single "0" node.
 */
fun isStorageRootPath(path: String): Boolean {
    val segments = normalizedTreePath(path).split('/').filter { it.isNotEmpty() }
    return (segments.size == 3 && segments[0] == "storage" && segments[1] == "emulated") ||
        (segments.size == 2 && segments[0] == "storage")
}

/**
 * The primary internal storage root (/storage/emulated/0): shown as
 * "Home" when it holds videos of its own (#173). Other volume roots
 * keep their scanned name so two volumes never share one label.
 */
private fun isPrimaryStorageRootPath(path: String): Boolean {
    val segments = normalizedTreePath(path).split('/').filter { it.isNotEmpty() }
    return segments.size == 3 && segments[0] == "storage" && segments[1] == "emulated"
}

/**
 * Builds the forest from the flat folder list, preserving the input
 * order (already sorted by the active comparator) inside every level.
 * Folders with a blank path are roots and never parents. On duplicate
 * paths the first folder wins as the parent node — and as the only node
 * (#196): the scan can list the same directory twice (show-all
 * enumeration uses path.hashCode() ids while MediaStore uses bucket
 * ids), and a duplicate node both renders the folder twice and breaks
 * LazyColumn keys for its children — the second copy's children share
 * keys with the first copy's and never render, so expanding the
 * duplicate shows nothing. Storage roots are never parents (#173):
 * their children become top-level roots, so the tree starts at Home
 * instead of a single "0" node. A primary storage root that holds
 * videos of its own stays as a root, labelled with [homeLabel] when
 * given. Parent matching is by normalized path prefix; when the exact
 * walk finds nothing, a case-insensitive fallback runs, because a
 * child's path can differ from its parent's by case and otherwise the
 * child strands as a root and the parent shows no children (#196).
 */
fun buildFolderTree(folders: List<MediaFolder>, homeLabel: String? = null): List<FolderTreeNode> {
    // Dedupe by normalized path, first wins (#196). Blank paths are
    // exempt — collapsing them would hide folders, and they can never
    // be parents anyway.
    val seenPaths = mutableSetOf<String>()
    val deduped = folders.filter { folder ->
        val path = normalizedTreePath(folder.path)
        path.isBlank() || seenPaths.add(path)
    }
    val byPath = mutableMapOf<String, MediaFolder>()
    for (folder in deduped) {
        val path = normalizedTreePath(folder.path)
        if (path.isNotBlank() && path !in byPath && !isStorageRootPath(path)) {
            byPath[path] = folder
        }
    }
    // Lowercase index for the case-insensitive parent fallback (#196).
    val byLowerPath = mutableMapOf<String, String>()
    for (path in byPath.keys) {
        byLowerPath.putIfAbsent(path.lowercase(Locale.US), path)
    }
    // Issue #358: synthesize the missing intermediate parents. The
    // scan only lists directories that hold videos directly, so a
    // parent like /Movies (no videos of its own) never appears — and
    // without a node for it, its subfolders strand as roots and the
    // tree has no second level. For each scanned folder, walk its
    // ancestors up to (excluding) the storage root; any ancestor
    // absent from byPath — exactly and case-insensitively — becomes a
    // synthetic node, so the chain is complete down to the storage
    // root. Synthetics join byPath/byLowerPath and the parent walk
    // below, exactly like scanned folders.
    val syntheticPaths = mutableSetOf<String>()
    // #375: also dedupe the synthetics case-insensitively among
    // themselves. The check above only looks at scanned folders, so
    // children that disagree on the case of an intermediate segment
    // (e.g. /Movies/Action vs /movies/Comedy) used to synthesize BOTH
    // /Movies and /movies, splitting the tree into two roots — the
    // parent walk's case fallback can't merge them because each child
    // then finds an exact-case parent. First-seen case wins the node.
    val syntheticLowerPaths = mutableSetOf<String>()
    for (folder in deduped) {
        var prefix = normalizedTreePath(folder.path).substringBeforeLast('/', "")
        while (prefix.isNotEmpty() && !isStorageRootPath(prefix)) {
            val lowerPrefix = prefix.lowercase(Locale.US)
            if (prefix !in byPath && lowerPrefix !in byLowerPath &&
                lowerPrefix !in syntheticLowerPaths
            ) {
                syntheticPaths.add(prefix)
                syntheticLowerPaths.add(lowerPrefix)
            }
            prefix = prefix.substringBeforeLast('/', "")
        }
    }
    val syntheticFolders = syntheticPaths.map { syntheticTreeFolder(it) }
    for (synthetic in syntheticFolders) {
        byPath[normalizedTreePath(synthetic.path)] = synthetic
        byLowerPath.putIfAbsent(
            normalizedTreePath(synthetic.path).lowercase(Locale.US),
            normalizedTreePath(synthetic.path),
        )
    }
    // Parent of each folder: the longest other path that is a strict prefix.
    val childrenOf = mutableMapOf<String, MutableList<MediaFolder>>()
    val roots = mutableListOf<MediaFolder>()
    for (folder in deduped + syntheticFolders) {
        val path = normalizedTreePath(folder.path)
        var parentPath: String? = null
        if (path.isNotBlank()) {
            var prefix = path.substringBeforeLast('/', "")
            while (prefix.isNotEmpty()) {
                if (prefix in byPath) {
                    parentPath = prefix
                    break
                }
                prefix = prefix.substringBeforeLast('/', "")
            }
            if (parentPath == null) {
                // Case-insensitive fallback (#196): exact walk found
                // nothing, so the child would strand as a root. Match
                // against the lowercase index and take the original
                // (exact-case) parent path back.
                var lowerPrefix = path.lowercase(Locale.US).substringBeforeLast('/', "")
                while (lowerPrefix.isNotEmpty()) {
                    val match = byLowerPath[lowerPrefix]
                    if (match != null) {
                        parentPath = match
                        break
                    }
                    lowerPrefix = lowerPrefix.substringBeforeLast('/', "")
                }
            }
        }
        if (parentPath == null) {
            roots.add(folder)
        } else {
            childrenOf.getOrPut(parentPath) { mutableListOf() }.add(folder)
        }
    }
    fun toNode(folder: MediaFolder): FolderTreeNode {
        val displayFolder =
            if (homeLabel != null && isPrimaryStorageRootPath(normalizedTreePath(folder.path))) {
                folder.copy(name = homeLabel)
            } else {
                folder
            }
        val children = childrenOf[normalizedTreePath(folder.path)]
            ?.map { toNode(it) } ?: emptyList()
        // Issue #358: a synthetic parent has no videos of its own —
        // its row shows the sums of its subtree (children are already
        // aggregated, so summing the immediate children is enough), and
        // tapping it opens those videos via its "tree:" id. Real
        // folders keep their scanned values and direct-videos-only
        // behaviour.
        val resolvedFolder =
            if (folder.id.startsWith(SYNTHETIC_TREE_ID_PREFIX) && children.isNotEmpty()) {
                val totalBytes = children.sumOf { it.folder.totalSizeBytes }
                displayFolder.copy(
                    videoCount = children.sumOf { it.folder.videoCount },
                    totalSizeBytes = totalBytes,
                    totalDurationMs = children.sumOf { it.folder.totalDurationMs },
                    lastModified = children.maxOfOrNull { it.folder.lastModified } ?: 0L,
                )
            } else {
                displayFolder
            }
        return FolderTreeNode(resolvedFolder, children)
    }
    return roots.map { toNode(it) }
}

/**
 * #375: the immediate subfolders of [parentPath], for the folder view's
 * drill-in (MX/mpvEx model: opening a folder shows its subfolders above
 * its videos). Built through [buildFolderTree], so synthesized
 * intermediate parents work exactly like scanned ones — a child with no
 * videos of its own still appears, with its aggregated counts, and
 * opens via its "tree:" id. Empty when the folder has no children or
 * isn't in the list.
 */
fun subfoldersOf(folders: List<MediaFolder>, parentPath: String): List<MediaFolder> {
    val target = normalizedTreePath(parentPath)
    if (target.isBlank()) return emptyList()
    fun find(nodes: List<FolderTreeNode>): FolderTreeNode? {
        for (node in nodes) {
            if (normalizedTreePath(node.folder.path) == target) return node
            find(node.children)?.let { return it }
        }
        return null
    }
    return find(buildFolderTree(folders))?.children?.map { it.folder } ?: emptyList()
}

/**
 * #375: the parent folder of [folderPath] in the tree, for back-up-one-
 * level. Null when the folder is a root (or isn't in the list) — the
 * caller then goes to the root list instead. Synthesized intermediates
 * count as parents, so back from Action lands on Movies even when
 * Movies holds no videos of its own.
 */
fun parentFolderOf(folders: List<MediaFolder>, folderPath: String): MediaFolder? {
    val target = normalizedTreePath(folderPath)
    if (target.isBlank()) return null
    fun find(nodes: List<FolderTreeNode>, parent: MediaFolder?): MediaFolder? {
        for (node in nodes) {
            if (normalizedTreePath(node.folder.path) == target) return parent
            find(node.children, node.folder)?.let { return it }
        }
        return null
    }
    return find(buildFolderTree(folders), null)
}

/** Flattens the forest to the rows actually visible (collapsed subtrees hidden). */
fun flattenVisibleTree(
    roots: List<FolderTreeNode>,
    expandedKeys: Set<String>,
): List<VisibleTreeRow> {
    val rows = mutableListOf<VisibleTreeRow>()
    fun visit(node: FolderTreeNode, depth: Int) {
        val expanded = treeKey(node.folder) in expandedKeys
        rows.add(
            VisibleTreeRow(
                folder = node.folder,
                depth = depth,
                hasChildren = node.children.isNotEmpty(),
                expanded = expanded,
            )
        )
        if (expanded) {
            node.children.forEach { visit(it, depth + 1) }
        }
    }
    roots.forEach { visit(it, 0) }
    return rows
}

/**
 * The tree list itself: a LazyColumn of [FolderItemRow]s with a leading
 * expander chevron on folders that have children. Row behaviour (tap to
 * open, long-press to multi-select, selection tint) is identical to the
 * flat list — the callbacks are the caller's, unchanged.
 */
@Composable
fun LocalFolderTree(
    folders: List<MediaFolder>,
    expandedKeys: Set<String>,
    onToggleExpand: (MediaFolder) -> Unit,
    showThumbnails: Boolean,
    showSize: Boolean,
    showDate: Boolean,
    showCount: Boolean,
    showFullName: Boolean,
    isSelected: (MediaFolder) -> Boolean,
    onLongClick: (MediaFolder) -> Unit,
    onClick: (MediaFolder) -> Unit,
    // Issue #279: the list state is hoisted by the caller (LocalScreen)
    // so toggling tree <-> flat list keeps the tree's scroll position
    // instead of discarding it. Defaults to a fresh state, as before.
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    // Issue #173: the primary storage root is labelled Home (localized);
    // hoisted out of the remember block — stringResource is a @Composable
    // call and cannot run inside remember's calculation lambda.
    val homeLabel = stringResource(R.string.nav_local)
    val roots = remember(folders, homeLabel) {
        buildFolderTree(folders, homeLabel = homeLabel)
    }
    val visibleRows = remember(roots, expandedKeys) {
        flattenVisibleTree(roots, expandedKeys)
    }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            items(
                items = visibleRows,
                key = { treeKey(it.folder) },
                contentType = { "folder_tree_row" },
            ) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(treeIndentStep * row.depth))
                    if (row.hasChildren) {
                        IconButton(
                            onClick = { onToggleExpand(row.folder) },
                            modifier = Modifier.size(treeExpanderSize),
                        ) {
                            Icon(
                                imageVector = if (row.expanded) Icons.Filled.KeyboardArrowDown
                                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(
                                    if (row.expanded) R.string.tree_collapse
                                    else R.string.tree_expand
                                ),
                                tint = MpxTheme.colors.textSecondary,
                            )
                        }
                    } else {
                        Spacer(Modifier.width(treeExpanderSize))
                    }
                    FolderItemRow(
                        folder = row.folder,
                        showThumbnails = showThumbnails,
                        showSize = showSize,
                        showDate = showDate,
                        isSelected = isSelected(row.folder),
                        onLongClick = { onLongClick(row.folder) },
                        showCount = showCount,
                        showFullName = showFullName,
                        onClick = { onClick(row.folder) },
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(
                        start = 76.dp + treeIndentStep * row.depth
                    ),
                    color = MpxTheme.colors.border,
                    thickness = MpxHairline,
                )
            }
        }
    }
}
