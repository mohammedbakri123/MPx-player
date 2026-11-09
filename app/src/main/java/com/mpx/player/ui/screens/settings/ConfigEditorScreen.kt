package com.mpx.player.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.playback.engine.MpvConfigSync
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Which mpv config file the editor is editing (#431). */
enum class MpvConfigType { MPV_CONF, INPUT_CONF }

/**
 * Issue #431: in-app editor for mpv.conf / input.conf (mpvEx
 * ConfigEditorScreen shape — monospace text field, save check in the
 * top bar, "Unsaved changes" subtitle — rebuilt in MPx's own style).
 *
 * The file in filesDir/mpv is what libmpv reads at startup, so save
 * writes it plus the pref cache (the editor reopens with it). When a
 * config folder is picked (#432) the folder is the source of truth:
 * the editor loads its file first and writes back to it too. Reset
 * deletes the local file, clears the pref, and deletes the folder's
 * copy, so a later player-start sync cannot resurrect it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditorScreen(
    configType: MpvConfigType,
    settingsRepository: AppSettingsRepository,
    storageUri: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val fileName = when (configType) {
        MpvConfigType.MPV_CONF -> MpvConfigSync.MPV_CONF
        MpvConfigType.INPUT_CONF -> MpvConfigSync.INPUT_CONF
    }
    val hintRes = when (configType) {
        MpvConfigType.MPV_CONF -> R.string.adv_editor_hint_mpv
        MpvConfigType.INPUT_CONF -> R.string.adv_editor_hint_input
    }

    var configText by remember { mutableStateOf("") }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    // Load once: the picked folder's file first, then the local file
    // libmpv reads, then the pref cache.
    LaunchedEffect(configType) {
        val initial = withContext(Dispatchers.IO) {
            MpvConfigSync.readConfigFile(context, storageUri, fileName)
                ?: runCatching {
                    File(MpvConfigSync.configDir(context), fileName)
                        .takeIf { it.exists() }?.readText()
                }.getOrNull()
                ?: run {
                    val saved = settingsRepository.load()
                    if (configType == MpvConfigType.MPV_CONF) saved.mpvConf else saved.inputConf
                }
        } ?: ""
        configText = initial
        loaded = true
    }

    fun saveConfig() {
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                File(MpvConfigSync.configDir(context), fileName).writeText(configText)
                val saved = settingsRepository.load()
                settingsRepository.save(
                    if (configType == MpvConfigType.MPV_CONF) saved.copy(mpvConf = configText)
                    else saved.copy(inputConf = configText),
                )
            }.isSuccess
            // The folder copy is best-effort: the local file is what
            // libmpv reads, so a folder write failure must not fail
            // the save.
            if (ok && storageUri.isNotBlank()) {
                if (!MpvConfigSync.writeConfigFile(context, storageUri, fileName, configText)) {
                    android.util.Log.w("ConfigEditor", "Failed to write $fileName to picked folder")
                }
            }
            withContext(Dispatchers.Main) {
                if (ok) {
                    hasUnsavedChanges = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.adv_editor_saved, fileName),
                        Toast.LENGTH_SHORT,
                    ).show()
                    onNavigateBack()
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.adv_editor_save_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    fun resetConfig() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                File(MpvConfigSync.configDir(context), fileName).delete()
                val saved = settingsRepository.load()
                settingsRepository.save(
                    if (configType == MpvConfigType.MPV_CONF) saved.copy(mpvConf = "")
                    else saved.copy(inputConf = ""),
                )
                if (storageUri.isNotBlank()) {
                    MpvConfigSync.deleteConfigFile(context, storageUri, fileName)
                }
            }
            withContext(Dispatchers.Main) {
                configText = ""
                hasUnsavedChanges = false
                showResetConfirm = false
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(
                                if (configType == MpvConfigType.MPV_CONF) R.string.adv_edit_mpv_conf
                                else R.string.adv_edit_input_conf,
                            ),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                            color = MpxTheme.colors.textPrimary,
                        )
                        if (hasUnsavedChanges) {
                            Text(
                                text = stringResource(R.string.adv_editor_unsaved),
                                style = MaterialTheme.typography.bodySmall,
                                color = MpxTheme.colors.textSecondary,
                            )
                        }
                    }
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
                    IconButton(onClick = { showResetConfirm = true }) {
                        Icon(
                            Icons.Filled.Restore,
                            stringResource(R.string.adv_editor_reset),
                            tint = MpxTheme.colors.textPrimary,
                        )
                    }
                    IconButton(
                        onClick = { saveConfig() },
                        enabled = loaded && hasUnsavedChanges,
                        modifier = Modifier.padding(horizontal = 12.dp).size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (hasUnsavedChanges) MpxTheme.colors.primary.copy(alpha = 0.2f)
                            else MpxTheme.colors.surfaceRaised,
                            contentColor = MpxTheme.colors.primary,
                            disabledContainerColor = MpxTheme.colors.surfaceRaised,
                            disabledContentColor = MpxTheme.colors.textMuted,
                        ),
                    ) {
                        Icon(Icons.Filled.Check, stringResource(R.string.action_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MpxTheme.colors.black),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = stringResource(hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = MpxTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.adv_editor_applies_note),
                style = MaterialTheme.typography.bodySmall,
                color = MpxTheme.colors.textMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
            )
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .imePadding(),
            ) {
                if (loaded) {
                    BasicTextField(
                        value = configText,
                        onValueChange = { configText = it; hasUnsavedChanges = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = MpxTheme.colors.textPrimary,
                        ),
                        cursorBrush = SolidColor(MpxTheme.colors.primary),
                    )
                }
            }
        }
    }

    if (showResetConfirm) {
        MpxDialog(
            title = stringResource(R.string.adv_editor_reset),
            onDismiss = { showResetConfirm = false },
            confirmText = stringResource(R.string.adv_editor_reset),
            onConfirm = { resetConfig() },
            dismissText = stringResource(R.string.action_cancel),
        ) {
            Text(
                text = stringResource(R.string.adv_editor_reset_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MpxTheme.colors.textSecondary,
            )
        }
    }
}
