package com.mpx.player.ui.screens.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import com.mpx.player.data.local.entity.CookieProfileEntity
import com.mpx.player.ui.components.MpxDialog
import com.mpx.player.ui.theme.MpxTheme

/**
 * Issue #409: pick a saved cookie profile for this download —
 * Seal's per-download cookie picker (`DownloadDialogV2.kt:759,804`).
 * Tap a profile to use its cookies for the URL already in the
 * field; the download starts with them and the dialog closes. The
 * picker only opens when profiles exist, so an empty list shows the
 * empty text instead of a blank dialog.
 */
@Composable
fun CookieProfilePickerDialog(
    profiles: List<CookieProfileEntity>,
    onPick: (CookieProfileEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    MpxDialog(
        title = stringResource(R.string.download_use_saved_cookies),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_cancel),
        onConfirm = onDismiss,
    ) {
        if (profiles.isEmpty()) {
            Text(
                text = stringResource(R.string.download_cookie_profiles_none),
                color = MpxTheme.colors.textSecondary,
                fontSize = 14.sp,
            )
        } else {
            LazyColumn {
                items(profiles, key = { it.id }) { profile ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onPick(profile) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                color = MpxTheme.colors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = profile.url,
                                color = MpxTheme.colors.textSecondary,
                                fontSize = 13.sp,
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
