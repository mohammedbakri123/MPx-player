package com.mpx.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------
// Shared empty / placeholder states. Nothing here pretends a
// feature works: data, playback, transfer, and downloads are
// all unwired in this skeleton.
// ------------------------------------------------------------------

@Composable
private fun EmptyState(title: String, message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalScreen(onOpenPlayer: () -> Unit, onOpenMusic: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Media") },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Filled.Folder, null) }
                    IconButton(onClick = {}) { Icon(Icons.Filled.Search, null) }
                    IconButton(onClick = {}) { Icon(Icons.Filled.MoreVert, null) }
                },
            )
        },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(
                onClick = onOpenPlayer,
            ) { Icon(Icons.Filled.PlayArrow, null) }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = true,
                    onClick = onOpenMusic,
                    label = { Text("Music") },
                )
                listOf(
                    "File Transfer",
                    "Status Saver",
                    "My Playlists",
                    "Cleaner",
                    "Privacy",
                ).forEach { label ->
                    FilterChip(
                        selected = false,
                        onClick = {},
                        enabled = false,
                        label = { Text(label) },
                    )
                }
            }
            Text(
                "FOLDERS",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            // No folder data yet: MediaStore scanning is not
            // wired. This empty state replaces invented folders.
            EmptyState(
                title = "No folders loaded yet",
                message = "MediaStore scanning is not built yet, " +
                    "so no real folders are shown here.",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(onOpenPlayer: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Tracks", "Playlists", "Albums", "Artists", "Folders")
    Scaffold(
        topBar = { TopAppBar(title = { Text("Music") }) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(label) },
                    )
                }
            }
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search Songs...") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                )
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onOpenPlayer) {
                    Icon(Icons.Filled.Shuffle, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Shuffle All")
                }
            }
            EmptyState(
                title = "No tracks loaded yet",
                message = "Track data comes from the same unwired " +
                    "MediaStore layer as Local.",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Transfer") }) },
    ) { padding ->
        Column(Modifier.padding(padding).padding(20.dp)) {
            Text("Downloader", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Placeholder only — downloads are not built yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = "",
                onValueChange = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste a video link here...") },
                leadingIcon = { Icon(Icons.Filled.Link, null) },
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {},
                enabled = false,
            ) {
                Icon(Icons.Filled.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Download")
            }
            Spacer(Modifier.height(32.dp))
            Text("No downloads", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Downloaded files will appear here once the downloader is built.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(onOpenSettings: () -> Unit, onOpenMusic: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Me") }) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp)) {
            item {
                Text("Tools", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "File Transfer, Private Folder, Video Playlists, " +
                        "Media Manager, Local Network, Cloud Drive, " +
                        "Recycle Bin: Soon. Music Player opens Music.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }
            item {
                ListItem(
                    headlineContent = { Text("Music Player") },
                    modifier = Modifier.clickable { onOpenMusic() },
                )
                ListItem(
                    headlineContent = { Text("App Theme") },
                    supportingContent = { Text("Dark") },
                )
                ListItem(
                    headlineContent = { Text("Settings") },
                    modifier = Modifier.clickable { onOpenSettings() },
                )
                ListItem(
                    headlineContent = { Text("Status Saver") },
                    supportingContent = { Text("Soon") },
                )
                ListItem(
                    headlineContent = { Text("Custom Pop-up Play") },
                    supportingContent = { Text("Soon") },
                )
                ListItem(
                    headlineContent = { Text("Legal") },
                    supportingContent = {
                        Text("A local player — your files never leave your device.")
                    },
                )
                ListItem(
                    headlineContent = { Text("Help") },
                    supportingContent = { Text("Soon") },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val rows = listOf(
        "List", "Player", "Decoder", "Audio",
        "Subtitle", "General", "Development", "App Language",
    )
    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(rows) { label ->
                ListItem(
                    headlineContent = { Text(label) },
                    supportingContent = { Text("Coming soon") },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(onClose: () -> Unit) {
    var playing by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.KeyboardArrowDown, null)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.MoreVert, null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize(),
        ) {
            // 16:9 black video area. No engine is wired, so
            // this is a shell, not a playing video.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No video loaded — player engine not wired yet",
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "MPx Player",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(24.dp))
            Slider(value = 0f, onValueChange = {})
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("0:00", color = Color.White.copy(alpha = 0.7f))
                Text("0:00", color = Color.White.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Shuffle, null, tint = Color.White)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.SkipPrevious, null, tint = Color.White)
                }
                IconButton(onClick = { playing = !playing }) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.SkipNext, null, tint = Color.White)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Repeat, null, tint = Color.White)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Double-tap to play/pause • Drag sideways to seek",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
            // Close is also available from the top bar; this
            // row keeps the shell usable without a gesture.
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onClose) {
                Icon(Icons.Filled.Close, null)
                Spacer(Modifier.width(4.dp))
                Text("Close player")
            }
        }
    }
}
