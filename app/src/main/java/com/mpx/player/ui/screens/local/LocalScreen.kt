package com.mpx.player.ui.screens.local

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.data.repository.MediaRepositoryStub
import com.mpx.player.ui.components.HonestStatusBanner
import com.mpx.player.ui.components.ShortcutChipRow
import com.mpx.player.ui.theme.MpxBlack
import com.mpx.player.ui.theme.MpxBorder
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxSurface
import com.mpx.player.ui.theme.MpxSurfaceElevated
import com.mpx.player.ui.theme.MpxTextMuted
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalScreen(
    onOpenPlayer: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember { MediaRepositoryStub() }
    val folders = remember { repository.getMediaFolders() }
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    var sortField by remember { mutableStateOf(SortField.DATE) }
    var showSortDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var featureDialogMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Media",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MpxTextPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MpxSurface,
                ),
                actions = {
                    IconButton(onClick = {
                        viewMode = if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                    }) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList,
                            contentDescription = "Toggle view",
                            tint = MpxTextPrimary,
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Search indexing will be active in Phase 1")
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MpxTextPrimary,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More options",
                                tint = MpxTextPrimary,
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("View & Sort options") },
                                onClick = {
                                    menuExpanded = false
                                    showSortDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh library") },
                                onClick = {
                                    menuExpanded = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Library refresh is wired in Phase 1")
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenSettings()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenPlayer,
                containerColor = MpxPrimary,
                contentColor = MpxTextPrimary,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Quick play",
                    modifier = Modifier.size(28.dp),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Shortcut Chips Row (Music, Transfer, Private, Playlists, etc.)
            ShortcutChipRow(
                onOpenMusic = onOpenMusic,
                onOpenTransfer = onOpenTransfer,
                onChipClick = { featureName ->
                    featureDialogMessage = "$featureName is scheduled for subsequent phases. Currently previewing layout."
                },
            )

            // Honest milestone notice
            HonestStatusBanner(
                text = "Structure matches MX Player layout. Real MediaStore scanning connects in Phase 1.",
            )

            // Section Header: FOLDERS + Sort Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "FOLDERS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MpxTextSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MpxSurfaceElevated)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${folders.size}",
                            color = MpxTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showSortDialog = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sort: ${sortField.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MpxPrimary,
                        fontSize = 12.sp,
                    )
                }
            }

            // Folder list / grid
            if (viewMode == ViewMode.LIST) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(folders, key = { it.id }) { folder ->
                        FolderItemRow(
                            folder = folder,
                            onClick = onOpenPlayer,
                            onMenuClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Folder options for ${folder.name}")
                                }
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 76.dp),
                            color = MpxBorder,
                            thickness = 0.5.dp,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(folders, key = { it.id }) { folder ->
                        FolderItemGridCard(
                            folder = folder,
                            onClick = onOpenPlayer,
                        )
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        SortViewDialog(
            currentSort = sortField,
            currentView = viewMode,
            onSortChange = {
                sortField = it
            },
            onViewChange = {
                viewMode = it
            },
            onDismiss = { showSortDialog = false },
        )
    }

    if (featureDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { featureDialogMessage = null },
            containerColor = MpxSurface,
            title = { Text("Feature Placeholder", color = MpxTextPrimary) },
            text = { Text(featureDialogMessage ?: "", color = MpxTextSecondary) },
            confirmButton = {
                TextButton(onClick = { featureDialogMessage = null }) {
                    Text("OK", color = MpxPrimary)
                }
            },
        )
    }
}
