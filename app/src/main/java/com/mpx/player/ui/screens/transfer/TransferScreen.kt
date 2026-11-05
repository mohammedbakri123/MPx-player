package com.mpx.player.ui.screens.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.mpx.player.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpx.player.ui.components.EmptyStateView
import com.mpx.player.ui.components.HonestStatusBanner
import com.mpx.player.ui.components.MpxCard
import com.mpx.player.ui.components.MpxPrimaryButton
import kotlinx.coroutines.launch
import com.mpx.player.ui.theme.MpxBlack
import com.mpx.player.ui.theme.MpxBorder
import com.mpx.player.ui.theme.MpxHairline
import com.mpx.player.ui.theme.MpxPrimary
import com.mpx.player.ui.theme.MpxShapes
import com.mpx.player.ui.theme.MpxSurfaceVariant
import com.mpx.player.ui.theme.MpxTextPrimary
import com.mpx.player.ui.theme.MpxTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var urlText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MpxBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.transfer_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MpxTextPrimary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MpxBlack,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MpxBlack,
                contentColor = MpxPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MpxPrimary,
                    )
                },
                divider = {
                    HorizontalDivider(color = MpxBorder, thickness = MpxHairline)
                },
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = stringResource(R.string.transfer_tab_downloader),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selectedTab == 0) MpxPrimary else MpxTextSecondary,
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = stringResource(R.string.transfer_tab_file_transfer),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selectedTab == 1) MpxPrimary else MpxTextSecondary,
                        )
                    },
                )
            }

            if (selectedTab == 0) {
                // Downloader view with honest placeholder
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    HonestStatusBanner(
                        text = stringResource(R.string.transfer_downloader_banner),
                    )

                    Spacer(Modifier.height(16.dp))

                    MpxCard(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = null,
                                    tint = MpxPrimary,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.transfer_url_downloader),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MpxTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.transfer_url_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MpxTextSecondary,
                            )

                            Spacer(Modifier.height(14.dp))

                            OutlinedTextField(
                                value = urlText,
                                onValueChange = { urlText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        stringResource(R.string.transfer_url_placeholder),
                                        color = MpxTextSecondary,
                                        fontSize = 14.sp,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Link,
                                        contentDescription = null,
                                        tint = MpxTextSecondary,
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        val pasted = clipboardManager.getText()?.text.orEmpty()
                                        if (pasted.startsWith("http")) {
                                            urlText = pasted
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(context.getString(R.string.transfer_clipboard_no_url))
                                            }
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Filled.ContentPaste,
                                            contentDescription = stringResource(R.string.transfer_cd_paste),
                                            tint = MpxPrimary,
                                        )
                                    }
                                },
                                singleLine = true,
                                shape = MpxShapes.medium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MpxBlack,
                                    unfocusedContainerColor = MpxBlack,
                                    focusedBorderColor = MpxPrimary,
                                    unfocusedBorderColor = MpxBorder,
                                    focusedTextColor = MpxTextPrimary,
                                    unfocusedTextColor = MpxTextPrimary,
                                ),
                            )

                            Spacer(Modifier.height(14.dp))

                            MpxPrimaryButton(
                                text = stringResource(R.string.transfer_start_download),
                                icon = Icons.Filled.Download,
                                onClick = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.transfer_downloader_placeholder_toast),
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Text(
                        text = stringResource(R.string.transfer_download_tasks),
                        style = MaterialTheme.typography.labelSmall,
                        color = MpxTextSecondary,
                        fontSize = 12.sp,
                    )

                    Spacer(Modifier.height(12.dp))

                    EmptyStateView(
                        title = stringResource(R.string.transfer_no_downloads_title),
                        message = stringResource(R.string.transfer_no_downloads_msg),
                        icon = Icons.Filled.DownloadDone,
                        modifier = Modifier.height(200.dp),
                    )
                }
            } else {
                // Local File Transfer view
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HonestStatusBanner(
                        text = stringResource(R.string.transfer_p2p_banner),
                    )

                    Spacer(Modifier.height(24.dp))

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(MpxShapes.extraLarge)
                            .background(MpxBlack)
                            .border(MpxHairline, MpxBorder, MpxShapes.extraLarge),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = null,
                            tint = MpxPrimary,
                            modifier = Modifier.size(48.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.transfer_direct_title),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MpxTextPrimary,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.transfer_direct_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MpxTextSecondary,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )

                    Spacer(Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.transfer_p2p_spec))
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = MpxShapes.large,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MpxPrimary,
                                contentColor = MpxTextPrimary,
                            ),
                        ) {
                            Icon(Icons.Filled.ArrowUpward, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.transfer_send), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.transfer_p2p_spec))
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = MpxShapes.large,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MpxTextPrimary,
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MpxPrimary),
                        ) {
                            Icon(Icons.Filled.ArrowDownward, null, Modifier.size(20.dp), tint = MpxPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.transfer_receive), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MpxPrimary)
                        }
                    }
                }
            }
        }
    }
}
