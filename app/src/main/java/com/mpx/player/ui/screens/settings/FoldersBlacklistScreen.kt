package com.mpx.player.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mpx.player.R
import com.mpx.player.data.repository.MediaStoreRepository
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.components.MpxPrimaryButton
import com.mpx.player.ui.components.MpxSecondaryButton
import com.mpx.player.ui.screens.local.isPathBlacklisted
import com.mpx.player.ui.theme.MpxSpaceL
import com.mpx.player.ui.theme.MpxSpaceM
import com.mpx.player.ui.theme.MpxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Folder blacklist management (#158, mpvEx parity). A real setting:
 * blacklisted folder paths are stored in [AppSettings.blacklistedFolders]
 * and hidden from Local and Music (filtered at every load site in
 * LocalScreen and MusicScreen, #300). Mirrors mpvEx's Folders preferences
 * screen: a list with per-item remove, clear-all with confirmation, and
 * an add dialog that scans all media folders (video + audio) with
 * checkboxes.
 *
 * Limitation vs mpvEx: no long-press multi-select delete on the list
 * itself — per-item remove plus clear-all covers the same actions.
 */
@Composable
fun FoldersBlacklistScreen(
    viewModel: SettingsViewModel,
) {
    // Migration Phase 2e: the sorted list and the add/remove/clear
    // writes live in the ViewModel — this screen only renders them.
    // The add dialog's folder scan still builds a MediaStoreRepository
    // by hand (a dialog-local scan, not a getInstance site); wiring it
    // behind an interface is a follow-up, not this step.
    val blacklisted by viewModel.blacklistedFolders.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = MpxSpaceL),
    ) {
        Text(
            text = stringResource(R.string.pref_folders_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MpxTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = MpxSpaceL),
        )
        Spacer(modifier = Modifier.height(MpxSpaceL))

        if (blacklisted.isEmpty()) {
            Text(
                text = stringResource(R.string.pref_folders_empty_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MpxTheme.colors.textMuted,
                modifier = Modifier.padding(horizontal = MpxSpaceL),
            )
        } else {
            PreferenceCard {
                blacklisted.forEachIndexed { index, path ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MpxSpaceL, vertical = MpxSpaceM),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MpxTheme.colors.textSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.size(MpxSpaceM))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = path.substringAfterLast('/').ifBlank { path },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MpxTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MpxTheme.colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = {
                            viewModel.removeBlacklistedFolder(path)
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.pref_folders_remove),
                                tint = MpxTheme.colors.textSecondary,
                            )
                        }
                    }
                    if (index < blacklisted.lastIndex) PreferenceDivider()
                }
            }
            Spacer(modifier = Modifier.height(MpxSpaceL))
            MpxSecondaryButton(
                text = stringResource(R.string.pref_folders_clear_all),
                onClick = { showClearConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MpxSpaceL),
            )
            Spacer(modifier = Modifier.height(MpxSpaceL))
        }

        MpxPrimaryButton(
            text = stringResource(R.string.pref_folders_add),
            onClick = { showAddDialog = true },
            icon = Icons.Filled.Add,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MpxSpaceL),
        )
    }

    if (showAddDialog) {
        FoldersBlacklistAddDialog(
            alreadyBlacklisted = blacklisted.toSet(),
            onAdd = { selected ->
                viewModel.addBlacklistedFolders(selected)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    if (showClearConfirm) {
        MpxDialog(
            title = stringResource(R.string.pref_folders_clear_confirm_title),
            onDismiss = { showClearConfirm = false },
            confirmText = stringResource(R.string.pref_folders_clear_all),
            onConfirm = {
                viewModel.clearBlacklistedFolders()
                showClearConfirm = false
            },
            dismissText = stringResource(R.string.action_cancel),
        ) {
            Text(
                text = stringResource(R.string.pref_folders_clear_confirm_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MpxTheme.colors.textSecondary,
            )
        }
    }
}

// Issue #300: the blacklist hides audio too, so a folder with only songs
// must be black-listable. Video and audio folders are merged on (path,
// name) and de-duplicated by path — a folder holding both videos and
// songs appears once. Only path/name are shown, so neither folder type's
// extra fields (video counts, sizes) are needed here.
private data class BlacklistFolderOption(
    val path: String,
    val name: String,
)

/** Add dialog: scans all media folders (video + audio, excluding already-blacklisted) with checkboxes. */
@Composable
private fun FoldersBlacklistAddDialog(
    alreadyBlacklisted: Set<String>,
    onAdd: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var folders by remember { mutableStateOf<List<BlacklistFolderOption>?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        val scanned = withContext(Dispatchers.IO) {
            val repo = MediaStoreRepository(context)
            (repo.getMediaFolders(showAllFolders = true).map { it.path to it.name } +
                repo.getAudioFolders().map { it.path to it.name })
                .distinctBy { it.first }
                .filter { !isPathBlacklisted(it.first, alreadyBlacklisted) }
                .sortedBy { it.second.lowercase() }
                .map { (path, name) -> BlacklistFolderOption(path = path, name = name) }
        }
        folders = scanned
    }

    val available = folders
    val allSelected = available != null && available.isNotEmpty() && selected.size == available.size

    MpxDialog(
        title = stringResource(R.string.pref_folders_select_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.pref_folders_add),
        onConfirm = { onAdd(selected) },
        dismissText = stringResource(R.string.action_cancel),
    ) {
        when {
            available == null -> {
                Text(
                    text = stringResource(R.string.pref_folders_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MpxTheme.colors.textSecondary,
                )
            }
            available.isEmpty() -> {
                Text(
                    text = stringResource(R.string.pref_folders_none_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MpxTheme.colors.textSecondary,
                )
            }
            else -> {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (allSelected) emptySet()
                                else available.map { it.path }.toSet()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (allSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = MpxTheme.colors.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.size(MpxSpaceM))
                        Text(
                            text = stringResource(
                                if (allSelected) R.string.pref_folders_deselect_all
                                else R.string.pref_folders_select_all,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MpxTheme.colors.textPrimary,
                        )
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(available, key = { it.path }) { folder ->
                            val checked = folder.path in selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (checked) selected - folder.path
                                        else selected + folder.path
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selected = if (isChecked) selected + folder.path
                                        else selected - folder.path
                                    },
                                )
                                Spacer(modifier = Modifier.size(MpxSpaceM))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MpxTheme.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = folder.path,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MpxTheme.colors.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
