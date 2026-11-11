package com.mpx.player.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.result.ActivityResultLauncher
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import org.koin.compose.viewmodel.koinViewModel
import com.mpx.player.data.settings.SettingsBackup
import com.mpx.player.playback.engine.MpvConfigSync
import com.mpx.player.ui.ActivityResultHandler
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxSpaceL
import com.mpx.player.ui.theme.MpxSpaceM
import com.mpx.player.ui.theme.MpxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Issue #431 (houses #430, #432, #433): the Advanced settings screen,
 * mirroring mpvEx's Advanced screen — Backup & Restore, MPV
 * Configuration, Scripts — in MPx's own Compose style, strings and
 * navigation. Reached from Settings > Advanced > Advanced settings.
 *
 * The mpv.conf / input.conf editors are hosted here as internal
 * state (same drill-down pattern SettingsScreen uses): opening one
 * replaces the list, back closes the editor first. The editors write
 * filesDir/mpv (what libmpv reads) plus the pref cache, so the rows
 * below show the cached text's first line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    onNavigateBack: () -> Unit,
    // Issue #432: the mpv config folder picker — the launcher lives
    // on the Activity (MainActivity), this screen only supplies the
    // callback, same as the SD-card picker in SettingsScreen.
    mpvConfigFolderLauncher: ActivityResultLauncher<Uri?>? = null,
    mpvConfigFolderHandler: ActivityResultHandler<Uri?>? = null,
    // Issue #433: the full-settings XML export/import pickers — same
    // pattern (launchers on the Activity, callbacks here).
    settingsExportLauncher: ActivityResultLauncher<String>? = null,
    settingsExportHandler: ActivityResultHandler<Uri?>? = null,
    settingsImportLauncher: ActivityResultLauncher<Array<String>>? = null,
    settingsImportHandler: ActivityResultHandler<Uri?>? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Migration Phase 2e: settings load/save live in SettingsViewModel
    // now — the screen holds no repository.
    val viewModel: SettingsViewModel = koinViewModel()
    val settings by viewModel.settings.collectAsState()

    // Issue #433: import stages the URI (the confirm dialog asks
    // first — import replaces all current settings); the result
    // dialogs report the counts.
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var exportStats by remember { mutableStateOf<SettingsBackup.ExportStats?>(null) }
    var importStats by remember { mutableStateOf<SettingsBackup.ImportStats?>(null) }

    fun update(next: AppSettings) {
        viewModel.updateSettings(next)
    }

    // Issue #432: the picked folder takes the persistable read/write
    // permission, saves the tree URI, and gets the standard mpv
    // subfolders + an empty mpv.conf created inside it. A cancelled
    // pick (null) changes nothing.
    SideEffect {
        mpvConfigFolderHandler?.callback = { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (_: Exception) {
                }
                update(settings.copy(mpvConfStorageUri = uri.toString()))
                scope.launch(Dispatchers.IO) {
                    MpvConfigSync.prepareConfigFolder(context, uri.toString())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.adv_config_folder_ready),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    SideEffect {
        settingsExportHandler?.callback = { uri ->
            if (uri != null) {
                scope.launch {
                    val result = SettingsBackup.exportSettings(context, uri)
                    withContext(Dispatchers.Main) {
                        result.fold(
                            onSuccess = { stats -> exportStats = stats },
                            onFailure = {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.adv_export_failed),
                                    Toast.LENGTH_LONG,
                                ).show()
                            },
                        )
                    }
                }
            }
        }
        settingsImportHandler?.callback = { uri ->
            if (uri != null) {
                pendingImportUri = uri
                showImportConfirm = true
            }
        }
    }

    // Issue #270 (same pattern as SettingsScreen): reload on resume —
    // the editor writes the pref directly, so coming back must refresh
    // the row subtitles.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reloadSettings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var editing by rememberSaveable { mutableStateOf<MpvConfigType?>(null) }
    BackHandler(enabled = editing != null) { editing = null }
    // The editor saves through the repo; reload when it closes so the
    // row subtitles show the saved text.
    LaunchedEffect(editing) {
        if (editing == null) viewModel.reloadSettings()
    }

    val editingType = editing
    if (editingType != null) {
        ConfigEditorScreen(
            configType = editingType,
            viewModel = viewModel,
            storageUri = settings.mpvConfStorageUri,
            onNavigateBack = { editing = null },
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.pref_advanced_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MpxTheme.colors.black),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Issue #433: full-settings XML export / import — the whole
            // app's settings in one file.
            item { PreferenceSectionHeader(text = stringResource(R.string.adv_section_backup_restore)) }
            item {
                PreferenceCard {
                    PreferenceRow(
                        title = stringResource(R.string.adv_export_settings),
                        subtitle = stringResource(R.string.adv_export_settings_summary),
                        icon = Icons.Filled.Upload,
                        onClick = { settingsExportLauncher?.launch(SettingsBackup.defaultExportFilename()) },
                    )
                    PreferenceDivider()
                    PreferenceRow(
                        title = stringResource(R.string.adv_import_settings),
                        subtitle = stringResource(R.string.adv_import_settings_summary),
                        icon = Icons.Filled.Download,
                        onClick = { settingsImportLauncher?.launch(arrayOf("text/xml", "application/xml", "*/*")) },
                    )
                }
            }
            item { PreferenceSectionHeader(text = stringResource(R.string.adv_section_mpv_config)) }
            item {
                PreferenceCard {
                    // Issue #432: the config folder picker (mpvEx shape) —
                    // row tap opens the SAF folder picker, the X clears
                    // the stored URI and stops the sync.
                    PreferenceRow(
                        title = stringResource(R.string.adv_config_folder_title),
                        subtitle = MpvConfigSync.folderDisplayName(context, settings.mpvConfStorageUri)
                            ?: stringResource(R.string.adv_config_folder_none),
                        icon = Icons.Filled.Folder,
                        onClick = { mpvConfigFolderLauncher?.launch(null) },
                        trailing = {
                            IconButton(
                                onClick = { update(settings.copy(mpvConfStorageUri = "")) },
                                enabled = settings.mpvConfStorageUri.isNotBlank(),
                            ) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = stringResource(R.string.adv_config_folder_none),
                                    tint = MpxTheme.colors.textSecondary,
                                )
                            }
                        },
                    )
                    PreferenceDivider()
                    PreferenceRow(
                        title = stringResource(R.string.adv_edit_mpv_conf),
                        subtitle = settings.mpvConf.lines().firstOrNull { it.isNotBlank() }?.trim()
                            ?: stringResource(R.string.adv_edit_tap_hint),
                        icon = Icons.Filled.Description,
                        onClick = { editing = MpvConfigType.MPV_CONF },
                    )
                    PreferenceDivider()
                    PreferenceRow(
                        title = stringResource(R.string.adv_edit_input_conf),
                        subtitle = settings.inputConf.lines().firstOrNull { it.isNotBlank() }?.trim()
                            ?: stringResource(R.string.adv_edit_tap_hint),
                        icon = Icons.Filled.Description,
                        onClick = { editing = MpvConfigType.INPUT_CONF },
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                Text(
                    text = stringResource(R.string.adv_editor_applies_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MpxTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
            // Issue #430: Lua scripts toggle — scripts in
            // filesDir/mpv/scripts/ (populated by #432's sync) load at
            // player start unless the toggle is off.
            item { PreferenceSectionHeader(text = stringResource(R.string.adv_section_scripts)) }
            item {
                PreferenceCard {
                    AdvancedToggleRow(
                        title = stringResource(R.string.adv_lua_title),
                        summary = stringResource(R.string.adv_lua_summary),
                        checked = settings.enableLuaScripts,
                        onChange = { update(settings.copy(enableLuaScripts = it)) },
                    )
                }
            }
        }
    }

    // Issue #433: import asks first (it replaces all current
    // settings); the result dialogs report the counts, and the import
    // one says a restart is needed — the running app keeps its
    // in-memory copy until then.
    if (showImportConfirm) {
        MpxDialog(
            title = stringResource(R.string.adv_import_confirm_title),
            onDismiss = { showImportConfirm = false },
            confirmText = stringResource(R.string.adv_import_settings),
            onConfirm = {
                val uri = pendingImportUri
                showImportConfirm = false
                if (uri != null) {
                    scope.launch {
                        val result = SettingsBackup.importSettings(context, uri)
                        withContext(Dispatchers.Main) {
                            result.fold(
                                onSuccess = { importResult ->
                                    when (importResult) {
                                        is SettingsBackup.ImportResult.Success -> {
                                            importStats = importResult.stats
                                            viewModel.reloadSettings()
                                        }
                                        SettingsBackup.ImportResult.InvalidFile -> {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.adv_import_invalid),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }
                                },
                                onFailure = {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.adv_import_failed),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                },
                            )
                        }
                    }
                }
            },
            dismissText = stringResource(R.string.action_cancel),
        ) {
            Text(
                text = stringResource(R.string.adv_import_confirm_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MpxTheme.colors.textSecondary,
            )
        }
    }

    exportStats?.let { stats ->
        MpxDialog(
            title = stringResource(R.string.adv_export_complete_title),
            onDismiss = { exportStats = null },
            confirmText = stringResource(R.string.action_ok),
            onConfirm = { exportStats = null },
        ) {
            Text(
                text = stringResource(R.string.adv_exported_message, stats.totalExported),
                style = MaterialTheme.typography.bodyMedium,
                color = MpxTheme.colors.textSecondary,
            )
        }
    }

    importStats?.let { stats ->
        MpxDialog(
            title = stringResource(R.string.adv_import_complete_title),
            onDismiss = { importStats = null },
            confirmText = stringResource(R.string.action_ok),
            onConfirm = { importStats = null },
        ) {
            Text(
                text = stringResource(R.string.adv_import_result, stats.imported, stats.failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MpxTheme.colors.textSecondary,
            )
        }
    }
}

/** A switch row with a title + summary, same shape as SettingsScreen's ToggleRow. */
@Composable
private fun AdvancedToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = MpxSpaceL, vertical = MpxSpaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MpxTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MpxTheme.colors.textMuted,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MpxTheme.colors.primary,
                checkedTrackColor = MpxTheme.colors.primary.copy(alpha = 0.4f),
            ),
        )
    }
}
