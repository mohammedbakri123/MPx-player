package com.mpx.player.ui.screens.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.domain.settings.AppSettings
import com.mpx.player.domain.settings.AppSettingsRepository
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxTheme

/**
 * SponsorBlock settings (issue #348): remove sponsor segments,
 * intros and outros from downloaded videos — Seal's SponsorBlock
 * dialog shape (AdvancedSettingDialogs.kt, JunkFood02/Seal,
 * GPL-3.0), adapted to MPx's save-on-tap dialog pattern: every
 * choice persists at once, so there is nothing to lose on dismiss
 * — Done just closes the dialog. The downloader reads the settings
 * at request time, so a change here applies to the next download.
 *
 * Categories are stored as one comma-separated string (the raw
 * `--sponsorblock-remove` value); the checkboxes rebuild it in
 * canonical order, so toggling never scrambles the list. The six
 * boxes are yt-dlp's `default` set (Seal's dialog also offers
 * `preview` and `filler` — not offered here). Off by default, so
 * today's behavior is unchanged until the user turns it on; the
 * cutting itself uses the bundled ffmpeg (#336).
 */
@Composable
fun DownloadSponsorBlockDialog(
    settingsRepository: AppSettingsRepository,
    onDismiss: () -> Unit,
) {
    var settings by remember { mutableStateOf(settingsRepository.load()) }

    fun update(next: AppSettings) {
        settings = next
        settingsRepository.save(next)
    }

    val selected = settings.downloadSponsorBlockCategories
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    MpxDialog(
        title = stringResource(R.string.download_sponsorblock_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_done),
        onConfirm = onDismiss,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            OptionsSwitchRow(
                label = stringResource(R.string.download_sponsorblock_enable),
                checked = settings.downloadSponsorBlock,
                onCheckedChange = { update(settings.copy(downloadSponsorBlock = it)) },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.download_sponsorblock_enable_desc),
                color = MpxTheme.colors.textSecondary,
                fontSize = 13.sp,
            )
            OptionsSectionLabel(stringResource(R.string.download_sponsorblock_categories))
            SPONSORBLOCK_CATEGORIES.forEach { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = SPONSORBLOCK_CATEGORIES.filter { item ->
                                if (item == category) item !in selected else item in selected
                            }
                            update(
                                settings.copy(
                                    downloadSponsorBlockCategories = next.joinToString(","),
                                ),
                            )
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = category in selected,
                        onCheckedChange = { checked ->
                            val next = SPONSORBLOCK_CATEGORIES.filter { item ->
                                if (item == category) checked else item in selected
                            }
                            update(
                                settings.copy(
                                    downloadSponsorBlockCategories = next.joinToString(","),
                                ),
                            )
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MpxTheme.colors.primary,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = category,
                        color = MpxTheme.colors.textPrimary,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

/**
 * The SponsorBlock categories offered as checkboxes — yt-dlp's
 * `default` set. Category names are yt-dlp identifiers, not
 * translated (Seal shows them raw too).
 */
private val SPONSORBLOCK_CATEGORIES = listOf(
    "sponsor",
    "intro",
    "outro",
    "selfpromo",
    "interaction",
    "music_offtopic",
)
