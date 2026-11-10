package com.mpx.player.ui.screens.about
import com.mpx.player.ui.theme.MpxTheme

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.mpx.player.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.R
import kotlinx.coroutines.launch
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutBetaScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val issuesUrl = stringResource(R.string.about_beta_issues_url)
    val copiedMsg = stringResource(R.string.about_beta_copied_snackbar)
    val browserErrorMsg = stringResource(R.string.about_beta_browser_error)

    fun openIssuesPage() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(issuesUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            clipboardManager.setText(AnnotatedString(issuesUrl))
            scope.launch {
                snackbarHostState.showSnackbar(browserErrorMsg)
            }
        } catch (_: Exception) {
            clipboardManager.setText(AnnotatedString(issuesUrl))
            scope.launch {
                snackbarHostState.showSnackbar(copiedMsg)
            }
        }
    }

    fun copyIssuesUrl() {
        clipboardManager.setText(AnnotatedString(issuesUrl))
        scope.launch {
            snackbarHostState.showSnackbar(copiedMsg)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxTheme.colors.black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about_beta_screen_title),
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. App & Beta Release Hero Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MpxShapes.large,
                    colors = CardDefaults.cardColors(containerColor = MpxTheme.colors.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MpxTheme.colors.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Movie,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = MpxTheme.colors.textPrimary,
                        )

                        Spacer(Modifier.height(4.dp))

                        // Beta Badge
                        Surface(
                            shape = MpxShapes.extraLarge,
                            color = MpxTheme.colors.textMuted.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(MpxHairline, MpxTheme.colors.textMuted),
                        ) {
                            Text(
                                text = stringResource(R.string.about_beta_badge),
                                color = MpxTheme.colors.textMuted,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.app_version_name),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MpxTheme.colors.textPrimary,
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = stringResource(R.string.about_beta_tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textSecondary,
                            lineHeight = 18.sp,
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = stringResource(
                                R.string.about_beta_build_info,
                                BuildConfig.VERSION_CODE,
                                BuildConfig.GIT_BRANCH,
                            ),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MpxTheme.colors.textSecondary.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            // 2. Feedback Card (Prominent CTA directly below hero)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MpxShapes.large,
                    colors = CardDefaults.cardColors(containerColor = MpxTheme.colors.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MpxTheme.colors.primary.copy(alpha = 0.5f)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MpxTheme.colors.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.about_beta_section_feedback),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MpxTheme.colors.textPrimary,
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.about_beta_feedback_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MpxTheme.colors.textSecondary,
                            lineHeight = 20.sp,
                        )

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = { openIssuesPage() },
                                modifier = Modifier.weight(1f),
                                shape = MpxShapes.small,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MpxTheme.colors.primary,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.about_beta_open_github),
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            OutlinedButton(
                                onClick = { copyIssuesUrl() },
                                shape = MpxShapes.small,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MpxTheme.colors.border),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = stringResource(R.string.about_beta_copy_github_url),
                                    tint = MpxTheme.colors.textPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 3. What is Ready in Beta (Functional working features)
            item {
                Text(
                    text = stringResource(R.string.about_beta_section_ready),
                    style = MaterialTheme.typography.labelSmall,
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MpxShapes.large,
                    colors = CardDefaults.cardColors(containerColor = MpxTheme.colors.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.about_beta_ready_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textSecondary,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )

                        FeatureStatusRow(
                            icon = Icons.Filled.PlayCircleOutline,
                            iconTint = MpxTheme.colors.primary,
                            title = stringResource(R.string.about_beta_feat_player_title),
                            description = stringResource(R.string.about_beta_feat_player_desc),
                            isReady = true,
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MpxTheme.colors.border.copy(alpha = 0.5f),
                            thickness = MpxHairline,
                        )

                        FeatureStatusRow(
                            icon = Icons.Filled.PictureInPictureAlt,
                            iconTint = MpxTheme.colors.primary,
                            title = stringResource(R.string.about_beta_feat_bg_pip_title),
                            description = stringResource(R.string.about_beta_feat_bg_pip_desc),
                            isReady = true,
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MpxTheme.colors.border.copy(alpha = 0.5f),
                            thickness = MpxHairline,
                        )

                        FeatureStatusRow(
                            icon = Icons.Filled.MusicNote,
                            iconTint = MpxTheme.colors.primary,
                            title = stringResource(R.string.about_beta_feat_music_title),
                            description = stringResource(R.string.about_beta_feat_music_desc),
                            isReady = true,
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MpxTheme.colors.border.copy(alpha = 0.5f),
                            thickness = MpxHairline,
                        )

                        FeatureStatusRow(
                            icon = Icons.Filled.Storage,
                            iconTint = MpxTheme.colors.primary,
                            title = stringResource(R.string.about_beta_feat_persistence_title),
                            description = stringResource(R.string.about_beta_feat_persistence_desc),
                            isReady = true,
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MpxTheme.colors.border.copy(alpha = 0.5f),
                            thickness = MpxHairline,
                        )

                        FeatureStatusRow(
                            icon = Icons.Filled.ColorLens,
                            iconTint = MpxTheme.colors.primary,
                            title = stringResource(R.string.about_beta_feat_themes_title),
                            description = stringResource(R.string.about_beta_feat_themes_desc),
                            isReady = true,
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MpxTheme.colors.border.copy(alpha = 0.5f),
                            thickness = MpxHairline,
                        )

                        FeatureStatusRow(
                            icon = Icons.Filled.Language,
                            iconTint = MpxTheme.colors.primary,
                            title = stringResource(R.string.about_beta_feat_i18n_title),
                            description = stringResource(R.string.about_beta_feat_i18n_desc),
                            isReady = true,
                        )
                    }
                }
            }

            // 4. What is Still Placeholder (Honest disclosures - not faked)
            item {
                Text(
                    text = stringResource(R.string.about_beta_section_placeholders),
                    style = MaterialTheme.typography.labelSmall,
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MpxShapes.large,
                    colors = CardDefaults.cardColors(containerColor = MpxTheme.colors.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.about_beta_placeholders_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textSecondary,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )

                        FeatureStatusRow(
                            icon = Icons.Filled.SaveAlt,
                            iconTint = MpxTheme.colors.textMuted,
                            title = stringResource(R.string.about_beta_holder_status_title),
                            description = stringResource(R.string.about_beta_holder_status_desc),
                            isReady = false,
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MpxTheme.colors.border.copy(alpha = 0.5f),
                            thickness = MpxHairline,
                        )

                        FeatureStatusRow(
                            icon = Icons.Filled.Lock,
                            iconTint = MpxTheme.colors.textMuted,
                            title = stringResource(R.string.about_beta_holder_private_title),
                            description = stringResource(R.string.about_beta_holder_private_desc),
                            isReady = false,
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MpxTheme.colors.border.copy(alpha = 0.5f),
                            thickness = MpxHairline,
                        )

                        FeatureStatusRow(
                            icon = Icons.Filled.FolderSpecial,
                            iconTint = MpxTheme.colors.textMuted,
                            title = stringResource(R.string.about_beta_holder_manager_title),
                            description = stringResource(R.string.about_beta_holder_manager_desc),
                            isReady = false,
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MpxTheme.colors.border.copy(alpha = 0.5f),
                            thickness = MpxHairline,
                        )

                        FeatureStatusRow(
                            icon = Icons.Filled.Delete,
                            iconTint = MpxTheme.colors.textMuted,
                            title = stringResource(R.string.about_beta_holder_bin_title),
                            description = stringResource(R.string.about_beta_holder_bin_desc),
                            isReady = false,
                        )
                    }
                }
            }

            // 5. Zero Telemetry & Privacy Integrity
            item {
                Text(
                    text = stringResource(R.string.about_beta_section_privacy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MpxTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MpxShapes.large,
                    colors = CardDefaults.cardColors(containerColor = MpxTheme.colors.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = MpxTheme.colors.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.dialog_privacy_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MpxTheme.colors.textPrimary,
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.about_beta_privacy_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MpxTheme.colors.textSecondary,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun FeatureStatusRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    isReady: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(MpxShapes.small)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MpxTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )

                Spacer(Modifier.width(8.dp))

                if (isReady) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MpxTheme.colors.success,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.about_status_ready),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MpxTheme.colors.success,
                            fontSize = 11.sp,
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.HourglassTop,
                            contentDescription = null,
                            tint = MpxTheme.colors.textMuted,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.about_status_placeholder),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MpxTheme.colors.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MpxTheme.colors.textSecondary,
                lineHeight = 16.sp,
            )
        }
    }
}
