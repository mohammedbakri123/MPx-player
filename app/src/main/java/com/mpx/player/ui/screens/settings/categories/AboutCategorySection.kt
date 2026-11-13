package com.mpx.player.ui.screens.settings.categories

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mpx.player.BuildConfig
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.ui.screens.settings.AppUpdateDialog
import com.mpx.player.ui.screens.settings.PreferenceRow
import com.mpx.player.ui.screens.settings.SettingsViewModel
import com.mpx.player.ui.screens.settings.UpdateCheckStatus
import com.mpx.player.ui.screens.settings.components.ToggleRow
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxTheme
import com.mpx.player.update.AppUpdateChecker

@Composable
fun AboutCategorySection(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    viewModel: SettingsViewModel,
    onOpenAboutBeta: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val updateStatus by viewModel.updateStatus.collectAsState()
    val availableRelease by viewModel.availableRelease.collectAsState()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()

    Column(modifier = modifier) {
        Text(
            stringResource(R.string.about_app_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MpxTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        if (onOpenAboutBeta != null) {
            OutlinedButton(
                onClick = onOpenAboutBeta,
                modifier = Modifier.fillMaxWidth(),
                shape = MpxShapes.small,
            ) {
                Icon(
                    Icons.Filled.Info,
                    null,
                    tint = MpxTheme.colors.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.about_beta_open_page_button),
                    color = MpxTheme.colors.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (BuildConfig.SELF_UPDATE_ENABLED) {
            Spacer(Modifier.height(16.dp))
            PreferenceRow(
                title = stringResource(R.string.app_update_check_title),
                subtitle = when (updateStatus) {
                    UpdateCheckStatus.IDLE -> stringResource(
                        R.string.app_update_current_version,
                        AppUpdateChecker.currentVersion(),
                    )
                    UpdateCheckStatus.CHECKING -> stringResource(R.string.app_update_checking)
                    UpdateCheckStatus.UP_TO_DATE -> stringResource(R.string.app_update_up_to_date)
                    UpdateCheckStatus.AVAILABLE -> stringResource(
                        R.string.app_update_available,
                        availableRelease?.tag.orEmpty(),
                    )
                    UpdateCheckStatus.FAILED -> stringResource(R.string.app_update_check_failed)
                },
                icon = Icons.Filled.Update,
                onClick = {
                    if (updateStatus == UpdateCheckStatus.CHECKING) return@PreferenceRow
                    viewModel.checkForUpdate { result ->
                        when (result) {
                            is AppUpdateChecker.CheckResult.UpToDate -> {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.app_update_up_to_date),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            is AppUpdateChecker.CheckResult.Available -> {
                                // Dialog visibility is managed in ViewModel
                            }
                            AppUpdateChecker.CheckResult.Failed -> {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.app_update_check_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                },
            )
            if (showUpdateDialog) {
                availableRelease?.let { release ->
                    AppUpdateDialog(
                        release = release,
                        onDismiss = { viewModel.dismissUpdateDialog() },
                    )
                }
            }
            ToggleRow(stringResource(R.string.app_update_auto_check), settings.appUpdateAutoCheck) {
                onUpdate(settings.copy(appUpdateAutoCheck = it))
            }
        }
    }
}
