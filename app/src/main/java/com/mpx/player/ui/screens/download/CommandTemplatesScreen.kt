package com.mpx.player.ui.screens.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.data.local.entity.CommandTemplateEntity
import com.mpx.player.download.MpxDownloader
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.theme.MpxTheme
import kotlinx.coroutines.launch

/**
 * Issue #410: the command templates page — Seal's TemplateListPage.
 * Lists the saved custom commands (name + command preview), newest
 * first. Tap a row to edit it (shared editor dialog), duplicate
 * copies it with " (copy)" after the name, delete removes it. The
 * "+" in the top bar creates a new one. Templates are picked from
 * the custom-command editor; picking one fills the single command
 * slot, so the download path needs no changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandTemplatesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val downloader = remember(context) { MpxDownloader.getInstance(context) }
    val scope = rememberCoroutineScope()

    val templates by downloader.getCommandTemplates().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<CommandTemplateEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.download_command_templates_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MpxTheme.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back),
                            tint = MpxTheme.colors.textPrimary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.download_template_new_title),
                            tint = MpxTheme.colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MpxTheme.colors.black,
                ),
            )
        },
    ) { padding ->
        if (templates.isEmpty()) {
            EmptyStateView(
                title = stringResource(R.string.download_command_templates_title),
                message = stringResource(R.string.download_command_templates_none),
                icon = Icons.Filled.Code,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                items(templates, key = { it.id }) { template ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { editing = template }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = template.name,
                                color = MpxTheme.colors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = template.template,
                                color = MpxTheme.colors.textSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch { downloader.duplicateCommandTemplate(template.id) }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.download_template_duplicate),
                                tint = MpxTheme.colors.textSecondary,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch { downloader.deleteCommandTemplate(template.id) }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.download_template_delete),
                                tint = MpxTheme.colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        if (creating) {
            TemplateEditorDialog(
                existing = null,
                onSave = { name, template ->
                    creating = false
                    scope.launch { downloader.saveCommandTemplate(null, name, template) }
                },
                onDismiss = { creating = false },
            )
        }
        editing?.let { template ->
            TemplateEditorDialog(
                existing = template,
                onSave = { name, text ->
                    editing = null
                    scope.launch { downloader.saveCommandTemplate(template.id, name, text) }
                },
                onDismiss = { editing = null },
            )
        }
    }
}
