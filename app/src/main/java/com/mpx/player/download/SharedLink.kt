package com.mpx.player.download

import android.content.Intent

/**
 * Issue #416: receive shared links — the URL out of an incoming
 * intent, or null when there is none. Seal's `matchUrlFromSharedText`
 * (`util/TextUtil.kt:91-97`, first match of a URL regex over the
 * shared text) does the same job; MPx takes the first `https?://…`
 * URL and trims the punctuation a share often trails with
 * ("Check this out: https://…." — the trailing period is not part
 * of the link).
 *
 * - ACTION_VIEW: the link is the intent's data URI itself.
 * - ACTION_SEND: the link hides inside EXTRA_TEXT, which may be a
 *   bare URL or prose around one ("Watch this https://…").
 */
object SharedLink {

    private val URL_PATTERN = Regex("https?://\\S+")
    private val TRAILING_PUNCTUATION = Regex("[.,;:!?)\\]}'\"]+$")

    fun urlFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.dataString?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                URL_PATTERN.find(text)?.value
                    ?.replace(TRAILING_PUNCTUATION, "")
                    ?.takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }
}
