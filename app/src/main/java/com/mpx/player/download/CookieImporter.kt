package com.mpx.player.download

import android.webkit.CookieManager
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cookie import for restricted downloads (issue #337).
 *
 * Seal reads the in-app WebView's cookie SQLite DB
 * (`DownloadUtil.getCookieListFromDatabase`, JunkFood02/Seal, GPL-3.0)
 * and writes a Netscape-format cookies file (`toCookiesFileContent`);
 * MPx has no in-app WebView, so it reads the system WebView cookie
 * store through `CookieManager.getCookie(url)` instead and writes the
 * same Netscape format — Seal's `Cookie.toNetscapeCookieString`
 * (`WebViewPage.kt:51`): tab-separated domain / subdomain-flag /
 * path / secure-flag / expiry / name / value.
 *
 * Honest limit: `CookieManager` only sees cookies set by WebViews
 * running inside MPx itself — the store is per-app, so a login in
 * Chrome (or any other browser) is NOT visible here and the import
 * reports "no cookies". A cookies.txt file import, or an in-app
 * WebView login (Seal's pattern), would be needed to reach those
 * cookies; neither is part of this issue.
 */
object CookieImporter {

    /**
     * Writes the WebView cookies for [rawUrl]'s host into [outFile]
     * in Netscape format. Returns true when at least one cookie was
     * written, false when the store has no cookies for the URL (the
     * caller shows "no cookies"). The `getCookie` call runs on the
     * main thread — WebView's cookie store is main-thread bound —
     * and the file write runs on the caller's thread.
     */
    suspend fun importForUrl(rawUrl: String, outFile: File): Boolean {
        val url = normalizeUrl(rawUrl) ?: return false
        val host = try {
            URL(url).host?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } ?: return false
        val rawCookies = withContext(Dispatchers.Main) {
            try {
                CookieManager.getInstance().getCookie(url)
            } catch (_: Exception) {
                null
            }
        }.orEmpty()
        val cookies = parseCookies(rawCookies)
        if (cookies.isEmpty()) return false
        // Session cookies carry no expiry in getCookie's output;
        // Netscape format needs one — a year out keeps yt-dlp happy
        // without pretending the cookie lives forever.
        val expiry = System.currentTimeMillis() / 1000 + 365L * 24 * 60 * 60
        val secure = url.startsWith("https://")
        val content = buildString {
            append("# Netscape HTTP Cookie File\n")
            append("# Cookies imported from the system WebView store by MPx\n")
            for ((name, value) in cookies) {
                append(".").append(host).append('\t')
                    .append("TRUE\t/\t")
                    .append(if (secure) "TRUE" else "FALSE").append('\t')
                    .append(expiry).append('\t')
                    .append(name).append('\t').append(value).append('\n')
            }
        }
        outFile.parentFile?.mkdirs()
        outFile.writeText(content)
        return true
    }

    /** Adds https:// when the user pasted a bare host/path. */
    fun normalizeUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return null
        return if ("://" in trimmed) trimmed else "https://$trimmed"
    }

    /**
     * Parses `getCookie`'s "name=value; name2=value2" output.
     * Drops anything unparseable or unsafe for the tab-separated
     * Netscape format (a tab or newline in a name/value would corrupt
     * the file for every cookie after it).
     */
    private fun parseCookies(raw: String): List<Pair<String, String>> {
        if (raw.isBlank()) return emptyList()
        return raw.split(';')
            .map { it.trim() }
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val name = part.substring(0, eq).trim()
                val value = part.substring(eq + 1).trim()
                if (name.isEmpty() || value.isEmpty()) return@mapNotNull null
                if (name.any { it == '\t' || it == '\n' || it == '\r' } ||
                    value.any { it == '\t' || it == '\n' || it == '\r' }) return@mapNotNull null
                name to value
            }
    }
}
