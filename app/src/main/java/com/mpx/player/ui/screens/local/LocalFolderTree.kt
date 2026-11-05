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
import com.mpx.player.ui.components.FastScrollbar
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
 * the scan, so it has no node — its subfolders show as roots instead.
 * Nothing is synthesized and no recursive scan runs; counts, sizes and
 * durations are the scanned values, exactly as in Folder mode.
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

private fun normalizedTreePath(path: String): String {
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
    // Parent of each folder: the longest other path that is a strict prefix.
    val childrenOf = mutableMapOf<String, MutableList<MediaFolder>>()
    val roots = mutableListOf<MediaFolder>()
    for (folder in deduped) {
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
        return FolderTreeNode(displayFolder, children)
    }
    return roots.map { toNode(it) }
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
    val listState = rememberLazyListState()
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
        FastScrollbar(
            listState = listState,
            itemCount = visibleRows.size,
            getSectionText = { index ->
                visibleRows.getOrNull(index)?.folder?.name?.take(1)
                    ?.uppercase(Locale.US) ?: ""
            },
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}
