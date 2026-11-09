package com.mpx.player.ui.screens.download

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.data.local.entity.DownloadHistoryEntity
import com.mpx.player.download.DownloadedFile
import com.mpx.player.download.MpxDownloader
import com.mpx.player.ui.components.MpxBottomSheet
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Issue #417: the history detail sheet — Seal's `VideoDetailDrawer`
 * (`ui/page/videolist/VideoDetailDrawer.kt:57-215`). A history entry
 * whose file is gone is a dead end in the row alone (Play / Share /
 * Open all resolve the file first and show "file missing"), and the
 * URL is never shown even though the entry carries it. The sheet
 * shows the title, the link (tap copies it, the button opens it in
 * a browser), and the actions that depend on the file: Play and
 * Share while it exists, Download again when it is gone — and
 * Delete, which offers to delete the file together with the record
 * so the two stop drifting apart.
 *
 * The row tap stays Play (the common case keeps one tap); the sheet
 * opens from the row's More button. The file is resolved once when
 * the sheet opens, so the Play/Re-download swap is decided here
 * instead of per row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailSheet(
    entry: DownloadHistoryEntity,
    dateText: String,
    downloader: MpxDownloader,
    onDismiss: () -> Unit,
    onPlay: (DownloadedFile) -> Unit,
    onRedownload: () -> Unit,
    onShare: (DownloadedFile) -> Unit,
    onOpen: (DownloadedFile) -> Unit,
    onDelete: (file: DownloadedFile?, deleteFile: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var file by remember { mutableStateOf<DownloadedFile?>(null) }
    var resolving by remember { mutableStateOf(true) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteFileToo by remember { mutableStateOf(true) }

    LaunchedEffect(entry.id) {
        file = withContext(Dispatchers.IO) { downloader.historyFileIfExists(entry) }
        resolving = false
    }

    fun copyLink() {
        clipboardManager.setText(AnnotatedString(entry.url))
        Toast.makeText(context, context.getString(R.string.download_link_copied), Toast.LENGTH_SHORT).show()
    }

    fun openLink() {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(entry.url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.download_open_link_failed), Toast.LENGTH_SHORT).show()
        }
    }

    MpxBottomSheet(
        onDismiss = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = entry.title,
                color = MpxTheme.colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (entry.uploader != null) "${entry.uploader} · $dateText" else dateText,
                color = MpxTheme.colors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = null,
                    tint = MpxTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.url,
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { copyLink() },
                )
                IconButton(onClick = { copyLink() }) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.download_history_copy_link),
                        tint = MpxTheme.colors.textSecondary,
                    )
                }
                IconButton(onClick = { openLink() }) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.download_history_open_link),
                        tint = MpxTheme.colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (resolving) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MpxTheme.colors.primary,
                    trackColor = MpxTheme.colors.surfaceVariant,
                )
            } else {
                val resolvedFile = file
                if (resolvedFile != null) {
                    SheetActionRow(Icons.Filled.PlayArrow, stringResource(R.string.download_play)) {
                        onPlay(resolvedFile)
                    }
                    SheetActionRow(Icons.Filled.Share, stringResource(R.string.download_share)) {
                        onShare(resolvedFile)
                    }
                } else {
                    SheetActionRow(Icons.Filled.Download, stringResource(R.string.download_history_redownload)) {
                        onRedownload()
                    }
                }
                SheetActionRow(Icons.Filled.Delete, stringResource(R.string.download_history_delete)) {
                    showDeleteConfirm = true
                }
            }
        }
    }

    if (showDeleteConfirm) {
        MpxDialog(
            title = stringResource(R.string.download_history_delete_title),
            onDismiss = { showDeleteConfirm = false },
            confirmText = stringResource(R.string.download_history_delete),
            onConfirm = {
                onDelete(file, deleteFileToo)
                showDeleteConfirm = false
            },
            dismissText = stringResource(R.string.action_cancel),
            body = {
                Column {
                    Text(
                        text = stringResource(R.string.download_history_delete_body, entry.title),
                        color = MpxTheme.colors.textSecondary,
                        fontSize = 14.sp,
                    )
                    if (file != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = deleteFileToo,
                                onCheckedChange = { deleteFileToo = it },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.download_history_delete_file_too),
                                color = MpxTheme.colors.textPrimary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MpxTheme.colors.textPrimary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = MpxTheme.colors.textPrimary,
            fontSize = 15.sp,
        )
    }
}
