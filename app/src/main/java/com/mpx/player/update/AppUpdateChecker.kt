package com.mpx.player.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.mpx.player.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Issue #414: in-app self-update — Seal's `UpdateUtil` (`checkForUpdate`,
 * `downloadApk`, `installLatestApk`). Users only learn about a new
 * release by visiting the GitHub releases page; for a
 * GitHub-distributed app with near-daily releases, that is the
 * difference between staying current and silently running an old
 * build. Manual check only (no auto-check — later, per the issue).
 *
 * The comparison only works because the build stamps the release tag
 * into `versionName`/`versionCode` (see `app/build.gradle.kts`):
 * v0.4.34 installs as versionName "0.4.34", versionCode 434. Before
 * #414 every build reported "0.1.0-beta", so a check would always
 * claim an update was available.
 *
 * No new dependency: plain `HttpURLConnection` (same as the
 * direct-link download fallback) + `org.json`.
 */
object AppUpdateChecker {

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/mohammedbakri123/MPx-player/releases/latest"

    data class Release(
        val tag: String,
        val name: String,
        val publishedAt: String,
        val apkUrl: String,
        val apkName: String,
        val apkSize: Long,
    )

    sealed interface CheckResult {
        data class UpToDate(val latestTag: String) : CheckResult
        data class Available(val release: Release) : CheckResult
        data object Failed : CheckResult
    }

    /** The installed release, e.g. "0.4.34". */
    fun currentVersion(): String = BuildConfig.VERSION_NAME

    /**
     * Semver compare on "x.y.z", ignoring a leading "v" and any
     * "-suffix" (so "0.1.0-beta" still compares as 0.1.0). Negative
     * when [a] is older, 0 when equal, positive when newer. Unparseable
     * input compares as 0.0.0 — never crashes the check.
     */
    fun compareVersions(a: String, b: String): Int {
        fun parts(version: String): List<Int> =
            version.trim().removePrefix("v").substringBefore("-")
                .split(".").map { it.toIntOrNull() ?: 0 }
        val left = parts(a)
        val right = parts(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = (left.getOrElse(i) { 0 }) - (right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    /** Query the GitHub releases API for the latest release. Null on any failure. */
    private suspend fun fetchLatestRelease(): Release? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MPx-player")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val root = JSONObject(body)
            val tag = root.optString("tag_name", "")
            if (tag.isBlank()) return@withContext null
            // The release APK is the arm64 minified build; the armv7
            // split is not published as a release asset.
            val assets = root.optJSONArray("assets") ?: return@withContext null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (!name.endsWith(".apk") || "arm64" !in name) continue
                val url = asset.optString("browser_download_url", "")
                if (url.isBlank()) continue
                return@withContext Release(
                    tag = tag,
                    name = root.optString("name", tag),
                    publishedAt = root.optString("published_at", ""),
                    apkUrl = url,
                    apkName = name,
                    apkSize = asset.optLong("size", 0),
                )
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Compare the latest release against the installed version. */
    suspend fun checkForUpdate(): CheckResult {
        val latest = fetchLatestRelease() ?: return CheckResult.Failed
        return if (compareVersions(latest.tag, currentVersion()) > 0) {
            CheckResult.Available(latest)
        } else {
            CheckResult.UpToDate(latest.tag)
        }
    }

    /** The cached update APK, when one was already downloaded. */
    fun cachedApkFile(context: Context): File =
        File(File(context.cacheDir, "updates").apply { mkdirs() }, "latest.apk")

    /**
     * The version of a downloaded APK file, or null when it cannot be
     * read — Seal's `getPackageArchiveInfo` check, so a re-tap after a
     * failed install does not re-download ~88 MB.
     */
    private fun apkFileVersion(context: Context, file: File): String? {
        return try {
            @Suppress("DEPRECATION")
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    file.absolutePath,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            }
            info?.versionName
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Download the release APK, reporting (downloaded, total) as it
     * goes. Reuses the cached file when its version already covers
     * the release. Returns the file, or null on any failure (the
     * partial file is deleted, so a retry starts clean).
     */
    suspend fun downloadApk(
        context: Context,
        release: Release,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        val file = cachedApkFile(context)
        val cachedVersion = if (file.exists()) apkFileVersion(context, file) else null
        if (cachedVersion != null && compareVersions(cachedVersion, release.tag) >= 0) {
            return@withContext file
        }
        try {
            val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "MPx-player")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            var downloaded = 0L
            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
            file
        } catch (_: Exception) {
            try {
                file.delete()
            } catch (_: Exception) {
            }
            null
        }
    }

    /** Open the package installer for a downloaded APK. False when it cannot start. */
    fun installApk(context: Context, file: File): Boolean {
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
