package com.mpx.player.ui.screens.download

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mpx.player.R
import com.mpx.player.download.CookieImporter

/**
 * In-app WebView sign-in for restricted downloads (issue #337, rework).
 *
 * Seal's pattern (`WebViewPage.kt`, JunkFood02/Seal, GPL-3.0): the user
 * signs in inside the app's own WebView, and the cookies that WebView
 * sets are then read from the app's cookie store. `CookieManager` is
 * per-app — a login in Chrome or any other browser is invisible to MPx,
 * so the first attempt's "Import cookies from browser" button could
 * never work; this dialog is what makes the import real.
 *
 * Flow: the WebView opens the video site, the user logs in there, then
 * taps Done — [onUseCookies] reads the cookies this WebView set
 * ([CookieImporter]) and starts the download with them. Closing the
 * dialog without Done sends nothing: the cookies stay in the app's
 * store, but they are only ever passed to yt-dlp for the one download
 * started from Done.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInWebViewDialog(
    url: String,
    userAgent: String,
    onDismiss: () -> Unit,
    onUseCookies: () -> Unit,
) {
    val startUrl = CookieImporter.normalizeUrl(url) ?: return
    var pageTitle by remember { mutableStateOf<String?>(null) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(pageTitle ?: startUrl, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        TextButton(onClick = onUseCookies) {
                            Text(stringResource(R.string.action_done))
                        }
                    },
                )
            },
        ) { paddingValues ->
            AndroidView(
                modifier = Modifier.padding(paddingValues).fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.run {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            if (userAgent.isNotBlank()) userAgentString = userAgent
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                pageTitle = view.title?.takeIf { it.isNotBlank() }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                // Keep http(s) navigation inside the
                                // WebView (Seal's WebViewPage does the
                                // same); anything else is not a login page.
                                return request?.url?.scheme?.contains("http") != true
                            }
                        }
                        loadUrl(startUrl)
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
